// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.main;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.glavo.m3fx.controls.M3MenuItem;
import org.glavo.m3fx.controls.M3SplitButton;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies the home split button's launch and menu actions.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class LaunchButtonTest {
    /// Opening, dismissing, and choosing from the menu must never invoke the launch callback.
    @Test
    void onlyPrimaryActionLaunches() throws Exception {
        CompletableFuture.runAsync(() -> {
            AtomicInteger launches = new AtomicInteger();
            M3SplitButton button = MainPage.createLaunchButton(new SimpleObjectProperty<>(), launches::incrementAndGet);
            button.setAnimationsEnabled(false);
            M3MenuItem item = new M3MenuItem("Instance");
            button.getItems().add(item);
            Stage stage = new Stage();
            try {
                stage.setScene(new Scene(new StackPane(button), 320, 160));
                stage.show();
                ButtonBase primary = (ButtonBase) button.queryAccessibleAttribute(AccessibleAttribute.ITEM_AT_INDEX, 0);
                ButtonBase menu = (ButtonBase) button.queryAccessibleAttribute(AccessibleAttribute.ITEM_AT_INDEX, 1);
                menu.fire();
                assertTrue(button.isShowing());
                menu.fire();
                assertFalse(button.isShowing());
                menu.fire();
                item.fire();
                assertFalse(button.isShowing());
                assertEquals(0, launches.get());

                primary.fire();
                assertEquals(1, launches.get());
                button.fire();
                assertEquals(1, launches.get());
            } finally {
                stage.close();
            }
        }, Platform::runLater).get(10, TimeUnit.SECONDS);
    }
}
