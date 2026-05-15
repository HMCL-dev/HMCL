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
package org.jackhuang.hmcl.ui.game;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.scene.input.MouseEvent;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.setting.GameSetting;
import org.jackhuang.hmcl.setting.property.SettingProperty;
import org.jackhuang.hmcl.ui.construct.RadioChoiceList;
import org.jackhuang.hmcl.ui.construct.LineComponent;
import org.jackhuang.hmcl.ui.construct.LineInheritableToggleButton;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;

/// Binds independently overridden game setting properties to setting page controls.
@NotNullByDefault
final class IndependentSettingBinder {
    private IndependentSettingBinder() {
    }

    /// Binds a text field to a setting property with independent override state.
    static void bindTextField(
            boolean globalSetting,
            ObjectProperty<? extends GameSetting> currentSetting,
            LineComponent line,
            JFXTextField textField,
            Function<GameSetting, SettingProperty<String>> propertyGetter,
            Supplier<JFXButton> inheritanceButtonFactory,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<String>> activeProperty = new javafx.beans.property.SimpleObjectProperty<>();
        final boolean[] updating = {false};
        @Nullable JFXButton inheritButton = null;
        if (!globalSetting) {
            inheritButton = inheritanceButtonFactory.get();
            line.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                boolean overridden = isOverridden(setting, property);
                textField.setText(empty(getEffectiveValue(setting, propertyGetter, parentGetter)));
                textField.setDisable(!overridden);
                if (finalInheritButton != null) {
                    inheritanceButtonUpdater.accept(finalInheritButton, !overridden);
                }
            } finally {
                updating[0] = false;
            }
        };

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(newValue != null ? newValue : "");
                if (finalInheritButton != null) {
                    inheritanceButtonUpdater.accept(finalInheritButton, false);
                }
            } finally {
                updating[0] = false;
            }
        });

        if (finalInheritButton != null) {
            finalInheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                SettingProperty<String> property = activeProperty.get();
                if (setting == null || property == null || updating[0]) {
                    return;
                }

                updating[0] = true;
                try {
                    if (isOverridden(setting, property)) {
                        setOverridden(setting, property, false);
                        property.setValue(null);
                    } else {
                        property.setValue(empty(getEffectiveValue(setting, propertyGetter, parentGetter)));
                        setOverridden(setting, property, true);
                    }
                } finally {
                    updating[0] = false;
                }
                refresh.invalidated(property);
                event.consume();
            });
        }

        bindActiveProperty(currentSetting, activeProperty, propertyGetter, refresh);
    }

    /// Binds an integer text field to a setting property with independent override state.
    @SuppressWarnings("unchecked")
    static void bindIntegerTextField(
            boolean globalSetting,
            ObjectProperty<? extends GameSetting> currentSetting,
            LineComponent line,
            JFXTextField textField,
            Function<GameSetting, ? extends SettingProperty<Integer>> propertyGetter,
            boolean nullable,
            Supplier<JFXButton> inheritanceButtonFactory,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Integer>> activeProperty = new javafx.beans.property.SimpleObjectProperty<>();
        final boolean[] updating = {false};
        @Nullable JFXButton inheritButton = null;
        if (!globalSetting) {
            inheritButton = inheritanceButtonFactory.get();
            line.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Integer> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                boolean overridden = isOverridden(setting, property);
                Integer value = getEffectiveValue(setting, propertyGetter, parentGetter);
                textField.setText(value != null ? value.toString() : "");
                textField.setDisable(!overridden);
                if (finalInheritButton != null) {
                    inheritanceButtonUpdater.accept(finalInheritButton, !overridden);
                }
            } finally {
                updating[0] = false;
            }
        };

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Integer> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(parseInteger(newValue, nullable));
                if (finalInheritButton != null) {
                    inheritanceButtonUpdater.accept(finalInheritButton, false);
                }
            } finally {
                updating[0] = false;
            }
        });

        if (finalInheritButton != null) {
            finalInheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                SettingProperty<Integer> property = activeProperty.get();
                if (setting == null || property == null || updating[0]) {
                    return;
                }

                updating[0] = true;
                try {
                    if (isOverridden(setting, property)) {
                        setOverridden(setting, property, false);
                        property.setValue(null);
                    } else {
                        property.setValue(getEffectiveValue(setting, propertyGetter, parentGetter));
                        setOverridden(setting, property, true);
                    }
                } finally {
                    updating[0] = false;
                }
                refresh.invalidated(property);
                event.consume();
            });
        }

        bindActiveProperty(
                currentSetting,
                activeProperty,
                setting -> (SettingProperty<Integer>) propertyGetter.apply(setting),
                refresh);
    }

    /// Binds the game memory radio options and manual memory slider.
    static void bindMemoryChoiceList(
            ObjectProperty<? extends GameSetting> currentSetting,
            RadioChoiceList<Boolean> choiceList,
            JFXSlider maxMemorySlider,
            @Nullable JFXButton autoMemoryButton,
            @Nullable JFXButton maxMemoryButton,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeAutoMemoryProperty = new javafx.beans.property.SimpleObjectProperty<>();
        ObjectProperty<@Nullable SettingProperty<Integer>> activeMaxMemoryProperty = new javafx.beans.property.SimpleObjectProperty<>();
        final boolean[] updating = {false};

        int totalMemoryMiB = Math.max(1, (int) MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize()));
        maxMemorySlider.setValueFactory(slider -> Bindings.createStringBinding(
                () -> sliderValueToMaxMemory(slider.getValue(), totalMemoryMiB) + " MiB",
                slider.valueProperty()));

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Boolean> autoMemoryProperty = activeAutoMemoryProperty.get();
            SettingProperty<Integer> maxMemoryProperty = activeMaxMemoryProperty.get();
            if (setting == null || autoMemoryProperty == null || maxMemoryProperty == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                boolean autoMemoryOverridden = isOverridden(setting, autoMemoryProperty);
                boolean maxMemoryOverridden = isOverridden(setting, maxMemoryProperty);
                Boolean autoMemory = getEffectiveValue(setting, GameSetting::autoMemoryProperty, parentGetter);
                Integer maxMemory = getEffectiveValue(setting, GameSetting::maxMemoryProperty, parentGetter);

                choiceList.setSelectedValue(Boolean.TRUE.equals(autoMemory));
                maxMemorySlider.setValue(maxMemoryToSliderValue(maxMemory, totalMemoryMiB));
                if (autoMemoryButton != null) {
                    inheritanceButtonUpdater.accept(autoMemoryButton, !autoMemoryOverridden);
                }
                if (maxMemoryButton != null) {
                    inheritanceButtonUpdater.accept(maxMemoryButton, !maxMemoryOverridden);
                }
            } finally {
                updating[0] = false;
            }
        };

        choiceList.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Boolean> property = activeAutoMemoryProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(Boolean.TRUE.equals(newValue));
                if (autoMemoryButton != null) {
                    inheritanceButtonUpdater.accept(autoMemoryButton, false);
                }
            } finally {
                updating[0] = false;
            }
        });

        maxMemorySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Integer> property = activeMaxMemoryProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(sliderValueToMaxMemory(newValue.doubleValue(), totalMemoryMiB));
                if (maxMemoryButton != null) {
                    inheritanceButtonUpdater.accept(maxMemoryButton, false);
                }
            } finally {
                updating[0] = false;
            }
        });

        if (autoMemoryButton != null) {
            autoMemoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                toggleOverride(
                        setting,
                        activeAutoMemoryProperty.get(),
                        () -> getEffectiveValue(setting, GameSetting::autoMemoryProperty, parentGetter),
                        refresh);
                event.consume();
            });
        }

        if (maxMemoryButton != null) {
            maxMemoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                toggleOverride(
                        setting,
                        activeMaxMemoryProperty.get(),
                        () -> getEffectiveValue(setting, GameSetting::maxMemoryProperty, parentGetter),
                        refresh);
                event.consume();
            });
        }

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            SettingProperty<Boolean> oldAutoMemoryProperty = activeAutoMemoryProperty.get();
            if (oldAutoMemoryProperty != null) {
                oldAutoMemoryProperty.removeListener(refresh);
            }

            SettingProperty<Integer> oldMaxMemoryProperty = activeMaxMemoryProperty.get();
            if (oldMaxMemoryProperty != null) {
                oldMaxMemoryProperty.removeListener(refresh);
            }

            SettingProperty<Boolean> newAutoMemoryProperty = newValue != null ? newValue.autoMemoryProperty() : null;
            SettingProperty<Integer> newMaxMemoryProperty = newValue != null ? newValue.maxMemoryProperty() : null;
            activeAutoMemoryProperty.set(newAutoMemoryProperty);
            activeMaxMemoryProperty.set(newMaxMemoryProperty);
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            if (newAutoMemoryProperty != null) {
                newAutoMemoryProperty.addListener(refresh);
            }
            if (newMaxMemoryProperty != null) {
                newMaxMemoryProperty.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });
        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        GameSetting setting = currentSetting.get();
        if (setting != null) {
            SettingProperty<Boolean> autoMemoryProperty = setting.autoMemoryProperty();
            SettingProperty<Integer> maxMemoryProperty = setting.maxMemoryProperty();
            activeAutoMemoryProperty.set(autoMemoryProperty);
            activeMaxMemoryProperty.set(maxMemoryProperty);
            setting.addListener(refresh);
            autoMemoryProperty.addListener(refresh);
            maxMemoryProperty.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds an independent boolean setting to a toggle editor.
    static void bindToggleButton(
            ObjectProperty<? extends GameSetting> currentSetting,
            LineInheritableToggleButton button,
            Function<GameSetting, SettingProperty<Boolean>> propertyGetter,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeProperty = new javafx.beans.property.SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                boolean overridden = isOverridden(setting, property);
                Boolean rawValue = overridden ? getDirectValue(property) : null;
                Boolean effectiveValue = getEffectiveValue(setting, propertyGetter, parentGetter);
                button.setRawValue(rawValue);
                button.setEffectiveValue(Boolean.TRUE.equals(effectiveValue));
            } finally {
                updating[0] = false;
            }
        };

        button.rawValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                if (newValue == null) {
                    setOverridden(setting, property, false);
                    property.setValue(null);
                } else {
                    setOverridden(setting, property, true);
                    property.setValue(newValue);
                }
                button.setEffectiveValue(Boolean.TRUE.equals(getEffectiveValue(setting, propertyGetter, parentGetter)));
            } finally {
                updating[0] = false;
            }
        });

        bindActiveProperty(currentSetting, activeProperty, propertyGetter, refresh);
    }

    /// Binds the native directory mode to a boolean toggle editor.
    static void bindNativesDirTypeButton(
            ObjectProperty<? extends GameSetting> currentSetting,
            LineInheritableToggleButton button,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<NativesDirectoryType>> activeProperty = new javafx.beans.property.SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                boolean overridden = isOverridden(setting, property);
                NativesDirectoryType rawValue = getDirectValue(property);
                NativesDirectoryType effectiveValue = getEffectiveValue(setting, GameSetting::nativesDirTypeProperty, parentGetter);
                button.setRawValue(overridden ? rawValue == NativesDirectoryType.CUSTOM : null);
                button.setEffectiveValue(effectiveValue == NativesDirectoryType.CUSTOM);
            } finally {
                updating[0] = false;
            }
        };

        button.rawValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (setting == null || property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                if (newValue == null) {
                    setOverridden(setting, property, false);
                    property.setValue(null);
                } else {
                    setOverridden(setting, property, true);
                    property.setValue(newValue ? NativesDirectoryType.CUSTOM : NativesDirectoryType.VERSION_FOLDER);
                }
                button.setEffectiveValue(getEffectiveValue(setting, GameSetting::nativesDirTypeProperty, parentGetter) == NativesDirectoryType.CUSTOM);
            } finally {
                updating[0] = false;
            }
        });

        bindActiveProperty(currentSetting, activeProperty, GameSetting::nativesDirTypeProperty, refresh);
    }

    private static int sliderValueToMaxMemory(double value, int totalMemoryMiB) {
        return Math.max(0, (int) (clamp(value) * totalMemoryMiB));
    }

    private static double maxMemoryToSliderValue(@Nullable Integer maxMemory, int totalMemoryMiB) {
        if (maxMemory == null || totalMemoryMiB <= 0) {
            return 0;
        }

        return clamp(maxMemory.doubleValue() / totalMemoryMiB);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static <T> void toggleOverride(
            @Nullable GameSetting setting,
            @Nullable SettingProperty<T> property,
            Supplier<T> effectiveValueSupplier,
            InvalidationListener refresh) {
        if (setting == null || property == null) {
            return;
        }

        if (isOverridden(setting, property)) {
            setOverridden(setting, property, false);
            property.setValue(null);
        } else {
            property.setValue(effectiveValueSupplier.get());
            setOverridden(setting, property, true);
        }
        refresh.invalidated(property);
    }

    private static <T> void bindActiveProperty(
            ObjectProperty<? extends GameSetting> currentSetting,
            ObjectProperty<@Nullable SettingProperty<T>> activeProperty,
            Function<GameSetting, SettingProperty<T>> propertyGetter,
            InvalidationListener refresh) {
        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            SettingProperty<T> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(refresh);
            }

            SettingProperty<T> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            if (newProperty != null) {
                newProperty.addListener(refresh);
            }
            refresh.invalidated(newProperty);
        });
        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        GameSetting setting = currentSetting.get();
        if (setting != null) {
            SettingProperty<T> property = propertyGetter.apply(setting);
            activeProperty.set(property);
            setting.addListener(refresh);
            property.addListener(refresh);
            refresh.invalidated(property);
        }
    }

    private static String empty(@Nullable String value) {
        return value != null ? value : "";
    }

    private static @Nullable Integer parseInteger(@Nullable String value, boolean nullable) {
        if (StringUtils.isBlank(value)) {
            return nullable ? null : 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return nullable ? null : 0;
        }
    }

    private static <T> T getDirectValue(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
    }

    private static boolean isOverridden(GameSetting setting, SettingProperty<?> property) {
        return !(setting instanceof GameSetting.Instance instance)
                || instance.getOverrideProperties().contains(property.getName());
    }

    private static void setOverridden(GameSetting setting, SettingProperty<?> property, boolean overridden) {
        if (!(setting instanceof GameSetting.Instance instance)) {
            return;
        }

        if (overridden) {
            instance.getOverrideProperties().add(property.getName());
        } else {
            instance.getOverrideProperties().remove(property.getName());
        }
    }

    private static <T> T getEffectiveValue(
            GameSetting setting,
            Function<GameSetting, ? extends SettingProperty<T>> propertyGetter,
            Function<GameSetting.Instance, GameSetting.Global> parentGetter) {
        SettingProperty<T> property = propertyGetter.apply(setting);
        if (isOverridden(setting, property)) {
            return getDirectValue(property);
        }

        if (setting instanceof GameSetting.Instance instance) {
            return getDirectValue(propertyGetter.apply(parentGetter.apply(instance)));
        }

        return getDirectValue(property);
    }
}
