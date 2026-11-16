package com.ultratv.tv.nativeapp.data.repo

import com.ultratv.tv.nativeapp.data.db.ChannelDao
import com.ultratv.tv.nativeapp.data.db.ChannelEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository @Inject constructor(
    private val dao: ChannelDao,
) {
    suspend fun byId(id: Long): ChannelEntity? = dao.byId(id)
}
