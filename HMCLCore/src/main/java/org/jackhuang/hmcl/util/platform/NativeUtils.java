/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

                    // Currently we only need to use JNA on Windows
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
