-- Télémétrie et « dernière activité » des appareils : sortis de KV (quota gratuit de 1 000 écritures par jour)
-- vers D1 (100 000 écritures par jour). Aucune adresse IP n'est stockée.

-- Journaux (kind = 'event', conservés 7 jours) et plantages (kind = 'crash', 30 jours).
CREATE TABLE IF NOT EXISTS events (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  kind         TEXT    NOT NULL,           -- 'event' | 'crash'
  ts           INTEGER NOT NULL,           -- millisecondes
  device_id    TEXT,
  mac          TEXT,                       -- étiquette fournie par l'appareil, non fiable
  version      TEXT,
  version_code INTEGER,
  device       TEXT,
  acct         TEXT,                       -- empreinte HMAC courte du compte (jamais l'identifiant en clair)
  country      TEXT,
  level        TEXT,                       -- événements
  tag          TEXT,                       -- événements
  message      TEXT,                       -- événements
  android_sdk  INTEGER,                    -- plantages
  stack        TEXT                        -- plantages
);
CREATE INDEX IF NOT EXISTS idx_events_kind_ts ON events (kind, ts DESC);

-- Installations sans compte (relevé anonyme), conservées 180 jours après le dernier relevé.
CREATE TABLE IF NOT EXISTS anon_pings (
  fingerprint TEXT    PRIMARY KEY,         -- SHA-256 tronqué de l'identifiant d'installation
  first_seen  INTEGER NOT NULL,
  last_seen   INTEGER NOT NULL,
  version     TEXT,
  country     TEXT,
  web         INTEGER NOT NULL DEFAULT 0   -- sources envoyées depuis le web
);
CREATE INDEX IF NOT EXISTS idx_anon_last ON anon_pings (last_seen);

-- Dernière activité d'un appareil appairé, clé = SHA-256 du jeton (comme `dev:<hash>` dans KV).
CREATE TABLE IF NOT EXISTS device_seen (
  hash TEXT    PRIMARY KEY,
  seen INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_device_seen_seen ON device_seen (seen);
