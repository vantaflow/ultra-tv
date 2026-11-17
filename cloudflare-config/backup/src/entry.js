// Point d'entrée du bundle : le même Worker que sur Cloudflare, et la fabrique d'environnement de lecture seule.
import worker from "../../src/index.js";
import { createHandler } from "./handler.js";

export const handler = createHandler({ worker });
