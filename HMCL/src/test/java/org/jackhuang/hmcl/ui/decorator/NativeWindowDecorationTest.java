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

import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import org.jackhuang.hmcl.JavaFXLauncher;
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
        Class<?> listenerClass = Class.forName("javafx.scene.Scene$ScenePeerListener");
        Constructor<?> constructor = listenerClass.getDeclaredConstructor(Scene.class);
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(scene);
        Method pick = listenerClass.getDeclaredMethod("pickHeaderArea", double.class, double.class);
        pick.setAccessible(true);
        Point2D point = node.localToScene(node.getLayoutBounds().getWidth() / 2, node.getLayoutBounds().getHeight() / 2);
        @Nullable Object result = pick.invoke(listener, point.getX(), point.getY());
        return result == null ? null : result.toString();
    }

    /// Runs a checked test action on the JavaFX application thread and propagates failures.
    private static void onFxThread(Callable<@Nullable Void> action) throws Exception {
        FutureTask<@Nullable Void> task = new FutureTask<>(action);
        Platform.runLater(task);
        task.get(10, TimeUnit.SECONDS);
    }
}
