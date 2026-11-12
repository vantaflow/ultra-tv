// Façade des pages HTML : le rendu vit dans src/pages/*.js (une page ou un groupe de pages par module) et dans
// src/ui/*.js (jetons de design, gabarit, composants). Ce module garde les exports historiques utilisés par index.js.

export { ico, LOGO } from "./ui/components.js";
export { layout, shell } from "./ui/layout.js";
export { ago, fmtTime, countryLabel, sourcesLabel } from "./ui/format.js";
export { loginPage, signupPage, envoyerPage, apkPage } from "./pages/public.js";
export { pairPage } from "./pages/pair.js";
export { dashboardPage } from "./pages/dashboard.js";
export { announcementsPanel, ANN_SCRIPT } from "./pages/alerts.js";
