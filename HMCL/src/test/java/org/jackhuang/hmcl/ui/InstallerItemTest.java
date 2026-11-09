// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.Objects;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.junit.jupiter.api.Assertions.*;

/// Verifies installer row layout, state transitions, and independent actions.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class InstallerItemTest {
    /// Background activation tracks availability while child actions execute exactly once.
    @Test
    void actionsFollowInstallationState() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.FORGE);
            AtomicInteger installs = new AtomicInteger();
            AtomicInteger removals = new AtomicInteger();
            item.setOnInstall(installs::incrementAndGet);
            item.setOnRemove(removals::incrementAndGet);
            VBox root = scene(item);
            RipplerContainer row = (RipplerContainer) Objects.requireNonNull(item.lookup(".installer-item-wrapper"));
            Label status = (Label) Objects.requireNonNull(item.lookup(".installer-item-status"));
            HBox buttons = buttons(item);
            JFXButton remove = (JFXButton) buttons.getChildren().get(0);
            JFXButton install = (JFXButton) buttons.getChildren().get(1);
            assertTrue(install.isVisible());
            assertFalse(remove.isVisible());
            click(row);
            assertEquals(1, installs.get());

            item.resolvedStateProperty().set(new InstallerItem.InstalledState("1.0", false, false));
            assertTrue(remove.isVisible());
            assertTrue(install.isVisible());
            remove.fire();
            assertEquals(1, removals.get());
            assertEquals(1, installs.get());
            install.fire();
            assertEquals(2, installs.get());
            assertTrue(status.getText().contains("1.0"));

            item.resolvedStateProperty().set(new InstallerItem.InstalledState("2.0", true, false));
            assertFalse(remove.isVisible());
            assertFalse(install.isVisible());
            click(row);
            assertEquals(2, installs.get());

            item.resolvedStateProperty().set(new InstallerItem.IncompatibleState("fabric", "1.0"));
            root.applyCss();
            assertTrue(status.getPseudoClassStates().contains(PseudoClass.getPseudoClass("incompatible")));
            assertEquals(0.6, status.getOpacity(), 0.001);

            item.resolvedStateProperty().set(InstallerItem.InstallableState.INSTANCE);
            assertTrue(install.isVisible());
            assertFalse(status.getPseudoClassStates().contains(PseudoClass.getPseudoClass("incompatible")));
            item.setOnInstall(null);
            assertFalse(install.isVisible());
            click(row);
            assertEquals(2, installs.get());
        });
    }

    /// The game row never offers removal, even for a locally installed version.
    @Test
    void gameCannotBeRemoved() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.GAME);
            item.setOnInstall(() -> {});
            item.resolvedStateProperty().set(new InstallerItem.InstalledState("1.0", false, false));
            scene(item);
            assertFalse(buttons(item).getChildren().get(0).isVisible());
            assertTrue(buttons(item).getChildren().get(1).isVisible());
        });
    }

    /// Rows fill the list width and retain their localized state text and mouse activation.
    @Test
    void rowsFillTheListWidth() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.FORGE);
            AtomicInteger installs = new AtomicInteger();
            item.setOnInstall(installs::incrementAndGet);
            VBox root = scene(item);
            assertEquals(root.getWidth(), item.getWidth(), 0.001);
            assertTrue(item.getHeight() < 80);
            assertNull(item.lookup(".jfx-card"));
            RipplerContainer row = (RipplerContainer) Objects.requireNonNull(item.lookup(".installer-item-wrapper"));
            Label status = (Label) Objects.requireNonNull(item.lookup(".installer-item-status"));
            assertEquals(i18n("install.installer.not_installed"), status.getText());
            click(row);
            assertEquals(1, installs.get());
            item.resolvedStateProperty().set(new InstallerItem.InstalledState("1.0", true, false));
            click(row);
            assertEquals(1, installs.get());
            assertEquals(i18n("install.installer.external_version", "1.0"), status.getText());
        });
    }

    /// Returns the row's independent remove and install buttons in their layout order.
    private static HBox buttons(InstallerItem item) {
        HBox row = (HBox) Objects.requireNonNull(item.lookup(".installer-item"));
        return (HBox) row.getChildren().get(row.getChildren().size() - 1);
    }

    /// Lays out an installer in a full-width vertical list using the application's theme.
    private static VBox scene(InstallerItem item) {
        VBox root = new VBox(item);
        Scene scene = new Scene(root, 400, 800);
        scene.getStylesheets().addAll(
                Objects.requireNonNull(InstallerItemTest.class.getResource("/assets/css/blue.css")).toExternalForm(),
                Objects.requireNonNull(InstallerItemTest.class.getResource("/assets/css/root.css")).toExternalForm());
        root.applyCss();
        root.layout();
        return root;
    }

    /// Sends one primary click through the wrapper's background feedback layer.
    private static void click(RipplerContainer container) {
        Node node = container.getChildrenUnmodifiable().get(0);
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 20, 20, 20, 20, MouseButton.PRIMARY, 1,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(node, 20, 20)));
    }

    /// Runs assertions on the JavaFX thread and propagates failures to the test runner.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
