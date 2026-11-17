// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.sun.jna.Pointer;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.util.StringUtils;
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

    /// Installed on the first appearance update so naming the application does not load theme settings.
    private static boolean appearanceListenerInstalled;

    /// Registers, at most once, a listener that reapplies the appearance when automatic brightness changes.
    ///
    /// Deferred from class initialization so that [#setApplicationName(String)] does not touch [Themes].
    private static synchronized void installAppearanceListener() {
        if (appearanceListenerInstalled)
            return;
        appearanceListenerInstalled = true;
        FXUtils.onChange(Themes.autoBrightnessProperty(), ignored -> setAppearance(Themes.darkModeProperty().get()));
    }

    /// Applies the application appearance, clearing it to follow the system when automatic brightness is enabled.
    ///
    /// Installs the automatic-brightness listener on first use.
    ///
    /// @param dark whether to use the dark appearance when automatic brightness is disabled
    public static void setAppearance(boolean dark) {
        installAppearanceListener();
        if (Themes.autoBrightnessProperty().get()) {
            clearAppearance();
        } else {
            setAppearance(dark, false);
        }
    }

    public static void clearAppearance() {
        if (nsApp == null) return;

        try {
            var objc = ObjectiveCRuntime.INSTANCE;
            Pointer setSel = objc.sel_registerName("setAppearance:");
            objc.objc_msgSend(nsApp, setSel, Pointer.NULL);
        } catch (Throwable t) {
            LOG.warning("Failed to set macOS appearance", t);
        }
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

    /// Sets the process name and the bold application-menu title.
    ///
    /// Blank names are ignored. Objective-C failures are logged and do not abort startup.
    /// `setProcessName:` and `setTitle:` take an `NSString`, not a C string.
    ///
    /// @param name the application name
    public static void setApplicationName(String name) {
        if (nsApp == null || StringUtils.isBlank(name))
            return;

        try {
            var objc = ObjectiveCRuntime.INSTANCE;

            Pointer nsString = objc.objc_getClass("NSString");
            if (isNull(nsString))
                return;

            Pointer nameString = objc.objc_msgSend(nsString, objc.sel_registerName("stringWithUTF8String:"), name);
            if (isNull(nameString))
                return;

            Pointer nsProcessInfo = objc.objc_getClass("NSProcessInfo");
            if (!isNull(nsProcessInfo)) {
                Pointer processInfo = objc.objc_msgSend(nsProcessInfo, objc.sel_registerName("processInfo"));
                if (!isNull(processInfo))
                    objc.objc_msgSend(processInfo, objc.sel_registerName("setProcessName:"), nameString);
            }

            Pointer mainMenu = objc.objc_msgSend(nsApp, objc.sel_registerName("mainMenu"));
            if (isNull(mainMenu))
                return;

            // itemAtIndex: raises an uncatchable NSRangeException on an empty menu.
            // numberOfItems returns an NSInteger in the same register as a pointer result.
            long itemCount = Pointer.nativeValue(objc.objc_msgSend(mainMenu, objc.sel_registerName("numberOfItems")));
            if (itemCount <= 0)
                return;

            // itemAtIndex: takes an NSInteger, which is a 64-bit long on supported macOS versions.
            Pointer appMenuItem = objc.objc_msgSend(mainMenu, objc.sel_registerName("itemAtIndex:"), 0L);
            if (isNull(appMenuItem))
                return;

            Pointer setTitle = objc.sel_registerName("setTitle:");
            objc.objc_msgSend(appMenuItem, setTitle, nameString);

            Pointer submenu = objc.objc_msgSend(appMenuItem, objc.sel_registerName("submenu"));
            if (!isNull(submenu))
                objc.objc_msgSend(submenu, setTitle, nameString);
        } catch (Throwable t) {
            LOG.warning("Failed to set macOS application name", t);
        }
    }

    /// Publishes [Metadata#NAME] immediately and again each time the stage is shown.
    ///
    /// The application menu may not exist until the first window is shown, and JavaFX can rebuild it then.
    /// A replacement stage receives the same name. Returns without throwing when Objective-C is unavailable.
    ///
    /// @param stage the main stage
    public static void installApplicationName(Stage stage) {
        if (nsApp == null)
            return;

        setApplicationName(Metadata.NAME);
        stage.addEventFilter(WindowEvent.WINDOW_SHOWN, event -> setApplicationName(Metadata.NAME));
    }

    private MacOSNativeUtils() {
    }
}
