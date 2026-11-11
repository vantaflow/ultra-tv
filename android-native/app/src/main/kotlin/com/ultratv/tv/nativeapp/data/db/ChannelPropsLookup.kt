package com.ultratv.tv.nativeapp.data.db

import com.ultratv.tv.nativeapp.data.m3u.StreamProps

/** Propriétés de flux protégé de la chaîne « fournisseur:chaîne » (clé de lecture) ; null = flux ordinaire. */
suspend fun ChannelDao.streamProps(key: String): StreamProps? {
    val pid = key.substringBefore(':').toLongOrNull() ?: return null
    return StreamProps.fromJson(propsOf(pid, key.substringAfter(':')))?.takeIf { it.isProtected }
}
