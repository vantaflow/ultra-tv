import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "./styles/app.css";
import "./styles/v2.css";
import "./styles/v2-player.css";
import "./styles/v2-screens.css";

function mount() {
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
}

// Rendus de contrôle (web/scripts/screens.mjs) : build avec VITE_DEMO=1 → catalogue fictif semé avant l'affichage.
if (import.meta.env.VITE_DEMO === "1") void import("./demo").then((m) => m.seedDemo()).catch(() => {}).then(mount);
else mount();

// Animation d'ouverture : superposée à l'accueil déjà en cours de chargement, retirée à la fin (jamais bloquante).
const splash = document.getElementById("splash");
if (splash) setTimeout(() => splash.remove(), 800);
