// Fenêtre du code parental demandée par la porte des contenus (state/unlocks : askParentalCode). Sans code défini, rien ne
// s'ouvre : un contenu protégé ne devient jamais accessible faute de code.

import { useT } from "@/i18n";
import { checkParentalCode, hasParentalCode } from "@/lib/parental";
import { CodeModal } from "@/screens/ScreenTimeEditor";
import { useUnlocks } from "@/state/unlocks";
import { Modal } from "./common";

export function ParentalPrompt() {
  const t = useT();
  const req = useUnlocks((s) => s.request);
  if (!req) return null;
  if (!hasParentalCode()) {
    return (
      <Modal title={t("gate.title")} onClose={() => req.resolve(false)} foot={<button className="btn primary" onClick={() => req.resolve(false)}>{t("common.close")}</button>}>
        <p>{t("gate.noCode")}</p>
      </Modal>
    );
  }
  return (
    <CodeModal title={t("gate.title")} label={t("gate.enter", { name: req.label })} onClose={() => req.resolve(false)}
      onSubmit={async (c) => { if (!(await checkParentalCode(c))) return false; req.resolve(true); return true; }} />
  );
}
