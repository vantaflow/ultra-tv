// Données liées à l'identifiant local d'un profil : favoris, historique, ordre des chaînes.
import { db } from "./db";

/** Supprime les données d'un profil (suppression manuelle ou profil supprimé sur un autre appareil). */
export async function deleteProfileData(profile: string): Promise<void> {
  await db.favorites.filter((f) => f.profile === profile).delete();
  await db.history.filter((h) => h.profile === profile).delete();
  await db.channelOrder.filter((o) => o.profile === profile).delete();
}
