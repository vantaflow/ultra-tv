// Menu du clic droit : Electron n'en affiche aucun par défaut, et le bureau n'a pas de barre de menus hors macOS.
// Sans lui, impossible de coller une adresse ou un mot de passe à la souris dans un champ (Windows surtout).
// Pur et testable : le processus principal ne fait que transformer le résultat en Menu.

const LABELS = {
  fr: { cut: "Couper", copy: "Copier", paste: "Coller", selectAll: "Tout sélectionner", undo: "Annuler", redo: "Rétablir" },
  en: { cut: "Cut", copy: "Copy", paste: "Paste", selectAll: "Select all", undo: "Undo", redo: "Redo" },
  es: { cut: "Cortar", copy: "Copiar", paste: "Pegar", selectAll: "Seleccionar todo", undo: "Deshacer", redo: "Rehacer" },
  ar: { cut: "قص", copy: "نسخ", paste: "لصق", selectAll: "تحديد الكل", undo: "تراجع", redo: "إعادة" },
  pl: { cut: "Wytnij", copy: "Kopiuj", paste: "Wklej", selectAll: "Zaznacz wszystko", undo: "Cofnij", redo: "Ponów" },
  pt: { cut: "Recortar", copy: "Copiar", paste: "Colar", selectAll: "Selecionar tudo", undo: "Desfazer", redo: "Refazer" },
  tr: { cut: "Kes", copy: "Kopyala", paste: "Yapıştır", selectAll: "Tümünü seç", undo: "Geri al", redo: "Yinele" },
};

function labelsFor(locale) {
  const l = String(locale || "en").toLowerCase().slice(0, 2);
  return LABELS[l] || LABELS.en;
}

/**
 * Entrées du menu pour un clic droit ([params] de l'événement `context-menu`) : champ de saisie → annuler / rétablir,
 * couper, copier, coller, tout sélectionner ; texte sélectionné ailleurs → copier ; sinon aucun menu (liste vide).
 */
function contextMenuTemplate(params, locale) {
  const t = labelsFor(locale);
  const f = (params && params.editFlags) || {};
  const hasSelection = Boolean(params && params.selectionText && params.selectionText.trim());
  if (params && params.isEditable) {
    return [
      { role: "undo", label: t.undo, enabled: f.canUndo !== false },
      { role: "redo", label: t.redo, enabled: f.canRedo !== false },
      { type: "separator" },
      { role: "cut", label: t.cut, enabled: f.canCut !== false && hasSelection },
      { role: "copy", label: t.copy, enabled: f.canCopy !== false && hasSelection },
      { role: "paste", label: t.paste, enabled: f.canPaste !== false },
      { type: "separator" },
      { role: "selectAll", label: t.selectAll, enabled: f.canSelectAll !== false },
    ];
  }
  if (hasSelection) return [{ role: "copy", label: t.copy }];
  return [];
}

module.exports = { contextMenuTemplate, labelsFor };
