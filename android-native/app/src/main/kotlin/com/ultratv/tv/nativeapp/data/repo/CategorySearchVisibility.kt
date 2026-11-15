package com.ultratv.tv.nativeapp.data.repo

/**
 * Category IDs excluded from catalogue search for one provider and content kind.
 *
 * Profile keys are stored as KIND:providerId:remoteId. Provider-disabled category IDs
 * are global and are supplied separately by CategoryDao.
 */
internal object CategorySearchVisibility {
    fun excludedRemoteIds(
        kind: String,
        providerId: Long,
        profileHiddenKeys: Set<String>,
        disabledRemoteIds: Collection<String>,
    ): List<String> {
        val prefix = "$kind:$providerId:"
        val profileHidden = profileHiddenKeys.mapNotNull { key ->
            key.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
        }
        return (profileHidden + disabledRemoteIds).distinct()
    }
}
