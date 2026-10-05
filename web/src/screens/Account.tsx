// « Abonnement » : informations du compte IPTV de la source active (statut, expiration, connexions), lues en direct.

import { useEffect, useState, type ReactNode } from "react";
import { handshake, type XtreamHandshake } from "@/net/xtream";
import { currentTransport } from "@/net/transport";
import { credsOf } from "@/sync/core";
import { usePrefs } from "@/state/prefs";
import { useActiveSource } from "@/state/sources";

const STR = {
  en: {
    title: "Subscription", sub: "Your IPTV subscription, as reported by your provider.", iptv: "IPTV subscription", source: "Source",
    status: "Account status", expires: "Expires", days: "days left", conns: "Connections in use", created: "Created", trial: "Trial account",
    yes: "Yes", server: "Server", formats: "Formats", never: "No expiry date", unknown: "Unknown", refresh: "Refresh", loading: "Loading…",
    offline: "Could not reach the provider. Try again later.", noSource: "No source configured.", m3u: "M3U playlists do not provide subscription details.",
  },
  fr: {
    title: "Abonnement", sub: "Votre abonnement IPTV, tel que l'indique votre fournisseur.", iptv: "Abonnement IPTV", source: "Source",
    status: "État du compte", expires: "Expire le", days: "jours restants", conns: "Connexions utilisées", created: "Créé le", trial: "Compte d'essai",
    yes: "Oui", server: "Serveur", formats: "Formats", never: "Pas de date d'expiration", unknown: "Inconnu", refresh: "Actualiser", loading: "Chargement…",
    offline: "Fournisseur injoignable. Réessayez plus tard.", noSource: "Aucune source configurée.", m3u: "Les listes M3U ne fournissent pas d'informations d'abonnement.",
  },
  es: {
    title: "Suscripción", sub: "Tu suscripción IPTV, según tu proveedor.", iptv: "Suscripción IPTV", source: "Fuente",
    status: "Estado de la cuenta", expires: "Caduca el", days: "días restantes", conns: "Conexiones en uso", created: "Creada el", trial: "Cuenta de prueba",
    yes: "Sí", server: "Servidor", formats: "Formatos", never: "Sin fecha de caducidad", unknown: "Desconocido", refresh: "Actualizar", loading: "Cargando…",
    offline: "No se pudo contactar con el proveedor. Inténtalo más tarde.", noSource: "No hay ninguna fuente configurada.", m3u: "Las listas M3U no proporcionan datos de suscripción.",
  },
  ar: {
    title: "الاشتراك", sub: "اشتراك IPTV الخاص بك كما يُبلغ عنه مزوّدك.", iptv: "اشتراك IPTV", source: "المصدر",
    status: "حالة الحساب", expires: "ينتهي في", days: "يوم متبقٍ", conns: "الاتصالات المستخدمة", created: "أُنشئ في", trial: "حساب تجريبي",
    yes: "نعم", server: "الخادم", formats: "الصيغ", never: "بدون تاريخ انتهاء", unknown: "غير معروف", refresh: "تحديث", loading: "جارٍ التحميل…",
    offline: "تعذّر الوصول إلى المزوّد. حاول لاحقًا.", noSource: "لا يوجد مصدر.", m3u: "قوائم M3U لا توفّر معلومات الاشتراك.",
  },
};
export type AccountStr = typeof STR.en;
export function useAccountStr(): AccountStr {
  const lang = usePrefs((s) => s.lang);
  return lang === "fr" ? STR.fr : lang === "es" ? STR.es : lang === "ar" ? STR.ar : STR.en;
}

export function AccountRow({ k, v }: { k: string; v: ReactNode }) {
  return <div className="acc-row"><span className="muted">{k}</span><span>{v}</span></div>;
}

/** Carte « Abonnement IPTV » de la source active (réutilisée par l'édition Pro). */
export function IptvAccountCard() {
  const t = useAccountStr();
  const lang = usePrefs((s) => s.lang);
  const source = useActiveSource();
  const [info, setInfo] = useState<XtreamHandshake["user_info"] | null>(null);
  const [srv, setSrv] = useState<XtreamHandshake["server_info"] | null>(null);
  const [failed, setFailed] = useState(false);
  const [busy, setBusy] = useState(false);
  const fmt = (ms: number) => new Date(ms).toLocaleDateString(lang, { day: "numeric", month: "long", year: "numeric" });

  const load = async () => {
    if (!source || source.type !== "xtream") return;
    setBusy(true);
    try {
      const h = await handshake(await currentTransport(), credsOf(source));
      setInfo(h.user_info ?? null); setSrv(h.server_info ?? null); setFailed(false);
    } catch { setFailed(true); } finally { setBusy(false); }
  };
  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [source?.id]);

  const num = (v: unknown) => (v == null || v === "" || v === "null" ? null : Number(v));
  const exp = num(info?.exp_date) ? num(info!.exp_date)! * 1000 : null;
  const created = num((info as { created_at?: unknown } | null)?.created_at);
  const status = info?.status ?? null;
  const days = exp ? Math.max(0, Math.ceil((exp - Date.now()) / 86_400_000)) : null;

  return (
    <section className="acc-card">
      <h2>{t.iptv}</h2>
      {!source ? <p className="muted">{t.noSource}</p> : (
        <>
          <AccountRow k={t.source} v={source.name} />
          {source.type !== "xtream" ? <p className="muted">{t.m3u}</p> : (
            <>
              <AccountRow k={t.status} v={<span className={`acc-pill ${status === "Active" ? "ok" : status ? "bad" : ""}`}>{status ?? (busy ? t.loading : t.unknown)}</span>} />
              <AccountRow k={t.expires} v={exp ? `${fmt(exp)} · ${days} ${t.days}` : info ? t.never : "—"} />
              <AccountRow k={t.conns} v={info ? `${num(info.active_cons) ?? 0} / ${num(info.max_connections) ?? source.maxConnections ?? 1}` : "—"} />
              {created ? <AccountRow k={t.created} v={fmt(created * 1000)} /> : null}
              {(info as { is_trial?: unknown } | null)?.is_trial === "1" && <AccountRow k={t.trial} v={t.yes} />}
              {info?.allowed_output_formats?.length ? <AccountRow k={t.formats} v={info.allowed_output_formats.join(", ")} /> : null}
              {srv?.url && <AccountRow k={t.server} v={<span className="mono">{srv.url}</span>} />}
              {failed && <p className="muted">{t.offline}</p>}
              <div className="acc-actions"><button className="btn" disabled={busy} onClick={() => void load()}>{busy ? t.loading : t.refresh}</button></div>
            </>
          )}
        </>
      )}
    </section>
  );
}

export function Account() {
  const t = useAccountStr();
  return (
    <div className="page">
      <div className="page-head"><div><h1>{t.title}</h1><div className="sub">{t.sub}</div></div></div>
      <div className="acc-grid"><IptvAccountCard /></div>
    </div>
  );
}
