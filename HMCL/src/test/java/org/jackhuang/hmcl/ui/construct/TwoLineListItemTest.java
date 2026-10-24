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
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies tag sizing in parents that allocate only the item's preferred width.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class TwoLineListItemTest {

    /// Verifies tags remain visible as they are added, resized, removed, and added again.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testPreferredWidthIncludesTags(boolean wrapText) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("Example Mod", "a.jar");
            item.getTitleLabel().setWrapText(wrapText);
            HBox parent = createParent(item);
            layout(parent, 800);
            double originalWidth = item.getWidth();

            item.addTag("Fabric");
            layout(parent, 800);
            assertTagsVisible(item);
            assertTrue(item.getWidth() > originalWidth);

            item.addTag("NeoForge");
            item.addTag("Quilt");
            layout(parent, 800);
            assertTagsVisible(item);
            double taggedWidth = item.getWidth();

            item.getTags().get(0).setText("A longer loader tag");
            layout(parent, 800);
            assertTagsVisible(item);
            assertTrue(item.getWidth() > taggedWidth);

            item.getTags().clear();
            layout(parent, 800);
            assertEquals(originalWidth, item.getWidth(), 0.001);

            item.addTag("Fabric");
            item.setSubtitle("A subtitle that is wider than the title and its loader tag");
            layout(parent, 800);
            assertTagsVisible(item);
            assertTrue(item.getSubtitleLabel().getWidth() >= item.getSubtitleLabel().prefWidth(-1));
        });
    }

    /// Verifies constrained rows give the title priority while allowing truncation or wrapping.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTitleRetainsPriority(boolean wrapText) {
        onFxThread(() -> {
            TwoLineListItem item = new TwoLineListItem("A very long mod title ".repeat(20), "a.jar");
            item.getTitleLabel().setWrapText(wrapText);
            item.addTag("Fabric");
            item.addTag("NeoForge");
            HBox parent = createParent(item);
            layout(parent, 300);

            assertEquals(252, item.getWidth(), 0.001);
            assertEquals(item.getWidth(), item.getTitleLabel().getWidth(), 0.001);
            assertTrue(item.getBoundsInParent().getMaxX() <= parent.getWidth());
            if (wrapText) {
                assertTrue(item.getTitleLabel().getHeight() > item.getTitleLabel().prefHeight(-1));
            }

            item.setTitle("Mod");
            layout(parent, 300);
            assertTagsVisible(item);
        });
    }

    /// Creates a scene with an icon and an item without a horizontal growth constraint.
    private static HBox createParent(TwoLineListItem item) {
        Region icon = new Region();
        icon.setMinSize(40, 40);
        icon.setPrefSize(40, 40);
        icon.setMaxSize(40, 40);
        HBox parent = new HBox(8, icon, item);
        new Scene(parent);
        return parent;
    }

    /// Applies CSS and lays out the parent at the given width and its preferred height.
    private static void layout(HBox parent, double width) {
        parent.applyCss();
        parent.resize(width, parent.prefHeight(width));
        parent.layout();
    }

    /// Verifies every tag fits within the region that clips its contents.
    private static void assertTagsVisible(TwoLineListItem item) {
        for (Label tag : item.getTags()) {
            Region container = (Region) tag.getParent();
            assertTrue(tag.getBoundsInParent().getMaxX() <= container.getWidth(), "Tag is clipped");
            assertTrue(tag.getWidth() >= tag.prefWidth(-1), "Tag text is truncated");
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
