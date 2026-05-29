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
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.property.SettingProperty;
import org.jackhuang.hmcl.ui.MemoryStatusBar;
import org.jackhuang.hmcl.ui.construct.LineComponent;
import org.jackhuang.hmcl.ui.construct.LineInheritableToggleButton;
import org.jackhuang.hmcl.ui.construct.RadioChoiceList;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jackhuang.hmcl.util.platform.hardware.PhysicalMemoryStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.DataSizeUnit.GIGABYTES;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Binds independently overridden game setting properties to setting page controls.
@NotNullByDefault
final class IndependentSettingBinder {
    private IndependentSettingBinder() {
    }

    /// Binds a text field to a setting property with independent override state.
    static void bindTextField(
            boolean presetSetting,
            ObjectProperty<? extends @Nullable GameSettings> currentSetting,
            LineComponent line,
            JFXTextField textField,
            Function<GameSettings, SettingProperty<String>> propertyGetter,
            Supplier<JFXButton> inheritanceButtonFactory,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<String>> activeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable SettingProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        final Holder<InvalidationListener> refreshHolder = new Holder<>();
        @Nullable JFXButton inheritButton;
        if (presetSetting) {
            inheritButton = null;
        } else {
            inheritButton = inheritanceButtonFactory.get();
            line.setTitleTrailing(inheritButton);
        }

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            updateParentPropertyListener(setting, activeParentProperty, propertyGetter, parentGetter, refreshHolder.value);
            SettingProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean overridden = isOverridden(setting, property);
                textField.setText(empty(getEffectiveValue(setting, propertyGetter, parentGetter)));
                textField.setDisable(!overridden);
                if (inheritButton != null) {
                    inheritanceButtonUpdater.accept(inheritButton, !overridden);
                }
            } finally {
                updating.value = false;
            }
        };
        refreshHolder.value = refresh;

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(newValue != null ? newValue : "");
                if (inheritButton != null) {
                    inheritanceButtonUpdater.accept(inheritButton, false);
                }
            } finally {
                updating.value = false;
            }
        });

        if (inheritButton != null) {
            inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSettings setting = currentSetting.get();
                SettingProperty<String> property = activeProperty.get();
                if (setting == null || property == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    if (isOverridden(setting, property)) {
                        setOverridden(setting, property, false);
                    } else {
                        property.setValue(empty(getEffectiveValue(setting, propertyGetter, parentGetter)));
                        setOverridden(setting, property, true);
                    }
                } finally {
                    updating.value = false;
                }
                refresh.invalidated(property);
                event.consume();
            });
        }

        bindActiveProperty(currentSetting, activeProperty, propertyGetter, refresh);
    }

    /// Binds an integer text field to a setting property with independent override state.
    static void bindIntegerTextField(
            boolean presetSetting,
            ObjectProperty<? extends @Nullable GameSettings> currentSetting,
            LineComponent line,
            JFXTextField textField,
            Function<GameSettings, ? extends SettingProperty<Integer>> propertyGetter,
            Supplier<JFXButton> inheritanceButtonFactory,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Integer>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton;
        if (presetSetting) {
            inheritButton = null;
        } else {
            inheritButton = inheritanceButtonFactory.get();
            line.setTitleTrailing(inheritButton);
        }

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Integer> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean overridden = isOverridden(setting, property);
                Integer value = getEffectiveValue(setting, propertyGetter, parentGetter);
                textField.setText(value != null ? value.toString() : "");
                textField.setDisable(!overridden);
                if (inheritButton != null) {
                    inheritanceButtonUpdater.accept(inheritButton, !overridden);
                }
            } finally {
                updating.value = false;
            }
        };

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Integer> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(parseInteger(newValue, true));
                if (inheritButton != null) {
                    inheritanceButtonUpdater.accept(inheritButton, false);
                }
            } finally {
                updating.value = false;
            }
        });

        if (inheritButton != null) {
            inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSettings setting = currentSetting.get();
                SettingProperty<Integer> property = activeProperty.get();
                if (setting == null || property == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    if (isOverridden(setting, property)) {
                        setOverridden(setting, property, false);
                    } else {
                        property.setValue(getEffectiveValue(setting, propertyGetter, parentGetter));
                        setOverridden(setting, property, true);
                    }
                } finally {
                    updating.value = false;
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
            ObjectProperty<? extends @Nullable GameSettings> currentSetting,
            RadioChoiceList<Boolean> choiceList,
            JFXSlider maxMemorySlider,
            JFXTextField maxMemoryTextField,
            MemoryStatusBar memoryStatusBar,
            BorderPane memoryStatusLabels,
            @Nullable JFXButton autoMemoryButton,
            @Nullable JFXButton maxMemoryButton,
            BiConsumer<JFXButton, Boolean> inheritanceButtonUpdater,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeAutoMemoryProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable SettingProperty<Integer>> activeMaxMemoryProperty = new SimpleObjectProperty<>();
        Label physicalMemoryLabel = (Label) memoryStatusLabels.getLeft();
        Label allocatedMemoryLabel = (Label) memoryStatusLabels.getRight();
        final Holder<Boolean> updating = new Holder<>(false);

        int totalMemoryMiB = Math.max(1, (int) MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize()));
        maxMemorySlider.setValueFactory(slider -> Bindings.createStringBinding(
                () -> (int) (slider.getValue() * 100) + "%",
                slider.valueProperty()));

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Boolean> autoMemoryProperty = activeAutoMemoryProperty.get();
            SettingProperty<Integer> maxMemoryProperty = activeMaxMemoryProperty.get();
            if (setting == null || autoMemoryProperty == null || maxMemoryProperty == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean autoMemoryOverridden = isOverridden(setting, autoMemoryProperty);
                boolean maxMemoryOverridden = isOverridden(setting, maxMemoryProperty);
                Boolean autoMemory = getEffectiveValue(setting, GameSettings::autoMemoryProperty, parentGetter);
                @Nullable Integer maxMemory = getEffectiveValue(setting, GameSettings::maxMemoryProperty, parentGetter);

                choiceList.setSelectedValue(autoMemory);
                maxMemorySlider.setValue(maxMemoryToSliderValue(maxMemory, totalMemoryMiB));
                maxMemoryTextField.setText(maxMemoryToText(maxMemory));
                updateMemoryStatus(memoryStatusBar, physicalMemoryLabel, allocatedMemoryLabel, autoMemory, maxMemory);
                if (autoMemoryButton != null) {
                    inheritanceButtonUpdater.accept(autoMemoryButton, !autoMemoryOverridden);
                }
                if (maxMemoryButton != null) {
                    inheritanceButtonUpdater.accept(maxMemoryButton, !maxMemoryOverridden);
                }
            } finally {
                updating.value = false;
            }
        };

        choiceList.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Boolean> property = activeAutoMemoryProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(Boolean.TRUE.equals(newValue));
                if (autoMemoryButton != null) {
                    inheritanceButtonUpdater.accept(autoMemoryButton, false);
                }
                updateMemoryStatus(
                        memoryStatusBar,
                        physicalMemoryLabel,
                        allocatedMemoryLabel,
                        property.getValue(),
                        getEffectiveValue(setting, GameSettings::maxMemoryProperty, parentGetter));
            } finally {
                updating.value = false;
            }
        });

        maxMemorySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Integer> property = activeMaxMemoryProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                int maxMemory = sliderValueToMaxMemory(newValue.doubleValue(), totalMemoryMiB);
                setOverridden(setting, property, true);
                property.setValue(maxMemory);
                maxMemoryTextField.setText(Integer.toString(maxMemory));
                if (maxMemoryButton != null) {
                    inheritanceButtonUpdater.accept(maxMemoryButton, false);
                }
                updateMemoryStatus(
                        memoryStatusBar,
                        physicalMemoryLabel,
                        allocatedMemoryLabel,
                        getEffectiveValue(setting, GameSettings::autoMemoryProperty, parentGetter),
                        maxMemory);
            } finally {
                updating.value = false;
            }
        });

        maxMemoryTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Integer> property = activeMaxMemoryProperty.get();
            Integer maxMemory = parseMemoryText(newValue);
            if (setting == null || property == null || maxMemory == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(maxMemory);
                maxMemorySlider.setValue(maxMemoryToSliderValue(maxMemory, totalMemoryMiB));
                if (maxMemoryButton != null) {
                    inheritanceButtonUpdater.accept(maxMemoryButton, false);
                }
                updateMemoryStatus(
                        memoryStatusBar,
                        physicalMemoryLabel,
                        allocatedMemoryLabel,
                        getEffectiveValue(setting, GameSettings::autoMemoryProperty, parentGetter),
                        maxMemory);
            } finally {
                updating.value = false;
            }
        });

        if (autoMemoryButton != null) {
            autoMemoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSettings setting = currentSetting.get();
                toggleOverride(
                        setting,
                        activeAutoMemoryProperty.get(),
                        () -> getEffectiveValue(setting, GameSettings::autoMemoryProperty, parentGetter),
                        refresh);
                event.consume();
            });
        }

        if (maxMemoryButton != null) {
            maxMemoryButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSettings setting = currentSetting.get();
                toggleOverride(
                        setting,
                        activeMaxMemoryProperty.get(),
                        () -> getEffectiveValue(setting, GameSettings::maxMemoryProperty, parentGetter),
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
        SettingsManager.getGameSettings().addListener(refresh);
        SettingsManager.defaultGameSettingsPresetProperty().addListener(refresh);
        memoryStatusBar.memoryStatusProperty().addListener(observable -> {
            GameSettings setting = currentSetting.get();
            if (setting == null) {
                return;
            }

            updateMemoryStatus(
                    memoryStatusBar,
                    physicalMemoryLabel,
                    allocatedMemoryLabel,
                    getEffectiveValue(setting, GameSettings::autoMemoryProperty, parentGetter),
                    getEffectiveValue(setting, GameSettings::maxMemoryProperty, parentGetter));
        });

        GameSettings setting = currentSetting.get();
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
            ObjectProperty<? extends @Nullable GameSettings> currentSetting,
            LineInheritableToggleButton button,
            Function<GameSettings, SettingProperty<Boolean>> propertyGetter,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean overridden = isOverridden(setting, property);
                Boolean effectiveValue = getEffectiveValue(setting, propertyGetter, parentGetter);
                button.setRawValue(overridden ? getDirectValue(property) : Boolean.TRUE.equals(effectiveValue));
                button.setOverridden(overridden);
                button.setEffectiveValue(Boolean.TRUE.equals(effectiveValue));
            } finally {
                updating.value = false;
            }
        };

        button.rawValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(newValue);
                button.setEffectiveValue(Boolean.TRUE.equals(getEffectiveValue(setting, propertyGetter, parentGetter)));
            } finally {
                updating.value = false;
            }
        });

        button.overriddenProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, newValue);
                if (newValue) {
                    property.setValue(button.getRawValue());
                }
                button.setEffectiveValue(Boolean.TRUE.equals(getEffectiveValue(setting, propertyGetter, parentGetter)));
            } finally {
                updating.value = false;
            }
        });

        bindActiveProperty(currentSetting, activeProperty, propertyGetter, refresh);
    }

    /// Binds the native directory mode to a boolean toggle editor.
    static void bindNativesDirTypeButton(
            ObjectProperty<? extends GameSettings> currentSetting,
            LineInheritableToggleButton button,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        ObjectProperty<@Nullable SettingProperty<NativesDirectoryType>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean overridden = isOverridden(setting, property);
                NativesDirectoryType rawValue = getDirectValue(property);
                NativesDirectoryType effectiveValue = getEffectiveValue(setting, GameSettings::nativesDirTypeProperty, parentGetter);
                button.setRawValue((overridden ? rawValue : effectiveValue) == NativesDirectoryType.CUSTOM);
                button.setOverridden(overridden);
                button.setEffectiveValue(effectiveValue == NativesDirectoryType.CUSTOM);
            } finally {
                updating.value = false;
            }
        };

        button.rawValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, true);
                property.setValue(newValue ? NativesDirectoryType.CUSTOM : NativesDirectoryType.VERSION_FOLDER);
                button.setEffectiveValue(getEffectiveValue(setting, GameSettings::nativesDirTypeProperty, parentGetter) == NativesDirectoryType.CUSTOM);
            } finally {
                updating.value = false;
            }
        });

        button.overriddenProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setOverridden(setting, property, newValue);
                if (newValue) {
                    property.setValue(button.getRawValue() ? NativesDirectoryType.CUSTOM : NativesDirectoryType.VERSION_FOLDER);
                }
                button.setEffectiveValue(getEffectiveValue(setting, GameSettings::nativesDirTypeProperty, parentGetter) == NativesDirectoryType.CUSTOM);
            } finally {
                updating.value = false;
            }
        });

        bindActiveProperty(currentSetting, activeProperty, GameSettings::nativesDirTypeProperty, refresh);
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

    private static String maxMemoryToText(@Nullable Integer maxMemory) {
        return Integer.toString(Math.max(0, maxMemory != null ? maxMemory : 0));
    }

    private static @Nullable Integer parseMemoryText(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }

        try {
            return Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void updateMemoryStatus(
            MemoryStatusBar memoryStatusBar,
            Label physicalMemoryLabel,
            Label allocatedMemoryLabel,
            @Nullable Boolean autoMemory,
            @Nullable Integer maxMemory) {
        memoryStatusBar.memoryAllocatedProperty().set(calculateAllocatedMemory(memoryStatusBar, autoMemory, maxMemory));
        updateMemoryLabels(memoryStatusBar, physicalMemoryLabel, allocatedMemoryLabel, autoMemory, maxMemory);
    }

    private static double calculateAllocatedMemory(
            MemoryStatusBar memoryStatusBar,
            @Nullable Boolean autoMemory,
            @Nullable Integer maxMemory) {
        long maxMemoryBytes = Math.max(0, maxMemory != null ? maxMemory : 0) * 1024L * 1024L;
        return HMCLGameRepository.getAllocatedMemory(
                maxMemoryBytes,
                memoryStatusBar.getMemoryStatus().getAvailable(),
                Boolean.TRUE.equals(autoMemory));
    }

    private static void updateMemoryLabels(
            MemoryStatusBar memoryStatusBar,
            Label physicalMemoryLabel,
            Label allocatedMemoryLabel,
            @Nullable Boolean autoMemory,
            @Nullable Integer maxMemory) {
        PhysicalMemoryStatus memoryStatus = memoryStatusBar.getMemoryStatus();
        long maxMemoryBytes = Math.max(0, maxMemory != null ? maxMemory : 0) * 1024L * 1024L;
        boolean autoMemoryEnabled = Boolean.TRUE.equals(autoMemory);
        physicalMemoryLabel.setText(i18n("settings.memory.used_per_total",
                GIGABYTES.convertFromBytes(memoryStatus.getUsed()),
                GIGABYTES.convertFromBytes(memoryStatus.getTotal())));
        allocatedMemoryLabel.setText(i18n(
                memoryStatus.hasAvailable() && maxMemoryBytes > memoryStatus.getAvailable()
                        ? (autoMemoryEnabled
                                ? "settings.memory.allocate.auto.exceeded"
                                : "settings.memory.allocate.manual.exceeded")
                        : (autoMemoryEnabled
                                ? "settings.memory.allocate.auto"
                                : "settings.memory.allocate.manual"),
                GIGABYTES.convertFromBytes(maxMemoryBytes),
                GIGABYTES.convertFromBytes(HMCLGameRepository.getAllocatedMemory(
                        maxMemoryBytes,
                        memoryStatus.getAvailable(),
                        autoMemoryEnabled)),
                GIGABYTES.convertFromBytes(memoryStatus.getAvailable())));
    }

    private static <T> void toggleOverride(
            @Nullable GameSettings setting,
            @Nullable SettingProperty<T> property,
            Supplier<T> effectiveValueSupplier,
            InvalidationListener refresh) {
        if (setting == null || property == null) {
            return;
        }

        if (isOverridden(setting, property)) {
            setOverridden(setting, property, false);
        } else {
            property.setValue(effectiveValueSupplier.get());
            setOverridden(setting, property, true);
        }
        refresh.invalidated(property);
    }

    private static <T> void bindActiveProperty(
            ObjectProperty<? extends GameSettings> currentSetting,
            ObjectProperty<@Nullable SettingProperty<T>> activeProperty,
            Function<GameSettings, SettingProperty<T>> propertyGetter,
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
        SettingsManager.getGameSettings().addListener(refresh);
        SettingsManager.defaultGameSettingsPresetProperty().addListener(refresh);

        GameSettings setting = currentSetting.get();
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

    private static boolean isOverridden(GameSettings setting, SettingProperty<?> property) {
        return !(setting instanceof GameSettings.Instance instance)
                || instance.getOverrideProperties().contains(property.getName());
    }

    private static void setOverridden(GameSettings setting, SettingProperty<?> property, boolean overridden) {
        if (!(setting instanceof GameSettings.Instance instance)) {
            return;
        }

        if (overridden) {
            instance.getOverrideProperties().add(property.getName());
        } else {
            instance.getOverrideProperties().remove(property.getName());
        }
    }

    private static <T extends @UnknownNullability Object> T getEffectiveValue(
            GameSettings setting,
            Function<GameSettings, ? extends SettingProperty<T>> propertyGetter,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter) {
        SettingProperty<T> property = propertyGetter.apply(setting);
        if (isOverridden(setting, property)) {
            return getDirectValue(property);
        }

        if (setting instanceof GameSettings.Instance instance) {
            return getDirectValue(propertyGetter.apply(parentGetter.apply(instance)));
        }

        return getDirectValue(property);
    }

    /// Keeps a listener attached to the current instance's parent preset property.
    private static <T> void updateParentPropertyListener(
            @Nullable GameSettings setting,
            ObjectProperty<@Nullable SettingProperty<T>> activeParentProperty,
            Function<GameSettings, ? extends SettingProperty<T>> propertyGetter,
            Function<GameSettings.Instance, GameSettings.Preset> parentGetter,
            InvalidationListener listener) {
        SettingProperty<T> oldParentProperty = activeParentProperty.get();
        SettingProperty<T> newParentProperty = setting instanceof GameSettings.Instance instance
                ? propertyGetter.apply(parentGetter.apply(instance))
                : null;
        if (oldParentProperty == newParentProperty) {
            return;
        }

        if (oldParentProperty != null) {
            oldParentProperty.removeListener(listener);
        }
        activeParentProperty.set(newParentProperty);
        if (newParentProperty != null) {
            newParentProperty.addListener(listener);
        }
    }
}
