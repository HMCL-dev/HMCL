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

    /// Clears the explicit application appearance, so that the application inherits the system appearance again.
    ///
    /// While an explicit appearance is set, `NSApp.effectiveAppearance` reports that appearance rather than the
    /// system one. JavaFX derives [javafx.application.Platform.Preferences#colorSchemeProperty()] from
    /// `effectiveAppearance`, so leaving an appearance pinned makes the reported color scheme follow the launcher
    /// instead of the system.
    public static void clearAppearance() {
        if (nsApp == null) return;

        try {
            var objc = ObjectiveCRuntime.INSTANCE;
            // A null Pointer maps to Objective-C nil, which resets the appearance to inherited.
            objc.objc_msgSend(nsApp, objc.sel_registerName("setAppearance:"), (Pointer) null);
        } catch (Throwable t) {
            LOG.warning("Failed to clear macOS appearance", t);
        }
    }

    /// Reads whether the system is currently using dark mode, independently of the application appearance.
    ///
    /// This reads the `AppleInterfaceStyle` user default, which reflects the system setting and is unaffected by
    /// [#setAppearance(boolean)]. It must not be derived from `effectiveAppearance` or from the JavaFX color
    /// scheme, because both report the pinned application appearance once one has been set.
    ///
    /// @return whether the system is in dark mode, or `null` if it could not be determined
    public static @Nullable Boolean isSystemInDarkMode() {
        if (nsApp == null) return null;

        try {
            var objc = ObjectiveCRuntime.INSTANCE;

            @Nullable Pointer userDefaults = objc.objc_getClass("NSUserDefaults");
            if (isNull(userDefaults)) return null;

            @Nullable Pointer defaults = objc.objc_msgSend(userDefaults, objc.sel_registerName("standardUserDefaults"));
            if (isNull(defaults)) return null;

            @Nullable Pointer nsString = objc.objc_getClass("NSString");
            if (isNull(nsString)) return null;

            @Nullable Pointer key = objc.objc_msgSend(nsString, objc.sel_registerName("stringWithUTF8String:"), "AppleInterfaceStyle");
            if (isNull(key)) return null;

            @Nullable Pointer value = objc.objc_msgSend(defaults, objc.sel_registerName("stringForKey:"), key);
            // The key is absent in light mode.
            if (isNull(value)) return Boolean.FALSE;

            @Nullable Pointer utf8 = objc.objc_msgSend(value, objc.sel_registerName("UTF8String"));
            if (isNull(utf8)) return null;

            return "Dark".equalsIgnoreCase(utf8.getString(0));
        } catch (Throwable t) {
            LOG.warning("Failed to read macOS system appearance", t);
            return null;
        }
    }

    private MacOSNativeUtils() {
    }
}
