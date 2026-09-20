package com.bodzey.proaudioplayer.core.discovery.nsd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNsdMulticastPolicyTest {

    @Test
    fun android12AndOlderNeedManualMulticastLock() {
        assertTrue(
            requiresManualMulticastLock(
                sdkInt = 32,
                tExtensionVersion = 0,
            ),
        )
    }

    @Test
    fun android13BeforeTExtension7NeedsManualMulticastLock() {
        assertTrue(
            requiresManualMulticastLock(
                sdkInt = 33,
                tExtensionVersion = 6,
            ),
        )
    }

    @Test
    fun android13TExtension7AndNewerUsePlatformManagement() {
        assertFalse(
            requiresManualMulticastLock(
                sdkInt = 33,
                tExtensionVersion = 7,
            ),
        )
    }

    @Test
    fun android14AndNewerUsePlatformManagement() {
        assertFalse(
            requiresManualMulticastLock(
                sdkInt = 34,
                tExtensionVersion = 0,
            ),
        )
    }
}
