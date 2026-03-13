/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import com.sun.jna.Pointer;
import javafx.stage.Stage;
import org.jackhuang.hmcl.util.platform.macos.AppKit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.OptionalLong;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MacOSNativeUtils {

    private static Pointer nsApp;
    private static Pointer aquaAppearance;
    private static Pointer darkAquaAppearance;
    private static boolean initialized;

    static {
        init();
    }

    private static void init() {
        if (AppKit.INSTANCE == null) {
            initialized = false;
            return;
        }

        try {
            AppKit objc = AppKit.INSTANCE;

            Pointer NSApplication = objc.objc_getClass("NSApplication");
            if (NSApplication == null) {
                initialized = false;
                return;
            }

            Pointer sharedSel = objc.sel_registerName("sharedApplication");
            nsApp = objc.objc_msgSend(NSApplication, sharedSel);

            if (nsApp == null) {
                initialized = false;
                return;
            }

            Pointer NSAppearance = objc.objc_getClass("NSAppearance");
            if (NSAppearance == null) {
                initialized = false;
                return;
            }

            Pointer namedSel = objc.sel_registerName("appearanceNamed:");

            Pointer aquaName = nsString("NSAppearanceNameAqua");
            Pointer darkAquaName = nsString("NSAppearanceNameDarkAqua");

            if (aquaName == null || darkAquaName == null) {
                initialized = false;
                return;
            }

            aquaAppearance = objc.objc_msgSend(NSAppearance, namedSel, aquaName);
            darkAquaAppearance = objc.objc_msgSend(NSAppearance, namedSel, darkAquaName);

            initialized = (aquaAppearance != null && darkAquaAppearance != null);
        } catch (Throwable t) {
            LOG.warning("Failed to initialize macOS appearance support", t);
            initialized = false;
        }
    }

    private static Pointer nsString(String value) {
        if (AppKit.INSTANCE == null) return null;

        try {
            AppKit objc = AppKit.INSTANCE;
            Pointer NSString = objc.objc_getClass("NSString");
            Pointer sel = objc.sel_registerName("stringWithUTF8String:");
            return objc.objc_msgSend(NSString, sel, value);
        } catch (Throwable t) {
            LOG.warning("Failed to create NSString", t);
            return null;
        }
    }

    public static boolean isSupported() {
        return initialized;
    }

    public static void setAppearance(boolean dark) {
        if (!initialized || nsApp == null) return;

        try {
            AppKit objc = AppKit.INSTANCE;
            Pointer setSel = objc.sel_registerName("setAppearance:");
            objc.objc_msgSend(nsApp, setSel, dark ? darkAquaAppearance : aquaAppearance);
        } catch (Throwable t) {
            LOG.warning("Failed to set macOS appearance", t);
        }
    }

    public static OptionalLong getWindowHandle(Stage stage) {
        try {
            Class<?> windowStageClass = Class.forName("com.sun.javafx.tk.quantum.WindowStage");
            Class<?> glassWindowClass = Class.forName("com.sun.glass.ui.Window");
            Class<?> tkStageClass = Class.forName("com.sun.javafx.tk.TKStage");

            Object tkStage = MethodHandles.privateLookupIn(javafx.stage.Window.class, MethodHandles.lookup())
                    .findVirtual(javafx.stage.Window.class, "getPeer", MethodType.methodType(tkStageClass))
                    .invoke(stage);

            MethodHandles.Lookup windowStageLookup = MethodHandles.privateLookupIn(windowStageClass, MethodHandles.lookup());
            MethodHandle getPlatformWindow = windowStageLookup.findVirtual(windowStageClass, "getPlatformWindow", MethodType.methodType(glassWindowClass));
            Object platformWindow = getPlatformWindow.invoke(tkStage);

            long handle = (long) MethodHandles.privateLookupIn(glassWindowClass, MethodHandles.lookup())
                    .findVirtual(glassWindowClass, "getNativeWindow", MethodType.methodType(long.class))
                    .invoke(platformWindow);

            return OptionalLong.of(handle);
        } catch (Throwable ex) {
            LOG.warning("Failed to get window handle", ex);
            return OptionalLong.empty();
        }
    }

    private MacOSNativeUtils() {
    }
}
