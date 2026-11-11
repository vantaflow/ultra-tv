// Réinitialise le mot de passe d'un compte du Worker (KV de production), sans le passer en argument :
//   cd cloudflare-config && node scripts/reset-password.mjs <identifiant>
// Le mot de passe est demandé au clavier (non affiché, jamais écrit sur disque). Les autres champs du compte sont gardés ;
// sessEpoch est incrémenté pour fermer les sessions ouvertes.
import { execFileSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { hashPassword } from "../src/crypto.js";

const login = (process.argv[2] || "").trim().toLowerCase();
if (!login) { console.error("Usage : node scripts/reset-password.mjs <identifiant>"); process.exit(1); }

const wrangler = (args, opts = {}) =>
  execFileSync(process.platform === "win32" ? "npx.cmd" : "npx", ["wrangler", "kv", "key", ...args, "--binding", "CONFIG", "--remote", "--preview", "false"], { encoding: "utf8", shell: process.platform === "win32", ...opts });

function ask(prompt) {
  return new Promise((resolve) => {
    process.stdout.write(prompt);
    const stdin = process.stdin;
    let buf = "";
    stdin.setRawMode?.(true);
    stdin.resume();
    stdin.setEncoding("utf8");
    const onData = (ch) => {
      if (ch === "\r" || ch === "\n" || ch === "\u0004") {
        stdin.setRawMode?.(false); stdin.pause(); stdin.off("data", onData); process.stdout.write("\n"); resolve(buf);
      } else if (ch === "\u0003") { process.exit(130); }
      else if (ch === "\u007f" || ch === "\b") buf = buf.slice(0, -1);
      else buf += ch;
    };
    stdin.on("data", onData);
  });
}

const raw = wrangler(["get", `acct:${login}`], { stdio: ["ignore", "pipe", "ignore"] });
let acct;
try { acct = JSON.parse(raw); } catch { console.error(`Compte « ${login} » introuvable.`); process.exit(1); }

// Sans terminal interactif (ex. « ! » de Claude Code) : mot de passe lu dans UTV_NEW_PASSWORD.
const fromEnv = process.env.UTV_NEW_PASSWORD;
let pw;
if (fromEnv) pw = fromEnv;
else if (!process.stdin.isTTY) { console.error("Pas de terminal interactif : lancez le script dans PowerShell, ou définissez UTV_NEW_PASSWORD."); process.exit(1); }
else {
  pw = await ask("Nouveau mot de passe (10 caractères min.) : ");
  const pw2 = await ask("Confirmer : ");
  if (pw !== pw2) { console.error("Les deux saisies diffèrent."); process.exit(1); }
}
if (pw.length < 10) { console.error("Trop court."); process.exit(1); }

acct.passwordHash = await hashPassword(pw);
delete acct.salt;
acct.sessEpoch = (Number(acct.sessEpoch) || 0) + 1;

const dir = mkdtempSync(join(tmpdir(), "utv-acct-"));
const file = join(dir, "acct.json");
try {
  writeFileSync(file, JSON.stringify(acct));
  wrangler(["put", `acct:${login}`, "--path", file], { stdio: "inherit" });
  console.log(`Mot de passe de « ${login} » mis à jour.`);
} finally {
  rmSync(dir, { recursive: true, force: true });
}
