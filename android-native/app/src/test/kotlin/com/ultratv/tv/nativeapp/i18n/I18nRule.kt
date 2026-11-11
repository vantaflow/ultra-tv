package com.ultratv.tv.nativeapp.i18n

import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Initialise [I18n] avec le contexte Robolectric : à ajouter aux tests qui lisent des libellés (`@get:Rule`). */
class I18nRule : TestWatcher() {
    override fun starting(description: Description) {
        I18n.init(ApplicationProvider.getApplicationContext())
    }
}
