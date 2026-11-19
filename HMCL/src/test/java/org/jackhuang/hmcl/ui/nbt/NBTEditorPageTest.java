// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.nbt;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import org.glavo.m3fx.skins.M3TreeViewSkin;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.glavo.nbt.tag.CompoundTag;
import org.glavo.nbt.tag.ListTag;
import org.glavo.nbt.tag.StringTag;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies that Material tree rows retain NBT-specific content across reuse.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class NBTEditorPageTest {

    /// Checks file names, child counts, list indices and type icons in an expanded tree.
    @Test
    void rendersNbtEntriesWithMaterialSkin() throws Exception {
        var task = new FutureTask<@Nullable Void>(() -> {
            var tags = new ListTag<StringTag>();
            var tag = new StringTag("Example");
            tags.addTag(tag);
            var document = new CompoundTag();
            document.addTag("names", tags);
            var tree = NBTEditorPage.createTreeView(new NBTTreeItem(document, "level.dat"));
            var scene = new Scene(tree, 400, 240);
            M3ThemeManager.install(scene, M3Theme.defaultTheme());
            tree.applyCss();
            tree.layout();

            assertInstanceOf(M3TreeViewSkin.class, tree.getSkin());
            assertEquals(3, tree.getExpandedItemCount());
            var cell = tree.lookupAll(".m3-tree-cell").stream()
                    .filter(NBTTreeCell.class::isInstance)
                    .map(NBTTreeCell.class::cast)
                    .filter(row -> row.getIndex() == 0)
                    .findFirst().orElseThrow();
            assertEquals("level.dat: " + i18n("nbt.entries", 1), cell.getText());
            var icon = assertInstanceOf(ImageView.class, cell.getGraphic());
            assertTrue(icon.getImage().getUrl().endsWith("TAG_Compound.png"));
            cell.updateIndex(1);
            assertEquals("names: " + i18n("nbt.entries", 1), cell.getText());
            cell.updateIndex(2);
            assertEquals("0: " + tag.getAsString(), cell.getText());
            assertTrue(icon.getImage().getUrl().endsWith("TAG_String.png"));
            cell.updateIndex(-1);
            assertNull(cell.getGraphic());
            return null;
        });
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
