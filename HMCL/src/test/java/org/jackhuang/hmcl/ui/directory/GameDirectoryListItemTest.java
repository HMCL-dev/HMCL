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
package org.jackhuang.hmcl.ui.directory;

import javafx.application.Platform;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameDirectoryID;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.setting.GameDirectories;
import org.jackhuang.hmcl.setting.GameDirectoryTestEnvironment;
import org.jackhuang.hmcl.util.PortablePath;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies game directory rows track the selected game directory by ID.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class GameDirectoryListItemTest {

    /// Verifies a row stays selected when the merged entry is replaced by another instance with the same ID.
    @Test
    public void selectionFollowsGameDirectoryIdInsteadOfInstanceIdentity() throws ReflectiveOperationException {
        GameDirectoryID id = GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174000");
        GameDirectory selectedGameDirectory = new GameDirectory(
                id, LocalizedText.plain("Dev"), PortablePath.of("local/Dev"));
        GameDirectory otherGameDirectory = new GameDirectory(
                GameDirectoryID.parse("game-directory:123e4567-e89b-12d3-a456-426614174001"),
                LocalizedText.plain("Other"),
                PortablePath.of("local/Other"));
        GameDirectories userDirectories = GameDirectoryTestEnvironment.newUserGameDirectories();
        GameDirectories localDirectories = GameDirectoryTestEnvironment.newLocalGameDirectories();
        localDirectories.getGameDirectories().add(selectedGameDirectory);
        localDirectories.getGameDirectories().add(otherGameDirectory);

        try (GameDirectoryTestEnvironment ignored = new GameDirectoryTestEnvironment(localDirectories, userDirectories)) {
            GameDirectoryManager.init();
            GameDirectoryManager.setSelectedGameDirectory(selectedGameDirectory);

            onFxThread(() -> {
                GameDirectoryListItem selectedItem = new GameDirectoryListItem(selectedGameDirectory);
                GameDirectoryListItem otherItem = new GameDirectoryListItem(otherGameDirectory);

                assertTrue(selectedItem.isSelected());
                assertFalse(otherItem.isSelected());

                // The selected entry is replaced by a new instance carrying the same ID.
                GameDirectory replacement = new GameDirectory(
                        id, LocalizedText.plain("Dev"), PortablePath.of("local/Dev"));
                GameDirectoryManager.addLocalGameDirectory(replacement);
                assertSame(replacement, GameDirectoryManager.getSelectedGameDirectory());

                assertTrue(selectedItem.isSelected());
                assertFalse(otherItem.isSelected());
            });
        }
    }

    /// Runs assertions on the JavaFX thread and waits up to 30 seconds for completion.
    private static void onFxThread(Runnable action) {
        FutureTask<Boolean> task = new FutureTask<>(action, true);
        Platform.runLater(task);
        try {
            task.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError(e);
        }
    }
}
