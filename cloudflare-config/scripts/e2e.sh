#!/usr/bin/env bash
# Rejoue les attaques de l'audit et le parcours normal contre `wrangler dev`.
# Identifiants factices uniquement. Usage :
#   wrangler dev --local --port 8787 --env-file <fichier de secrets de TEST>
#   BASE=http://127.0.0.1:8787 OPS_TOKEN=... ADMIN_TOKEN=... ./scripts/e2e.sh
set -u
BASE=${BASE:-http://127.0.0.1:8787}
OPS_TOKEN=${OPS_TOKEN:?}
ADMIN_TOKEN=${ADMIN_TOKEN:?}
pass=0; fail=0
ok()   { echo "  PASS  $1"; pass=$((pass+1)); }
ko()   { echo "  FAIL  $1  ($2)"; fail=$((fail+1)); }
expect_code() { # nom attendu obtenu
  if [ "$2" = "$3" ]; then ok "$1 -> $3"; else ko "$1" "attendu $2, obtenu $3"; fi
}
ipn=0; ip() { ipn=$((ipn+1)); echo "198.51.100.$ipn"; }   # chaque scénario a sa propre IP (wrangler dev n'en fournit pas)
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
PW='correct-horse-battery'
LOGIN="e2e-$RANDOM$RANDOM"

echo "== Parcours normal"
IP=$(ip)
HDRS=$(curl -s -i -H "cf-connecting-ip: $IP" -H "Origin: $BASE" --data-urlencode "login=$LOGIN" --data-urlencode "password=$PW" --data-urlencode "confirm=$PW" "$BASE/signup")
COOKIE=$(echo "$HDRS" | tr -d '\r' | sed -n 's/^[Ss]et-[Cc]ookie: \(__Host-utv_sess=[^;]*\).*/\1/p')
[ -n "$COOKIE" ] && ok "création de compte, session émise" || ko "signup" "pas de cookie"
PAGE=$(curl -s -H "cf-connecting-ip: $IP" -H "Cookie: $COOKIE" "$BASE/")
CSRF=$(echo "$PAGE" | sed -n 's/.*name="csrf" value="\([^"]*\)".*/\1/p' | head -1)
[ -n "$CSRF" ] && ok "jeton CSRF présent dans le tableau de bord" || ko "csrf" "absent"

TVIP=$(ip)
START=$(curl -s -X POST -H "cf-connecting-ip: $TVIP" -H 'content-type: application/json' -d '{"label":"02:aa:bb:cc:dd:ee"}' "$BASE/api/pair/start")
CODE=$(echo "$START" | sed -n 's/.*"code":"\([^"]*\)".*/\1/p'); SECRET=$(echo "$START" | sed -n 's/.*"pollSecret":"\([^"]*\)".*/\1/p')
echo "  code d'appairage affiché sur la TV : $CODE"
expect_code "poll avant saisie du code" 202 "$(code -X POST -H "cf-connecting-ip: $TVIP" -H 'content-type: application/json' -d "{\"code\":\"$CODE\",\"pollSecret\":\"$SECRET\"}" "$BASE/api/pair/poll")"
expect_code "saisie du code dans le tableau de bord" 302 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: $BASE" -H "Cookie: $COOKIE" --data-urlencode "csrf=$CSRF" --data-urlencode "code=$CODE" --data-urlencode "name=Salon" "$BASE/pair")"
POLL=$(curl -s -X POST -H "cf-connecting-ip: $TVIP" -H 'content-type: application/json' -d "{\"code\":\"$CODE\",\"pollSecret\":\"$SECRET\"}" "$BASE/api/pair/poll")
TOKEN=$(echo "$POLL" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p'); DEVID=$(echo "$POLL" | sed -n 's/.*"deviceId":"\([^"]*\)".*/\1/p')
[ "${#TOKEN}" -eq 47 ] && ok "jeton d'appareil reçu (utv_ + 256 bits)" || ko "jeton" "longueur ${#TOKEN}"
expect_code "jeton livré une seule fois" 404 "$(code -X POST -H "cf-connecting-ip: $TVIP" -H 'content-type: application/json' -d "{\"code\":\"$CODE\",\"pollSecret\":\"$SECRET\"}" "$BASE/api/pair/poll")"
expect_code "ajout d'un fournisseur factice" 302 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: $BASE" -H "Cookie: $COOKIE" --data-urlencode "csrf=$CSRF" --data-urlencode kind=XTREAM --data-urlencode name=Factice --data-urlencode url=http://fake.invalid:8080 --data-urlencode username=demo-user --data-urlencode password=demo-pass-visible "$BASE/providers")"
CFG=$(curl -s -H "cf-connecting-ip: $TVIP" -H "Authorization: Bearer $TOKEN" "$BASE/api/config")
echo "$CFG" | grep -q 'demo-pass-visible' && ok "l'app récupère la configuration avec son jeton" || ko "lecture config" "$CFG"

echo "== Attaques"
A=$(ip)
expect_code "lecture sans jeton" 401 "$(code -H "cf-connecting-ip: $A" "$BASE/api/config")"
expect_code "lecture avec jeton deviné" 401 "$(code -H "cf-connecting-ip: $A" -H "Authorization: Bearer utv_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" "$BASE/api/config")"
expect_code "ancienne route /api/config/<mac> (MAC devinée)" 410 "$(code -H "cf-connecting-ip: $A" "$BASE/api/config/02:aa:bb:cc:dd:ee")"
curl -s -H "cf-connecting-ip: $A" "$BASE/api/config/02:aa:bb:cc:dd:ee?password=$PW" | grep -q demo-pass && ko "MAC devinée" "fuite" || ok "MAC devinée ne révèle rien"
expect_code "/logs sans secret" 401 "$(code -H "cf-connecting-ip: $A" "$BASE/logs")"
expect_code "/crashes sans secret" 401 "$(code -H "cf-connecting-ip: $A" "$BASE/crashes")"
expect_code "/logs avec ?token= (ancien mode)" 401 "$(code -H "cf-connecting-ip: $A" "$BASE/logs?token=$OPS_TOKEN")"
expect_code "/logs avec un jeton d'appareil" 401 "$(code -H "cf-connecting-ip: $A" -H "Authorization: Bearer $TOKEN" "$BASE/logs")"
expect_code "/logs avec le bon secret" 200 "$(code -H "cf-connecting-ip: $(ip)" -u "ops:$OPS_TOKEN" "$BASE/logs")"
expect_code "ingestion sans jeton" 401 "$(code -X POST -H "cf-connecting-ip: $A" -H 'content-type: application/json' -d '{"message":"spam"}' "$BASE/api/event")"
expect_code "ingestion trop grosse" 413 "$(code -X POST -H "cf-connecting-ip: $TVIP" -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' -d "{\"message\":\"$(head -c 20000 /dev/zero | tr '\0' a)\"}" "$BASE/api/event")"
curl -s -o /dev/null -X POST -H "cf-connecting-ip: $TVIP" -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' -d '{"tag":"<script>alert(2)</script>","message":"GET http://h.tv/get.php?username=bob&password=hunter2 <img src=x onerror=alert(1)>"}' "$BASE/api/event"
LOGS=$(curl -s -H "cf-connecting-ip: $(ip)" -u "ops:$OPS_TOKEN" "$BASE/logs")
echo "$LOGS" | grep -q hunter2 && ko "nettoyage des URL" "mot de passe stocké" || ok "URL nettoyées avant stockage"
echo "$LOGS" | grep -q '<img src=x' && ko "XSS dans les logs" "non échappé" || ok "XSS dans les logs échappé"
SPAM=0; for i in $(seq 1 70); do c=$(code -X POST -H "cf-connecting-ip: $TVIP" -H "Authorization: Bearer $TOKEN" -H 'content-type: application/json' -d "{\"message\":\"s$i\"}" "$BASE/api/event"); [ "$c" = 429 ] && SPAM=1; done
[ $SPAM = 1 ] && ok "rafale d'événements -> 429" || ko "rafale" "jamais limité"

B=$(ip); LAST=0
for i in 1 2 3 4 5 6; do LAST=$(code -X POST -H "cf-connecting-ip: $(ip)" -H "Origin: $BASE" --data-urlencode "login=$LOGIN" --data-urlencode "password=bad-guess-$i" "$BASE/login"); done
expect_code "force brute : compte verrouillé après 5 échecs" 429 "$LAST"
expect_code "force brute : même le bon mot de passe est refusé" 429 "$(code -X POST -H "cf-connecting-ip: $(ip)" -H "Origin: $BASE" --data-urlencode "login=$LOGIN" --data-urlencode "password=$PW" "$BASE/login")"
C=$(ip); BL=0; for i in $(seq 1 30); do c=$(code -X POST -H "cf-connecting-ip: $C" -H "Origin: $BASE" --data-urlencode "login=victim$i" --data-urlencode "password=x$i-guess-pw" "$BASE/login"); [ "$c" = 429 ] && BL=1; done
[ $BL = 1 ] && ok "force brute multi-comptes depuis une IP -> 429" || ko "limite IP" "jamais limité"

XSS='"><script>alert(1)</script>'
expect_code "XSS : ajout d'un nom hostile" 302 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: $BASE" -H "Cookie: $COOKIE" --data-urlencode "csrf=$CSRF" --data-urlencode kind=M3U --data-urlencode "name=$XSS" --data-urlencode url=http://a.tv/x.m3u "$BASE/providers")"
DASH=$(curl -s -D /tmp/e2e-h.$$ -H "cf-connecting-ip: $IP" -H "Cookie: $COOKIE" "$BASE/")
echo "$DASH" | grep -q '<script>alert(1)</script>' && ko "XSS stockée" "non échappée" || ok "XSS stockée échappée dans le tableau de bord"
grep -qi "content-security-policy:.*script-src 'nonce-" /tmp/e2e-h.$$ && ok "CSP à nonce présente" || ko "CSP" "absente"; rm -f /tmp/e2e-h.$$
expect_code "CSRF : mutation sans jeton" 403 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: $BASE" -H "Cookie: $COOKIE" --data-urlencode kind=M3U --data-urlencode url=http://a.tv/x "$BASE/providers")"
expect_code "CSRF : Origin étrangère" 403 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: https://evil.example" -H "Cookie: $COOKIE" --data-urlencode "csrf=$CSRF" --data-urlencode kind=M3U --data-urlencode url=http://a.tv/x "$BASE/providers")"
curl -s -i -H "origin: https://evil.example" "$BASE/api/config" | grep -qi '^access-control-allow-origin' && ko "CORS" "ouvert" || ok "pas de CORS ouvert"

echo "== Révocation"
expect_code "révocation de l'appareil" 302 "$(code -X POST -H "cf-connecting-ip: $IP" -H "Origin: $BASE" -H "Cookie: $COOKIE" --data-urlencode "csrf=$CSRF" "$BASE/devices/$DEVID/revoke")"
expect_code "jeton révoqué" 401 "$(code -H "cf-connecting-ip: $(ip)" -H "Authorization: Bearer $TOKEN" "$BASE/api/config")"

echo "== Migration"
expect_code "migration sans jeton admin" 401 "$(code -X POST -H "cf-connecting-ip: $(ip)" "$BASE/api/admin/migrate")"
expect_code "migration avec jeton admin" 200 "$(code -X POST -H "cf-connecting-ip: $(ip)" -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE/api/admin/migrate")"

echo; echo "Résultat : $pass OK, $fail KO"; [ $fail -eq 0 ]
