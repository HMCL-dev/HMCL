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
package org.jackhuang.hmcl.ui;

import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.IPropertyStore;
import org.jackhuang.hmcl.util.platform.windows.Shell32;
import org.jackhuang.hmcl.util.platform.windows.WinTypes;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public final class WindowsNativeUtils {

    public static OptionalLong getWindowHandle(Stage stage) {
        try {
            Class<?> windowStageClass = Class.forName("com.sun.javafx.tk.quantum.WindowStage");
            Class<?> glassWindowClass = Class.forName("com.sun.glass.ui.Window");
            Class<?> tkStageClass = Class.forName("com.sun.javafx.tk.TKStage");

            Object tkStage = MethodHandles.privateLookupIn(Window.class, MethodHandles.lookup())
                    .findVirtual(Window.class, "getPeer", MethodType.methodType(tkStageClass))
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

    /// Installs permanent window listeners that keep AppUserModel relaunch properties in sync
    /// with the stage's native HWND lifecycle.
    ///
    /// JavaFX may destroy and recreate the HWND across `hide()` / `show()` (for example with
    /// `HIDE_AND_REOPEN`). Properties are therefore applied on every [`WindowEvent#WINDOW_SHOWN`]
    /// and removed with `VT_EMPTY` on every [`WindowEvent#WINDOW_HIDING`], as required by
    /// [`SHGetPropertyStoreForWindow`](https://learn.microsoft.com/windows/win32/api/shellapi/nf-shellapi-shgetpropertystoreforwindow).
    ///
    /// Handlers must run on the JavaFX application thread: `IPropertyStore` is a COM interface,
    /// and the FX thread is already initialized for COM on Windows.
    ///
    /// @param stage the primary launcher stage
    public static void installWindowsAppUserModelRelaunchProperties(Stage stage) {
        if (OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS)
            return;
        if (!NativeUtils.USE_JNA || Shell32.INSTANCE == null)
            return;

        @Nullable Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null || !Files.isRegularFile(thisJar) || !"exe".equalsIgnoreCase(FileUtils.getExtension(thisJar)))
            return;

        String exePath = FileUtils.getAbsolutePath(thisJar);
        String iconResource = exePath + ",0";
        String relaunchCommand = '"' + exePath + '"';

        stage.addEventFilter(WindowEvent.WINDOW_SHOWN, event -> {
            try {
                applyWindowsAppUserModelRelaunchProperties(stage, relaunchCommand, iconResource);
            } catch (Throwable e) {
                LOG.warning("Failed to set AppUserModel relaunch properties", e);
            }
        });
        stage.addEventFilter(WindowEvent.WINDOW_HIDING, event -> {
            try {
                clearWindowsAppUserModelRelaunchProperties(stage);
            } catch (Throwable e) {
                LOG.warning("Failed to clear AppUserModel relaunch properties", e);
            }
        });
    }

    /// Writes AppUserModel relaunch properties for the current native window of `stage`.
    ///
    /// Successful [`IPropertyStore#SetValue`] calls on a window property store take effect
    /// immediately; no `Commit` is required or useful.
    ///
    /// @param stage the stage whose native window receives the properties
    /// @param relaunchCommand the `PKEY_AppUserModel_RelaunchCommand` value
    /// @param iconResource the `PKEY_AppUserModel_RelaunchIconResource` value
    private static void applyWindowsAppUserModelRelaunchProperties(Stage stage, String relaunchCommand, String iconResource) {
        Shell32 shell32 = Shell32.INSTANCE;
        if (shell32 == null)
            return;

        OptionalLong handle = WindowsNativeUtils.getWindowHandle(stage);
        if (handle.isEmpty() || handle.getAsLong() == WinTypes.HANDLE.INVALID_VALUE) {
            LOG.warning("Failed to get window handle for AppUserModel relaunch properties");
            return;
        }

        try (IPropertyStore store = IPropertyStore.forWindow(shell32, handle.getAsLong())) {
            if (store == null) {
                LOG.warning("Failed to call SHGetPropertyStoreForWindow");
                return;
            }

            // Set Relaunch* first, then AppUserModel.ID last. Window property store values take
            // effect immediately on SetValue; assigning the ID first can let the shell register the
            // window before relaunch metadata is complete.
            if (!setWindowsPropertyStoreString(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchCommand, relaunchCommand)
                    || !setWindowsPropertyStoreString(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchIconResource, iconResource)
                    || !setWindowsPropertyStoreString(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchDisplayNameResource, Metadata.FULL_NAME)
                    || !setWindowsPropertyStoreString(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_ID, Metadata.WINDOWS_APP_USER_MODEL_ID)) {
                return;
            }

            LOG.info("Set AppUserModel relaunch properties for HWND " + Long.toHexString(handle.getAsLong()));
        }
    }

    /// Removes AppUserModel relaunch properties from the current native window before it is hidden.
    ///
    /// @param stage the stage whose native window is about to be destroyed or hidden
    private static void clearWindowsAppUserModelRelaunchProperties(Stage stage) {
        Shell32 shell32 = Shell32.INSTANCE;
        if (shell32 == null)
            return;

        OptionalLong handle = WindowsNativeUtils.getWindowHandle(stage);
        if (handle.isEmpty() || handle.getAsLong() == WinTypes.HANDLE.INVALID_VALUE) {
            return;
        }

        try (IPropertyStore store = IPropertyStore.forWindow(shell32, handle.getAsLong())) {
            if (store == null) {
                LOG.warning("Failed to call SHGetPropertyStoreForWindow while clearing properties");
                return;
            }

            WinTypes.PROPVARIANT empty = new WinTypes.PROPVARIANT();
            empty.setEmpty();
            // Clear in reverse of apply order so ID is removed before relaunch metadata when possible.
            clearWindowsPropertyStoreValue(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_ID, empty);
            clearWindowsPropertyStoreValue(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchDisplayNameResource, empty);
            clearWindowsPropertyStoreValue(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchIconResource, empty);
            clearWindowsPropertyStoreValue(store, WinTypes.PROPERTYKEY.PKEY_AppUserModel_RelaunchCommand, empty);
        }
    }

    /// Sets a string property on an [`IPropertyStore`].
    ///
    /// @param store the property store
    /// @param key the property key
    /// @param value the string value
    /// @return `true` if [`IPropertyStore#SetValue`] succeeds
    private static boolean setWindowsPropertyStoreString(IPropertyStore store, WinTypes.PROPERTYKEY key, String value) {
        WinTypes.PROPVARIANT propvar = new WinTypes.PROPVARIANT();
        propvar.setStringValue(value);
        int hr = store.SetValue(key, propvar);
        if (hr < 0) {
            LOG.warning("Failed to set property pid=" + key.pid + " on IPropertyStore, HRESULT=0x" + Integer.toHexString(hr));
            return false;
        }
        return true;
    }

    /// Removes a property by writing `VT_EMPTY`.
    ///
    /// @param store the property store
    /// @param key the property key to clear
    /// @param empty a `VT_EMPTY` value
    private static void clearWindowsPropertyStoreValue(IPropertyStore store, WinTypes.PROPERTYKEY key, WinTypes.PROPVARIANT empty) {
        int hr = store.SetValue(key, empty);
        if (hr < 0) {
            LOG.warning("Failed to clear property pid=" + key.pid + " on IPropertyStore, HRESULT=0x" + Integer.toHexString(hr));
        }
    }

    private WindowsNativeUtils() {
    }
}
