-- Écritures fréquentes sorties de KV (quota gratuit de 1 000 écritures/jour épuisé, appairage impossible) :
-- dernière activité et pays de chaque appareil appairé, et état partagé (favoris, reprise, derniers vus) par source.
CREATE TABLE IF NOT EXISTS device_activity (
  device_id TEXT    PRIMARY KEY,
  last_seen INTEGER NOT NULL,             -- millisecondes, rafraîchi au plus une fois par heure
  country   TEXT
);
CREATE INDEX IF NOT EXISTS idx_device_activity_seen ON device_activity (last_seen);
CREATE TABLE IF NOT EXISTS shared_state (
  k    TEXT    PRIMARY KEY,               -- st:<login>:<id de source>
  blob TEXT    NOT NULL,                  -- chiffré (AES-GCM, lié au login et à la source)
  at   INTEGER NOT NULL
);
