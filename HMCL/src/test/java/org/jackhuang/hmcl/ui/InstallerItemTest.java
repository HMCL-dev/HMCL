// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCard;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.FlowPane;
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

/// Verifies installer card state transitions, action boundaries, and compact row compatibility.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class InstallerItemTest {
    /// A one-row action area must not leave space reserved for a second row below it.
    @Test
    void cardDoesNotReserveExtraActionRow() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.FORGE, InstallerItem.Style.CARD);
            item.setOnInstall(() -> {});
            item.resolvedStateProperty().set(new InstallerItem.InstalledState("66.1.5", false, false));
            scene(item);
            JFXCard card = (JFXCard) Objects.requireNonNull(item.lookup(".jfx-card"));
            Node actions = Objects.requireNonNull(card.lookup(".card-actions"));
            assertEquals(card.localToScene(card.getLayoutBounds()).getMaxY() - card.getInsets().getBottom(),
                    actions.localToScene(actions.getLayoutBounds()).getMaxY(), 0.001);
        });
    }

    /// Background activation tracks availability while child actions execute exactly once.
    @Test
    void cardActionsFollowInstallationState() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.FORGE, InstallerItem.Style.CARD);
            AtomicInteger installs = new AtomicInteger();
            AtomicInteger removals = new AtomicInteger();
            item.setOnInstall(installs::incrementAndGet);
            item.setOnRemove(removals::incrementAndGet);
            FlowPane root = scene(item);
            JFXCard card = (JFXCard) Objects.requireNonNull(item.lookup(".jfx-card"));
            JFXButton remove = (JFXButton) card.getActions().get(0);
            JFXButton install = (JFXButton) card.getActions().get(1);
            assertEquals(JFXCard.CardType.OUTLINED, card.getCardType());
            assertTrue(card.isInteractive());
            assertFalse(remove.isVisible());
            click(card);
            assertEquals(1, installs.get());

            item.resolvedStateProperty().set(new InstallerItem.InstalledState("1.0", false, false));
            assertTrue(remove.isVisible());
            assertTrue(install.isVisible());
            remove.fire();
            assertEquals(1, removals.get());
            assertEquals(1, installs.get());
            install.fire();
            assertEquals(2, installs.get());
            assertTrue(card.getSupportingText().contains("1.0"));
            assertTrue(card.getAccessibleText().contains("1.0"));

            item.resolvedStateProperty().set(new InstallerItem.InstalledState("2.0", true, false));
            assertFalse(card.isInteractive());
            assertFalse(remove.isVisible());
            assertFalse(install.isVisible());
            click(card);
            card.fire();
            assertEquals(2, installs.get());

            item.resolvedStateProperty().set(new InstallerItem.IncompatibleState("fabric", "1.0"));
            root.applyCss();
            assertFalse(card.isInteractive());
            assertTrue(card.getPseudoClassStates().contains(PseudoClass.getPseudoClass("incompatible")));
            assertEquals(0.6, card.lookup(".card-supporting-text").getOpacity(), 0.001);

            item.resolvedStateProperty().set(InstallerItem.InstallableState.INSTANCE);
            assertTrue(card.isInteractive());
            assertFalse(card.getPseudoClassStates().contains(PseudoClass.getPseudoClass("incompatible")));
            item.setOnInstall(null);
            assertFalse(card.isInteractive());
            card.fire();
            assertEquals(2, installs.get());
            item.setOnInstall(installs::incrementAndGet);
            item.setDisable(true);
            card.fire();
            assertEquals(2, installs.get());
        });
    }

    /// Long version descriptions expand the card without overlapping its action area.
    @Test
    void cardFitsWrappedStatusAndProtectsGameRemoval() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.GAME, InstallerItem.Style.CARD);
            item.setOnInstall(() -> {});
            item.resolvedStateProperty().set(new InstallerItem.InstalledState("preview version ".repeat(12), false, true));
            scene(item);
            JFXCard card = (JFXCard) Objects.requireNonNull(item.lookup(".jfx-card"));
            Label status = (Label) Objects.requireNonNull(card.lookup(".card-supporting-text"));
            Node actions = Objects.requireNonNull(card.lookup(".card-actions"));
            assertEquals(180, card.getWidth(), 0.001);
            assertTrue(status.getHeight() > status.prefHeight(-1));
            assertTrue(card.getHeight() > card.getWidth() * 0.7);
            assertTrue(status.localToScene(status.getBoundsInLocal()).getMaxY()
                    <= actions.localToScene(actions.getBoundsInLocal()).getMinY() + 0.001);
            assertTrue(actions.localToScene(actions.getBoundsInLocal()).getMaxY()
                    <= card.localToScene(card.getBoundsInLocal()).getMaxY() + 0.001);
            assertFalse(card.getActions().get(0).isVisible());
        });
    }

    /// List mode keeps its row surface, localized state text, and mouse activation.
    @Test
    void listModeRetainsItsPresentation() throws Exception {
        onFxThread(() -> {
            InstallerItem item = new InstallerItem(GameComponentType.FORGE, InstallerItem.Style.LIST_ITEM);
            AtomicInteger installs = new AtomicInteger();
            item.setOnInstall(installs::incrementAndGet);
            scene(item);
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

    /// Lays out an installer at its preferred size using the application's theme.
    private static FlowPane scene(InstallerItem item) {
        FlowPane root = new FlowPane(item);
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
