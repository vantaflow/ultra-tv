import { env } from "cloudflare:workers";
import { applyD1Migrations } from "cloudflare:test";

// Base D1 locale vide à chaque fichier de test : on y applique les migrations du dossier migrations/.
await applyD1Migrations(env.DB, env.TEST_MIGRATIONS);
