// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import com.sun.jna.Pointer;
import org.jackhuang.hmcl.ui.MacOSNativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.macos.ObjectiveCRuntime;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Verifies that macOS shows HMCL instead of the main-class name.
@NotNullByDefault
final class MacOSApplicationNameTest {
    private static final String APPLICATION_NAME_PROPERTY = "apple.awt.application.name";

    /// Clears the AWT name and expects HMCL on macOS, and no property elsewhere.
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void installMacApplicationNameUsesHmcl() {
        @Nullable String previous = System.getProperty(APPLICATION_NAME_PROPERTY);
        try {
            System.clearProperty(APPLICATION_NAME_PROPERTY);
            EntryPoint.installMacApplicationName();
            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                assertEquals(Metadata.NAME, System.getProperty(APPLICATION_NAME_PROPERTY));
            } else {
                assertNull(System.getProperty(APPLICATION_NAME_PROPERTY));
            }
        } finally {
            restoreApplicationNameProperty(previous);
        }
    }

    /// Keeps an explicit -Dapple.awt.application.name on every operating system.
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void installMacApplicationNamePreservesExistingValue() {
        @Nullable String previous = System.getProperty(APPLICATION_NAME_PROPERTY);
        String custom = "CustomApplicationName";
        try {
            System.setProperty(APPLICATION_NAME_PROPERTY, custom);
            EntryPoint.installMacApplicationName();
            assertEquals(custom, System.getProperty(APPLICATION_NAME_PROPERTY));
        } finally {
            restoreApplicationNameProperty(previous);
        }
    }

    /// Updates NSProcessInfo.processName and restores the previous name.
    @Test
    void setApplicationNameUpdatesProcessName() {
        assumeTrue(OperatingSystem.CURRENT_OS == OperatingSystem.MACOS);
        assumeTrue(MacOSNativeUtils.isSupported());

        String previous = currentProcessName();
        String sentinel = "HMCLApplicationNameTest";
        try {
            MacOSNativeUtils.setApplicationName(sentinel);
            assertEquals(sentinel, currentProcessName());
        } finally {
            MacOSNativeUtils.setApplicationName(previous);
        }
    }

    private static void restoreApplicationNameProperty(@Nullable String previous) {
        if (previous == null) {
            System.clearProperty(APPLICATION_NAME_PROPERTY);
        } else {
            System.setProperty(APPLICATION_NAME_PROPERTY, previous);
        }
    }

    /// Reads `[[NSProcessInfo processInfo] processName]` through `UTF8String`.
    private static String currentProcessName() {
        ObjectiveCRuntime objc = ObjectiveCRuntime.INSTANCE;
        Pointer processInfo = objc.objc_msgSend(
                objc.objc_getClass("NSProcessInfo"),
                objc.sel_registerName("processInfo"));
        Pointer name = objc.objc_msgSend(processInfo, objc.sel_registerName("processName"));
        Pointer utf8 = objc.objc_msgSend(name, objc.sel_registerName("UTF8String"));
        return utf8.getString(0, "UTF-8");
    }
}
