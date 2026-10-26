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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXListView;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/// Tests filename formatting and live updates in the instance mod list.
///
/// @author dev@howxu.cn
@NotNullByDefault
final class ModListPageTest {

    /// Temporary directory containing the mod files used by each test.
    @TempDir
    Path tempDir;

    /// Disabling uses the renamed file rather than the cached base filename.
    @Test
    void modSubtitleUsesUpdatedFilePathAfterDisabling() throws IOException {
        LocalModFile modFile = createModFile("example.jar", "testmod", ModLoaderType.FABRIC);

        modFile.setActive(false);

        assertEquals("testmod | example.jar.disabled", ModListPage.createModSubtitle(modFile));
    }

    /// Enabling removes the disabled suffix from the displayed filename.
    @Test
    void modSubtitleUsesUpdatedFilePathAfterEnabling() throws IOException {
        LocalModFile modFile = createModFile("example.jar.disabled", "testmod", ModLoaderType.FABRIC);

        modFile.setActive(true);

        assertEquals("testmod | example.jar", ModListPage.createModSubtitle(modFile));
    }

    /// Initial rendering retains the enabled filename.
    @Test
    void modSubtitleShowsCurrentEnabledFilePath() throws IOException {
        LocalModFile modFile = createModFile("example.jar", "testmod", ModLoaderType.FABRIC);

        assertEquals("testmod | example.jar", ModListPage.createModSubtitle(modFile));
    }

    /// Unknown loaders omit the mod ID from the subtitle.
    @Test
    void modSubtitleOmitsUnknownLoaderId() throws IOException {
        LocalModFile modFile = createModFile("unknown.jar", "unknown", ModLoaderType.UNKNOWN);

        assertEquals("unknown.jar", ModListPage.createModSubtitle(modFile));
    }

    /// Known loaders with blank IDs do not introduce an empty subtitle component.
    @Test
    void modSubtitleOmitsBlankId() throws IOException {
        LocalModFile modFile = createModFile("example.jar", "", ModLoaderType.FABRIC);

        assertEquals("example.jar", ModListPage.createModSubtitle(modFile));
    }

    /// Upstream now throws on rename failure without changing the file or active state.
    @Test
    void modSubtitleDoesNotChangeWhenFileRenameFails() throws IOException {
        LocalModFile modFile = createModFile("example.jar", "testmod", ModLoaderType.FABRIC);
        Path disabledTarget = tempDir.resolve("example.jar.disabled");
        Files.createDirectory(disabledTarget);
        Files.writeString(disabledTarget.resolve("child"), "occupied");

        assertThrows(IOException.class, () -> modFile.setActive(false));

        assertTrue(modFile.isActive());
        assertEquals("testmod | example.jar", ModListPage.createModSubtitle(modFile));
    }

    /// The actual visible row updates after both disabling and enabling its model.
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    void cellSubtitleRefreshesAfterToggling() throws Exception {
        LocalModFile modFile = createModFile("example.jar", "testmod", ModLoaderType.FABRIC);
        onFxThread(() -> {
            MDListCell<ModListPage.ModInfoObject> cell = createCell();
            showMod(cell, modFile);
            TwoLineListItem content = getContent(cell);
            assertEquals("testmod | example.jar", content.getSubtitle());

            modFile.setActive(false);
            assertEquals("testmod | example.jar.disabled", content.getSubtitle());

            modFile.setActive(true);
            assertEquals("testmod | example.jar", content.getSubtitle());
        });
    }

    /// A reused row observes its new mod instead of continuing to display the previous one.
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    void reusedCellRefreshesOnlyCurrentMod() throws Exception {
        LocalModFile first = createModFile("first.jar", "first", ModLoaderType.FABRIC);
        LocalModFile second = createModFile("second.jar", "second", ModLoaderType.FABRIC);
        onFxThread(() -> {
            MDListCell<ModListPage.ModInfoObject> cell = createCell();
            showMod(cell, first);
            showMod(cell, second);
            first.setActive(false);
            assertEquals("second | second.jar", getContent(cell).getSubtitle());

            second.setActive(false);
            assertEquals("second | second.jar.disabled", getContent(cell).getSubtitle());

            showMod(cell, null);
            second.setActive(true);
            assertNull(cell.getGraphic());
            assertEquals("second | second.jar.disabled", getContent(cell).getSubtitle());
        });
    }

    /// A failed file rename leaves the visible subtitle and model state unchanged.
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    void cellSubtitleDoesNotChangeWhenRenameFails() throws Exception {
        LocalModFile modFile = createModFile("example.jar", "testmod", ModLoaderType.FABRIC);
        Path target = Files.createDirectory(tempDir.resolve("example.jar.disabled"));
        Files.writeString(target.resolve("child"), "occupied");
        onFxThread(() -> {
            MDListCell<ModListPage.ModInfoObject> cell = createCell();
            showMod(cell, modFile);

            assertThrows(IOException.class, () -> modFile.setActive(false));

            assertTrue(modFile.isActive());
            assertEquals("testmod | example.jar", getContent(cell).getSubtitle());
        });
    }

    /// Creates a real mod archive; these path-only operations do not access a game instance.
    private LocalModFile createModFile(String fileName, String modId, ModLoaderType modLoaderType) throws IOException {
        Path file = tempDir.resolve(fileName);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.finish();
        }

        ModManager modManager = new ModManager(null);
        return new LocalModFile(
                modManager,
                modManager.getLocalMod(modId, modLoaderType),
                file,
                "Test Mod",
                new LocalAddonFile.Description("Test mod")
        );
    }

    /// Constructs the private production cell without exposing test-only production APIs.
    @SuppressWarnings("unchecked")
    private static MDListCell<ModListPage.ModInfoObject> createCell() throws ReflectiveOperationException {
        ModListPage page = new ModListPage(new SimpleObjectProperty<>());
        page.supportedLoaders.add(ModLoaderType.FABRIC);
        Class<?> cellClass = Class.forName(ModListPage.class.getName() + "$ModInfoListCell");
        Constructor<?> constructor = cellClass.getDeclaredConstructor(JFXListView.class, ModListPage.class);
        constructor.setAccessible(true);
        return (MDListCell<ModListPage.ModInfoObject>) constructor.newInstance(new JFXListView<>(), page);
    }

    /// Uses the normal cell update path to populate, reuse, or empty the row.
    private static void showMod(MDListCell<ModListPage.ModInfoObject> cell, @Nullable LocalModFile modFile)
            throws ReflectiveOperationException {
        Method updateItem = MDListCell.class.getDeclaredMethod("updateItem", Object.class, boolean.class);
        updateItem.setAccessible(true);
        updateItem.invoke(cell, modFile == null ? null : new ModListPage.ModInfoObject(modFile), modFile == null);
    }

    /// Reads the actual subtitle-bearing control from the private production cell.
    private static TwoLineListItem getContent(MDListCell<ModListPage.ModInfoObject> cell)
            throws ReflectiveOperationException {
        Field content = cell.getClass().getDeclaredField("content");
        content.setAccessible(true);
        return (TwoLineListItem) content.get(cell);
    }

    /// Runs UI assertions with a bounded wait and temporarily supplies in-memory launcher settings.
    private static void onFxThread(FxAction action) throws Exception {
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            Field settings = SettingsManager.class.getDeclaredField("launcherSettings");
            settings.setAccessible(true);
            @Nullable Object previousSettings = settings.get(null);
            try {
                settings.set(null, new LauncherSettings());
                action.run();
                return true;
            } finally {
                settings.set(null, previousSettings);
            }
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }

    /// JavaFX test action that may fail with checked I/O or reflection exceptions.
    @NotNullByDefault
    @FunctionalInterface
    private interface FxAction {
        /// Executes the test on the JavaFX application thread.
        void run() throws Exception;
    }
}
