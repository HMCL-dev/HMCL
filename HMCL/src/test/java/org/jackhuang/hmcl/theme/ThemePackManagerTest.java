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
package org.jackhuang.hmcl.theme;

import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/// Tests for theme-pack import, export, and resolution behavior.
@NotNullByDefault
public final class ThemePackManagerTest {
    /// Theme-pack image assets keep their entry names when exported from unpacked theme directories.
    @Test
    public void exportsUnpackedThemeResourceWithEntryName() throws Exception {
        Field launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        launcherSettingsField.setAccessible(true);
        Object previousLauncherSettings = launcherSettingsField.get(null);

        String packId = "example.unpacked-export";
        Path themePacksDirectory = ThemePackManager.THEME_PACKS_DIRECTORY.toAbsolutePath().normalize();
        Path themeDirectory = themePacksDirectory.resolve(packId).normalize();
        if (!themeDirectory.startsWith(themePacksDirectory)) {
            throw new AssertionError("Theme-pack test directory escapes the theme-pack directory: " + themeDirectory);
        }

        try {
            if (Files.exists(themeDirectory)) {
                FileUtils.forceDelete(themeDirectory);
            }
            Path wallpaper = themeDirectory.resolve("assets/wallpapers/wallpaper.png");
            Files.createDirectories(wallpaper.getParent());
            byte[] body = "unpacked image bytes".getBytes(StandardCharsets.US_ASCII);
            Files.write(wallpaper, body);
            Files.writeString(themeDirectory.resolve(ThemePackExporter.MANIFEST_ENTRY), """
                    {
                      "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                      "id": "example.unpacked-export",
                      "version": "1.0.0",
                      "name": "Unpacked",
                      "theme": {
                        "background": {
                          "type": "image",
                          "path": "assets/wallpapers/wallpaper.png"
                        }
                      }
                    }
                    """);

            LauncherSettings launcherSettings = new LauncherSettings();
            launcherSettings.selectedThemeProperty().set(new ThemeReference(packId, null));
            launcherSettingsField.set(null, launcherSettings);

            ThemePackManager.ExportedThemePack exported =
                    ThemePackManager.createCurrent("example.export", "Example", "Tester");
            Theme theme = exported.manifest().themes().get(0);
            ThemeBackgroundSettings background = Objects.requireNonNull(theme.appearance().background());
            ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());

            assertEquals("assets/wallpapers/wallpaper.png", image.path());
            assertEquals(1, exported.assets().size());
            assertEquals("assets/wallpapers/wallpaper.png", exported.assets().get(0).entryName());
            try (InputStream input = exported.assets().get(0).source().openStream()) {
                assertArrayEquals(body, input.readAllBytes());
            }
        } finally {
            launcherSettingsField.set(null, previousLauncherSettings);
            if (Files.exists(themeDirectory)) {
                FileUtils.forceDelete(themeDirectory);
            }
        }
    }

    /// Network launcher backgrounds are downloaded and exported as local theme-pack image assets.
    @Test
    public void exportsNetworkBackgroundAsImageAsset() throws Exception {
        Field launcherSettingsField = SettingsManager.class.getDeclaredField("launcherSettings");
        launcherSettingsField.setAccessible(true);
        Object previousLauncherSettings = launcherSettingsField.get(null);

        byte[] body = "fake image bytes".getBytes(StandardCharsets.US_ASCII);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0)) {
            Future<?> serverTask = executor.submit(() -> {
                try (Socket socket = server.accept();
                     InputStream input = socket.getInputStream();
                     OutputStream output = socket.getOutputStream()) {
                    int matched = 0;
                    byte[] endOfHeaders = "\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
                    int nextByte;
                    while (matched < endOfHeaders.length && (nextByte = input.read()) >= 0) {
                        matched = nextByte == endOfHeaders[matched] ? matched + 1 : 0;
                    }
                    byte[] header = ("HTTP/1.1 200 OK\r\n"
                            + "Content-Type: image/png\r\n"
                            + "Content-Length: " + body.length + "\r\n"
                            + "Connection: close\r\n"
                            + "\r\n").getBytes(StandardCharsets.US_ASCII);
                    output.write(header);
                    output.write(body);
                }
                return null;
            });

            LauncherSettings launcherSettings = new LauncherSettings();
            launcherSettings.backgroundTypeProperty().set(BackgroundType.NETWORK);
            launcherSettings.networkBackgroundImageUrlProperty().set(
                    "http://127.0.0.1:" + server.getLocalPort() + "/wallpaper.png");
            launcherSettings.getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND);
            launcherSettingsField.set(null, launcherSettings);

            ThemePackManager.ExportedThemePack exported =
                    ThemePackManager.createCurrent("example.export", "Example", "Tester");
            try {
                Theme theme = exported.manifest().themes().get(0);
                ThemeBackgroundSettings background = Objects.requireNonNull(theme.appearance().background());
                ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());

                assertEquals("assets/wallpapers/wallpaper.png", image.path());
                assertEquals(1, exported.assets().size());
                try (InputStream input = exported.assets().get(0).source().openStream()) {
                    assertArrayEquals(body, input.readAllBytes());
                }
                serverTask.get(5, TimeUnit.SECONDS);
            } finally {
                for (Path temporaryFile : exported.temporaryFiles()) {
                    Files.deleteIfExists(temporaryFile);
                }
            }
        } finally {
            launcherSettingsField.set(null, previousLauncherSettings);
            executor.shutdownNow();
        }
    }
}
