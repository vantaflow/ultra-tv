const test = require("node:test");
const assert = require("node:assert");
const { contextMenuTemplate } = require("../contextmenu.cjs");

const roles = (t) => t.filter((x) => x.role).map((x) => x.role);

test("champ de saisie : coller toujours proposé, même sans sélection", () => {
  const t = contextMenuTemplate({ isEditable: true, selectionText: "", editFlags: { canPaste: true, canCut: true, canCopy: true } }, "fr-FR");
  assert.deepStrictEqual(roles(t), ["undo", "redo", "cut", "copy", "paste", "selectAll"]);
  const paste = t.find((x) => x.role === "paste");
  assert.strictEqual(paste.label, "Coller");
  assert.strictEqual(paste.enabled, true);
  // Rien de sélectionné : couper et copier grisés.
  assert.strictEqual(t.find((x) => x.role === "copy").enabled, false);
});

test("texte sélectionné hors champ : copier seulement", () => {
  assert.deepStrictEqual(roles(contextMenuTemplate({ isEditable: false, selectionText: "abc" }, "en-US")), ["copy"]);
});

test("ailleurs : aucun menu ; langue inconnue : anglais", () => {
  assert.deepStrictEqual(contextMenuTemplate({ isEditable: false, selectionText: "" }, "fr"), []);
  assert.strictEqual(contextMenuTemplate({ isEditable: true, editFlags: {} }, "xx").find((x) => x.role === "paste").label, "Paste");
});
