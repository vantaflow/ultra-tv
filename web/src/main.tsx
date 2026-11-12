import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "./styles/app.css";
import "./styles/v2.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

// Animation d'ouverture : superposée à l'accueil déjà en cours de chargement, retirée à la fin (jamais bloquante).
const splash = document.getElementById("splash");
if (splash) setTimeout(() => splash.remove(), 800);
