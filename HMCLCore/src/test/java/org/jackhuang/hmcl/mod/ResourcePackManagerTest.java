package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerTest {

    @Test
    void testIsMcVersionSupported() {
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("26.1-snapshot-1")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("25w14craftmine")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.21")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.16.5")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.13-pre3")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("17w48a")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("13w24a")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.6.1")));

        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("13w23a")));
        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.6")));
        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("13w23a")));
        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("b1.1-1")));
    }
}
