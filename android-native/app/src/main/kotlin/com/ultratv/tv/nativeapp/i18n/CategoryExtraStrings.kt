package com.ultratv.tv.nativeapp.i18n

import com.ultratv.tv.nativeapp.R

/** Gérer les catégories : tri, chaînes masquées. Fichier à part : [DesignStrings] reste intact. */
val DesignStrings.catSortAz get() = tx(R.string.cat_sort_az)
val DesignStrings.catChannels get() = tx(R.string.cat_channels)
fun DesignStrings.catChannelsTitle(name: String) = I18n.string(lang, R.string.cat_channels_title, name)
val DesignStrings.catChannelsHint get() = tx(R.string.cat_channels_hint)
val DesignStrings.catChannelShown get() = tx(R.string.cat_channel_shown)
val DesignStrings.catChannelHidden get() = tx(R.string.cat_channel_hidden)
val DesignStrings.catOrderHint get() = tx(R.string.cat_order_hint)
