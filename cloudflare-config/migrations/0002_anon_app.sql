-- Installations sans compte : application utilisée (plateforme) et modèle de l'appareil.
-- Aucune adresse IP n'est stockée ; `model` est un libellé court (64 caractères au plus, nettoyé côté Worker).
ALTER TABLE anon_pings ADD COLUMN platform TEXT;   -- 'android-tv' | 'android-mobile' | 'windows' | 'macos' | 'linux'
ALTER TABLE anon_pings ADD COLUMN model TEXT;
CREATE INDEX IF NOT EXISTS idx_anon_platform ON anon_pings (platform);
CREATE INDEX IF NOT EXISTS idx_anon_first ON anon_pings (first_seen);
