import { useNavigate } from "react-router-dom";
import { useT } from "@/i18n";
import { StateCard } from "@/ui/common";

export function NoSource() {
  const t = useT();
  const nav = useNavigate();
  return <StateCard icon="source" title={t("state.noSourceTitle")} body={t("state.noSourceBody")} actions={<button className="btn primary" onClick={() => nav("/welcome")}>{t("set.addSource")}</button>} />;
}

export function EmptyCatalog() {
  const t = useT();
  const nav = useNavigate();
  return <StateCard icon="alert" title={t("state.emptyCatalog")} body={t("state.emptyCatalogBody")} actions={<button className="btn primary" onClick={() => nav("/settings/categories")}>{t("set.categories")}</button>} />;
}
