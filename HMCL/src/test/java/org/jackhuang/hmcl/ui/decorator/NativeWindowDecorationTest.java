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
package org.jackhuang.hmcl.ui.decorator;

import com.jfoenix.controls.JFXDialog;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.PickResult;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/// Exercises JavaFX's native header hit testing without requiring a desktop window manager.
@NotNullByDefault
@EnabledIf("supportsHeaderBar")
final class NativeWindowDecorationTest {
    /// Requires a running toolkit and the header API, but not platform support for extended windows.
    static boolean supportsHeaderBar() {
        try {
            Class.forName("javafx.scene.layout.HeaderBar");
            return JavaFXLauncher.isStarted()
                    && Integer.parseInt(System.getProperty("javafx.version", "0").split("[.\\-+]")[0]) >= 27;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /// Verifies nested title content and label skins are draggable while interactive controls are excluded.
    @Test
    void picksNestedTitleContentAndControls() throws Exception {
        onFxThread(() -> {
            NativeWindowDecoration decoration = createDecoration();
            Rectangle icon = new Rectangle(20, 20);
            Label title = new Label("Launcher title");
            HBox customTitle = new HBox(icon, title);
            Button action = new Button("Action");
            Rectangle buttonGraphic = new Rectangle(12, 12);
            action.setGraphic(buttonGraphic);
            BorderPane content = new BorderPane(new StackPane(customTitle), null, action, null, null);
            decoration.setContent(content);
            StackPane root = new StackPane(decoration.headerBar);
            Scene scene = new Scene(root, 800, 40);
            layout(root);

            assertEquals("DRAGBAR", pick(scene, customTitle));
            assertEquals("DRAGBAR", pick(scene, icon));
            assertEquals("DRAGBAR", pick(scene, title));
            assertNotEquals("DRAGBAR", pick(scene, action));
            assertNotEquals("DRAGBAR", pick(scene, buttonGraphic));

            // Reattachment and later content changes must preserve the hit-test policy.
            decoration.setContent(null);
            decoration.setContent(content);
            Label addedTitle = new Label("Updated title");
            TextField input = new TextField("Editable title");
            StackPane excluded = new StackPane(new Label("Excluded"));
            decoration.setDraggable(excluded, false);
            customTitle.getChildren().setAll(addedTitle, input, excluded);
            layout(root);
            assertEquals("DRAGBAR", pick(scene, addedTitle));
            assertNotEquals("DRAGBAR", pick(scene, input));
            assertNotEquals("DRAGBAR", pick(scene, excluded));
            return null;
        });
    }

    /// Verifies dialog overlays preserve native header dragging without allowing ordinary mouse clicks through.
    @Test
    void picksHeaderThroughDialogOverlay() throws Exception {
        onFxThread(() -> {
            NativeWindowDecoration decoration = createDecoration();
            Rectangle title = new Rectangle(20, 20);
            Button headerAction = new Button("Header action");
            decoration.setContent(new BorderPane(new StackPane(title), null, headerAction, null, null));
            decoration.headerBar.setPrefHeight(40);
            StackPane root = new StackPane(new BorderPane(new StackPane(), decoration.headerBar, null, null, null));
            Scene scene = new Scene(root, 800, 400);
            Button dialogAction = new Button("Dialog action");
            JFXDialogPane pane = new JFXDialogPane();
            pane.push(dialogAction);
            JFXDialog dialog = new JFXDialog(root, pane, JFXDialog.DialogTransition.NONE, false);
            // A nested overlay node must inherit transparency for header hit testing only.
            Region shade = new Region();
            dialog.getChildren().add(0, shade);
            decoration.setDraggable(pane, false);
            dialog.show();
            root.resize(800, 400);
            root.applyCss();
            root.layout();

            assertNotEquals("DRAGBAR", pick(scene, title));
            decoration.setDragTransparent(dialog);
            assertEquals("DRAGBAR", pick(scene, title));
            assertNotEquals("DRAGBAR", pick(scene, headerAction));
            assertNotEquals("DRAGBAR", pick(scene, 20, 200));
            assertSame(shade, pickMouse(scene, title.localToScene(10, 10)).getIntersectedNode());

            // Dialog controls must still block native dragging even when they overlap the header.
            Point2D actionCenter = dialogAction.localToScene(dialogAction.getWidth() / 2, dialogAction.getHeight() / 2);
            pane.getParent().setTranslateY(20 - actionCenter.getY());
            assertNotEquals("DRAGBAR", pick(scene, dialogAction));
            dialog.close();
            assertEquals("DRAGBAR", pick(scene, title));
            return null;
        });
    }

    /// Verifies each newly attached stage opts out of the system-provided window buttons.
    @Test
    void hidesSystemButtons() throws Exception {
        onFxThread(() -> {
            NativeWindowDecoration decoration = createDecoration();
            Stage first = new Stage();
            Stage replacement = new Stage();
            decoration.configureStage(first);
            decoration.configureStage(replacement);
            Method getter = decoration.headerBar.getClass().getMethod("getSystemButtonHeight", Stage.class);
            assertEquals(0.0, getter.invoke(null, first));
            assertEquals(0.0, getter.invoke(null, replacement));
            return null;
        });
    }

    /// Resolves the real APIs even when the headless toolkit reports no native window decorations.
    private static NativeWindowDecoration createDecoration() throws ReflectiveOperationException {
        Constructor<NativeWindowDecoration> constructor = NativeWindowDecoration.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    /// Applies CSS so hit testing includes control skins, then lays out the header at a fixed size.
    private static void layout(StackPane root) {
        root.resize(800, 40);
        root.applyCss();
        root.layout();
    }

    /// Uses the same header-area picker that the platform window toolkit calls.
    ///
    /// @return the native hit-test category at the node's center, or `null` for an ordinary client-area hit
    private static @Nullable String pick(Scene scene, Node node) throws ReflectiveOperationException {
        Point2D point = node.localToScene(node.getLayoutBounds().getWidth() / 2, node.getLayoutBounds().getHeight() / 2);
        return pick(scene, point.getX(), point.getY());
    }

    /// Returns the native header hit-test category at scene coordinates, or `null` for an ordinary client-area hit.
    private static @Nullable String pick(Scene scene, double x, double y) throws ReflectiveOperationException {
        Class<?> listenerClass = Class.forName("javafx.scene.Scene$ScenePeerListener");
        Constructor<?> constructor = listenerClass.getDeclaredConstructor(Scene.class);
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(scene);
        Method pick = listenerClass.getDeclaredMethod("pickHeaderArea", double.class, double.class);
        pick.setAccessible(true);
        @Nullable Object result = pick.invoke(listener, x, y);
        return result == null ? null : result.toString();
    }

    /// Performs ordinary mouse picking to verify the modal overlay still intercepts input.
    private static PickResult pickMouse(Scene scene, Point2D point) throws ReflectiveOperationException {
        Method pick = Scene.class.getDeclaredMethod("pick", double.class, double.class);
        pick.setAccessible(true);
        return (PickResult) pick.invoke(scene, point.getX(), point.getY());
    }

    /// Runs a checked test action on the JavaFX application thread and propagates failures.
    private static void onFxThread(Callable<@Nullable Void> action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
