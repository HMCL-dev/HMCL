// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.game;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.GameSettingsPresetID;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/// Tests editing inherited text settings and returning to the parent value.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
public final class IndependentSettingBinderTest {
    /// The parent preset that supplies inherited values.
    private final GameSettings.Preset preset = new GameSettings.Preset(GameSettingsPresetID.generate());

    /// The instance whose overrides the controls edit.
    private final GameSettings.Instance instance = new GameSettings.Instance();

    /// Keeps weakly registered listeners alive throughout each test.
    private final WeakListenerHolder listeners = new WeakListenerHolder();

    /// Verifies that typing overrides the current parent value and resetting resumes live inheritance.
    @Test
    public void editAndResetInheritedText() throws Exception {
        onFxThread(() -> {
            preset.jvmOptionsProperty().setValue("-Xint");
            JFXTextField field = new JFXTextField();
            JFXButton button = new JFXButton();
            IndependentSettingBinder.bindText(new SimpleObjectProperty<>(instance), field.textProperty(),
                    GameSettings::jvmOptionsProperty, Function.identity(), button, JFXButton::setUserData,
                    listeners, ignored -> preset, new SimpleObjectProperty<>(preset));

            assertEquals("-Xint", field.getText());
            assertFalse(field.isDisabled());
            assertTrue(field.isEditable());
            preset.jvmOptionsProperty().setValue("-ea");
            assertEquals("-ea", field.getText());
            assertTrue(instance.getOverrideProperties().isEmpty());
            assertEquals(true, button.getUserData());

            field.appendText(" -esa");
            assertEquals("-ea -esa", instance.jvmOptionsProperty().getValue());
            assertTrue(instance.getOverrideProperties().contains(GameSettings.PROPERTY_JVM_OPTIONS));
            assertEquals(false, button.getUserData());
            assertEquals("-ea", preset.jvmOptionsProperty().getValue());
            preset.jvmOptionsProperty().setValue("-da");
            assertEquals("-ea -esa", field.getText());

            button.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                    false, false, false, false, false, false, false, false, false, true, null));
            assertEquals("-da", field.getText());
            assertTrue(instance.getOverrideProperties().isEmpty());
            assertEquals(true, button.getUserData());
            preset.jvmOptionsProperty().setValue("-esa");
            assertEquals("-esa", field.getText());
        });
    }

    /// Verifies that clearing an inherited integer stores an explicit null override.
    @Test
    public void clearInheritedInteger() throws Exception {
        onFxThread(() -> {
            preset.minMemoryProperty().setValue(512);
            JFXTextField field = new JFXTextField();
            IndependentSettingBinder.bindText(new SimpleObjectProperty<>(instance), field.textProperty(),
                    GameSettings::minMemoryProperty, Lang::toIntOrNull, null, JFXButton::setUserData,
                    listeners, ignored -> preset, new SimpleObjectProperty<>(preset));

            assertEquals("512", field.getText());
            assertFalse(field.isDisabled());
            field.clear();
            assertTrue(instance.getOverrideProperties().contains(GameSettings.PROPERTY_MIN_MEMORY));
            assertNull(instance.minMemoryProperty().getValue());
            assertEquals(512, preset.minMemoryProperty().getValue());
            preset.minMemoryProperty().setValue(256);
            assertEquals("", field.getText());
            field.appendText("128");
            assertEquals(128, instance.minMemoryProperty().getValue());
        });
    }

    /// Runs a control interaction on the JavaFX thread and propagates assertion failures to JUnit.
    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<Boolean> task = new FutureTask<>(action, true);
        Platform.runLater(task);
        task.get(30, TimeUnit.SECONDS);
    }
}
