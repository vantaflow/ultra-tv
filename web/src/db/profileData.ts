// Données liées à l'identifiant local d'un profil : favoris, historique, ordre des chaînes, catégories masquées, groupes de chaînes.
import { db } from "./db";
import { deleteProfileGroups } from "./channelGroups";

/** Supprime les données d'un profil (suppression manuelle ou profil supprimé sur un autre appareil). */
export async function deleteProfileData(profile: string): Promise<void> {
  await db.favorites.filter((f) => f.profile === profile).delete();
  await db.history.filter((h) => h.profile === profile).delete();
  await db.channelOrder.filter((o) => o.profile === profile).delete();
  await db.profileHidden.where("[profile+sourceId]").between([profile, -Infinity], [profile, Infinity]).delete();
  await db.profileHiddenChannels.where("[profile+sourceId]").between([profile, -Infinity], [profile, Infinity]).delete();
  await db.profileCatOrder.where("[profile+sourceId]").between([profile, -Infinity], [profile, Infinity]).delete();
  await deleteProfileGroups(profile);
}
