(function () {
  var root = document.documentElement;
  function get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } }
  function set(k, v) { try { localStorage.setItem(k, v); } catch (e) {} }
  var lang = get("lang") === "en" ? "en" : "fr"; // FR par défaut
  function applyLang(l) { root.dataset.lang = l; root.lang = l; var b = document.getElementById("lang"); if (b) b.textContent = l === "fr" ? "EN" : "FR"; }
  applyLang(lang);
  var theme = get("theme");
  if (theme) root.dataset.theme = theme;
  document.getElementById("lang").addEventListener("click", function () { lang = lang === "fr" ? "en" : "fr"; set("lang", lang); applyLang(lang); });
  document.getElementById("theme").addEventListener("click", function () {
    var dark = root.dataset.theme ? root.dataset.theme === "dark" : !matchMedia("(prefers-color-scheme: light)").matches;
    var next = dark ? "light" : "dark"; root.dataset.theme = next; set("theme", next);
  });
  // Dernière version publiée (repli : le lien « latest » fonctionne sans cette requête).
  fetch("https://api.github.com/repos/khalilbenaz/ultra-tv/releases/latest").then(function (r) { return r.ok ? r.json() : null; }).then(function (j) {
    if (!j || !j.tag_name) return;
    document.querySelectorAll("[data-version]").forEach(function (n) { n.textContent = j.tag_name; });
  }).catch(function () {});
})();
