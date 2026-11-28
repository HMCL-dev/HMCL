// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.text.TextFlow;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

/// Checks message wrapping in an unshown dialog.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class MessageDialogPaneTest {

    /// Checks compact messages and scrolling as the available height changes.
    @ParameterizedTest
    @ValueSource(ints = {1, 300})
    public void testViewportTracksAllocation(int digits) throws Exception {
        FutureTask<Boolean> task = new FutureTask<>(() -> {
            MessageDialogPane dialog = new MessageDialogPane("1".repeat(digits), "Delete?", MessageDialogPane.MessageType.QUESTION);
            Button action = new Button("Delete");
            dialog.addButton(action);
            new Scene(dialog);
            dialog.applyCss();
            ScrollPane scroll = (ScrollPane) requireNonNull(dialog.lookup(".scroll-pane"));
            TextFlow flow = (TextFlow) scroll.getContent();
            assertTrue(flow.prefHeight(400) < 350);
            for (int height : new int[]{120, 450, 120}) {
                dialog.resize(300, height);
                dialog.layout();
                boolean overflow = digits > 1 && height == 120;
                Node bar = requireNonNull(scroll.lookup(".scroll-bar:vertical"));
                assertEquals(overflow, bar.isVisible());
                assertTrue(action.localToScene(action.getLayoutBounds()).getMaxY() <= height);
                if (digits == 1)
                    assertTrue(scroll.getHeight() <= Math.ceil(flow.prefHeight(400)) + scroll.getInsets().getTop() + scroll.getInsets().getBottom());
                if (overflow) {
                    scroll.setVvalue(scroll.getVmax());
                    dialog.layout();
                    Node viewport = requireNonNull(scroll.lookup(".viewport"));
                    assertEquals(viewport.localToScene(viewport.getLayoutBounds()).getMaxY(),
                            flow.localToScene(flow.getLayoutBounds()).getMaxY(), 1);
                }
            }
        }, true);
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
