// Fonction Vercel : POST /api/xtream (voir ../lib.js). Clé partagée dans la variable d'environnement RELAY_KEY.
import { handle } from "../lib.js";

export function POST(request) {
  return handle(request, process.env.RELAY_KEY);
}
