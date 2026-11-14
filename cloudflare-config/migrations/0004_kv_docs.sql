-- Comptes, appareils, jetons et caches : sortis de KV (1 000 écritures/jour en gratuit) vers D1 (100 000/jour).
-- Valeurs identiques à celles de KV (déjà chiffrées quand elles contiennent des identifiants). exp = expiration en ms.
CREATE TABLE IF NOT EXISTS kv_docs (
  k   TEXT    PRIMARY KEY,
  v   TEXT    NOT NULL,
  exp INTEGER
);
CREATE INDEX IF NOT EXISTS idx_kv_docs_exp ON kv_docs (exp) WHERE exp IS NOT NULL;
