package com.gameyourfitness.app.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Ersetzt Dispatchers.Main in ViewModel-Unit-Tests durch einen TestDispatcher
 * (JUnit-5-Pendant zur bekannten JUnit-4-MainDispatcherRule). Unconfined, damit
 * StateFlow-Updates synchron beobachtbar sind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherExtension(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        Dispatchers.setMain(dispatcher)
    }

    override fun afterEach(context: ExtensionContext?) {
        Dispatchers.resetMain()
    }
}
