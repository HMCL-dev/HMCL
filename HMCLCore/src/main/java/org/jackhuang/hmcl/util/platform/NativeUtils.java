// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class NativeUtils {
    public static final boolean USE_JNA = useJNA();

    public static <T extends Library> @Nullable T load(String name, Class<T> interfaceClass) {
        return load(name, interfaceClass, Collections.emptyMap());
    }

    public static <T extends Library> @Nullable T load(String name, Class<T> interfaceClass, Map<String, ?> options) {
        if (USE_JNA) {
            try {
                return Native.load(name, interfaceClass, options);
            } catch (UnsatisfiedLinkError e) {
                LOG.warning("Failed to load native library: " + name, e);
            }
        }

        return null;
    }

    private static boolean useJNA() {
        String backend = System.getProperty("hmcl.native.backend");
        if (backend == null || "auto".equalsIgnoreCase(backend)) {
            try {
                if (Platform.isWindows()) {
                    String osVersion = System.getProperty("os.version");

                    // Requires Windows 7 or later (6.1+)
                    // https://learn.microsoft.com/windows/win32/sysinfo/operating-system-version
                    if (osVersion == null || osVersion.startsWith("5.") || osVersion.equals("6.0"))
                        return false;

                    Native.getDefaultStringEncoding();
                    return true;
                }

                if (Platform.isMac()) {
                    String osVersion = System.getProperty("os.version");

                    // Require macOS 10.14 or later
                    if (osVersion != null) {
                        String[] parts = osVersion.split("\\.");
                        if (parts.length >= 2) {
                            try {
                                int major = Integer.parseInt(parts[0]);
                                int minor = Integer.parseInt(parts[1]);
                                if (major < 10 || (major == 10 && minor < 14)) {
                                    return false;
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    Native.getDefaultStringEncoding();
                    return true;
                }

                return false;
            } catch (Throwable ignored) {
                return false;
            }
        } else if ("jna".equalsIgnoreCase(backend)) {
            // Ensure JNA is available
            Native.getDefaultStringEncoding();
            return true;
        } else if ("none".equalsIgnoreCase(backend))
            return false;
        else
            throw new Error("Unsupported native backend: " + backend);
    }

    private NativeUtils() {
    }
}
