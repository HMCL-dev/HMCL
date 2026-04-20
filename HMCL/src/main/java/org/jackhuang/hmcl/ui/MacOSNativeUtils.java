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
import org.jackhuang.hmcl.util.platform.macos.ObjectiveCRuntime;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class MacOSNativeUtils {

    private static final Pointer nsApp = init();

    private static @Nullable Pointer init() {
        if (ObjectiveCRuntime.INSTANCE == null) {
            return null;
        }

        try {
            var objc = ObjectiveCRuntime.INSTANCE;

            Pointer nsApplication = objc.objc_getClass("NSApplication");
            if (!isNull(nsApplication)) {
                Pointer sharedSel = objc.sel_registerName("sharedApplication");
                if (!isNull(sharedSel))
                    return objc.objc_msgSend(nsApplication, sharedSel);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to initialize macOS appearance support", e);
        }

        return null;
    }

    public static boolean isSupported() {
        return nsApp != null;
    }

    private static boolean isNull(Pointer pointer) {
        return pointer == null || Pointer.nativeValue(pointer) == 0;
    }

    public static void setAppearance(boolean dark) {
        setAppearance(dark, false);
    }

    public static void setAppearance(boolean dark, boolean highContrast) {
        if (nsApp == null) return;

        try {
            var objc = ObjectiveCRuntime.INSTANCE;

            Pointer nsAppearance = objc.objc_getClass("NSAppearance");
            if (isNull(nsAppearance))
                return;

            Pointer namedSel = objc.sel_registerName("appearanceNamed:");
            Pointer nsString = objc.objc_getClass("NSString");
            if (isNull(nsString)) return;

            Pointer sel = objc.sel_registerName("stringWithUTF8String:");

            String appearanceName;
            if (highContrast) {
                appearanceName = dark ? "NSAppearanceNameAccessibilityHighContrastDarkAqua" : "NSAppearanceNameAccessibilityHighContrastAqua";
            } else {
                appearanceName = dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua";
            }

            Pointer appearanceNamePtr = objc.objc_msgSend(nsString, sel, appearanceName);
            if (isNull(appearanceNamePtr)) return;

            Pointer appearance = objc.objc_msgSend(nsAppearance, namedSel, appearanceNamePtr);
            if (isNull(appearance)) return;

            Pointer setSel = objc.sel_registerName("setAppearance:");
            objc.objc_msgSend(nsApp, setSel, appearance);
        } catch (Throwable t) {
            LOG.warning("Failed to set macOS appearance", t);
        }
    }

    private MacOSNativeUtils() {
    }
}
