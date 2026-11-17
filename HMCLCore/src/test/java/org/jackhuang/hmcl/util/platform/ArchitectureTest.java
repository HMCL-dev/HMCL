// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Verifies that architecture names describe the input rather than the current machine.
@NotNullByDefault
public final class ArchitectureTest {

    /// Kernel version and JVM byte order must not affect parsing of another platform's architecture.
    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    public void testParsingIgnoresCurrentEnvironment() {
        @Nullable String originalEndian = System.getProperty("sun.cpu.endian");
        @Nullable String originalVersion = System.getProperty("os.version");
        try {
            for (String endian : List.of("little", "big")) {
                System.setProperty("sun.cpu.endian", endian);
                for (String version : List.of("5.18", "5.19", "6.12")) {
                    System.setProperty("os.version", version);
                    assertEquals(Architecture.PPC64, Architecture.parseArchName("ppc64"));
                    assertEquals(Architecture.PPC64, Architecture.parseArchName("powerpc64"));
                    assertEquals(Architecture.PPC64LE, Architecture.parseArchName("ppc64le"));
                    assertEquals(Architecture.LOONGARCH64, Architecture.parseArchName("loongarch64"));
                    assertEquals(Architecture.LOONGARCH64_OW, Architecture.parseArchName("loongarch64_ow"));
                }
            }
        } finally {
            restoreProperty("sun.cpu.endian", originalEndian);
            restoreProperty("os.version", originalVersion);
        }
    }

    /// Common aliases and missing values retain their normalization behavior.
    @Test
    public void testAliases() {
        assertEquals(Architecture.X86_64, Architecture.parseArchName(" AMD64 "));
        assertEquals(Architecture.ARM64, Architecture.parseArchName("aarch64"));
        assertEquals(Architecture.ARM32, Architecture.parseArchName("armv7l"));
        assertEquals(Architecture.UNKNOWN, Architecture.parseArchName(null));
        assertEquals(Architecture.UNKNOWN, Architecture.parseArchName("unsupported"));
    }

    /// Restores a system property, including its original absence.
    private static void restoreProperty(String name, @Nullable String value) {
        if (value == null)
            System.clearProperty(name);
        else
            System.setProperty(name, value);
    }
}
