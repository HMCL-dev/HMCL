// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.export;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.glavo.m3fx.controls.M3CheckBox;
import org.glavo.m3fx.controls.M3TreeView;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies hierarchical export selection in a virtualized Material tree.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class ModpackFileSelectionPageTest {

    /// Reusing a row for another file with the same name replaces its check state, explanation, and bindings.
    @Test
    void fileTreeCheckBoxFollowsReusedCell() throws Exception {
        var task = new FutureTask<@Nullable Void>(() -> {
            var root = new ModpackFileSelectionPage.ModpackFileTreeItem("Files", "minecraft");
            var first = new ModpackFileSelectionPage.ModpackFileTreeItem("mods", "minecraft/mods");
            var second = new ModpackFileSelectionPage.ModpackFileTreeItem("mods", "minecraft/custom/mods");
            root.getChildren().addAll(List.of(first, second));
            first.setSelected(true);

            var tree = new M3TreeView<String>(root);
            tree.setCellFactory(view -> new ModpackFileSelectionPage.ModpackFileTreeCell());
            tree.setSelectionModel(new NoneMultipleSelectionModel<>());
            var scene = new Scene(tree, 400, 220);
            M3ThemeManager.install(scene, M3Theme.defaultTheme());
            tree.applyCss();
            tree.layout();
            var cell = tree.lookupAll(".m3-tree-cell").stream()
                    .filter(ModpackFileSelectionPage.ModpackFileTreeCell.class::isInstance)
                    .map(ModpackFileSelectionPage.ModpackFileTreeCell.class::cast)
                    .filter(row -> row.getTreeItem() == root)
                    .findFirst().orElseThrow();
            var graphic = assertInstanceOf(HBox.class, cell.getGraphic());
            var box = assertInstanceOf(M3CheckBox.class, graphic.getChildren().getFirst());
            var comment = assertInstanceOf(Label.class, graphic.getChildren().getLast());
            assertTrue(box.isIndeterminate());

            cell.updateIndex(1);
            assertEquals("mods", cell.getAccessibleText());
            assertTrue(box.isSelected());
            assertEquals(i18n("modpack.files.mods"), comment.getText());
            cell.updateIndex(2);
            assertFalse(comment.isManaged());
            assertFalse(box.isIndeterminate());
            assertFalse(box.isSelected());
            assertEquals(0.0, Objects.requireNonNull(box.lookup(".m3-checkbox-mark")).getOpacity());
            box.fire();
            assertTrue(second.isSelected());
            assertTrue(root.isSelected());

            cell.updateIndex(-1);
            assertNull(cell.getGraphic());
            assertTrue(second.isSelected());
            second.setSelected(false);
            second.setSelected(true);
            assertFalse(box.isSelected());
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
