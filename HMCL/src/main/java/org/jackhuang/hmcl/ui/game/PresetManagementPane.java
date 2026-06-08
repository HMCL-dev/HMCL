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
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.setting.SettingId;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentSublist;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.construct.RadioChoiceList;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocalizedText;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays and edits global game setting presets.
@NotNullByDefault
final class PresetManagementPane extends ComponentSublist {
    /// The currently edited game setting.
    private final ObservableValue<? extends @Nullable GameSettings> currentSetting;

    /// Selects a preset for editing in the owning page.
    private final Consumer<GameSettings.Preset> selectPreset;

    /// Keeps listeners weakly attached to global preset state.
    private final WeakListenerHolder holder;

    /// The rendered preset choices.
    private final RadioChoiceList<GameSettings.Preset> presetItem = new RadioChoiceList<>();

    /// Creates a preset management pane.
    PresetManagementPane(
            ObservableValue<? extends @Nullable GameSettings> currentSetting,
            Consumer<GameSettings.Preset> selectPreset,
            WeakListenerHolder holder) {
        this.currentSetting = Objects.requireNonNull(currentSetting);
        this.selectPreset = Objects.requireNonNull(selectPreset);
        this.holder = Objects.requireNonNull(holder);

        setTitle(i18n("settings.type.global.preset.manage_all"));
        setHasSubtitle(true);

        LineButton createButton = new LineButton();
        createButton.setTitle(i18n("settings.type.global.preset.create"));
        createButton.setLeading(SVG.ADD, 20);
        createButton.setOnAction(event -> createPreset());
        getContent().setAll(presetItem, createButton);

        bindPresetChoices();
    }

    /// Returns the display name for a preset.
    static String getPresetDisplayName(GameSettings.Preset setting) {
        @Nullable String name = getPresetCustomName(setting);
        if (StringUtils.isNotBlank(name)) {
            return name;
        }

        Integer autoNameNumber = setting.autoNameNumberProperty().getValue();
        if (autoNameNumber == null) {
            return setting.idProperty().getValue().toString();
        }
        return i18n("settings.type.global.preset.auto_name", autoNameNumber);
    }

    /// Returns the custom preset name in the current locale.
    private static @Nullable String getPresetCustomName(GameSettings.Preset setting) {
        @Nullable LocalizedText name = setting.nameProperty().getValue();
        return name != null ? name.getText(I18n.getLocale().getCandidateLocales()) : null;
    }

    /// Returns the preset currently selected by the owning page.
    private @Nullable GameSettings.Preset getCurrentPreset() {
        GameSettings setting = currentSetting.getValue();
        return setting instanceof GameSettings.Preset preset ? preset : null;
    }

    /// Binds the preset list to global presets and the owning page selection.
    private void bindPresetChoices() {
        final Holder<Boolean> updating = new Holder<>(false);
        Runnable rebuildItems = () -> {
            updating.value = true;
            try {
                List<RadioChoiceList.Choice<GameSettings.Preset>> choices = new ArrayList<>();
                for (GameSettings.Preset setting : SettingsManager.getGameSettings()) {
                    choices.add(new PresetChoice(setting));
                }
                presetItem.setFallbackValue(SettingsManager.getDefaultGameSettingsPresetOrCreate());
                presetItem.setChoices(choices);
                presetItem.setSelectedValue(getCurrentPreset());
                updateDescription();
            } finally {
                updating.value = false;
            }
        };
        ListChangeListener<GameSettings.Preset> updateItems = change -> {
            boolean rebuild = false;
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved() || change.wasPermutated()) {
                    rebuild = true;
                }
            }

            if (rebuild) {
                rebuildItems.run();
            } else {
                updateLabels();
            }
        };

        presetItem.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            if (!updating.value && newValue != null) {
                selectPreset.accept(newValue);
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            updating.value = true;
            try {
                presetItem.setSelectedValue(getCurrentPreset());
                updateDescription();
            } finally {
                updating.value = false;
            }
        });

        SettingsManager.getGameSettings().addListener(holder.weak(updateItems));
        rebuildItems.run();
    }

    /// Updates existing preset choices without rebuilding the rendered list.
    private void updateLabels() {
        for (RadioChoiceList.Choice<GameSettings.Preset> choice : presetItem.getChoices()) {
            choice.setTitle(getPresetDisplayName(choice.getValue()));
        }
        updateDescription();
    }

    /// Updates the selected preset name shown in the pane header.
    private void updateDescription() {
        GameSettings.Preset setting = getCurrentPreset();
        setDescription(setting != null ? getPresetDisplayName(setting) : "");
    }

    /// Creates a new preset and selects it for editing.
    private void createPreset() {
        int number = createDefaultPresetNumber();
        PromptDialogPane.Builder.StringQuestion nameQuestion =
                new PromptDialogPane.Builder.StringQuestion("", "")
                        .setPromptText(i18n("settings.type.global.preset.auto_name", number));
        Controllers.prompt(new PromptDialogPane.Builder(i18n("settings.type.global.preset.create"), (questions, handler) -> {
            String name = (String) questions.get(0).getValue();
            GameSettings.Preset setting = new GameSettings.Preset(SettingsManager.gameSettingsPresets().newPresetId());
            if (StringUtils.isBlank(name)) {
                setting.autoNameNumberProperty().setValue(number);
            } else {
                setting.nameProperty().setValue(LocalizedText.plain(name.trim()));
            }
            SettingsManager.getGameSettings().add(setting);
            selectPreset.accept(setting);
            handler.resolve();
        }).addQuestion(nameQuestion));
    }

    /// Returns the first automatic preset number that is not used by existing presets.
    private int createDefaultPresetNumber() {
        for (int index = 1; ; index++) {
            String name = i18n("settings.type.global.preset.auto_name", index);
            boolean used = false;
            for (GameSettings.Preset setting : SettingsManager.getGameSettings()) {
                Integer autoNameNumber = setting.autoNameNumberProperty().getValue();
                if ((autoNameNumber != null && autoNameNumber == index)
                        || Objects.equals(name, getPresetDisplayName(setting))) {
                    used = true;
                    break;
                }
            }

            if (!used) {
                return index;
            }
        }
    }

    /// Asks the user for a new preset name.
    private void renamePreset(GameSettings.Preset setting) {
        @Nullable String currentName = getPresetCustomName(setting);
        PromptDialogPane.Builder.StringQuestion nameQuestion =
                new PromptDialogPane.Builder.StringQuestion("", currentName != null ? currentName : "")
                        .setPromptText(getPresetDisplayName(setting));
        Controllers.prompt(new PromptDialogPane.Builder(i18n("settings.type.global.preset.rename"), (questions, handler) -> {
            String name = (String) questions.get(0).getValue();
            if (StringUtils.isBlank(name)) {
                handler.reject(i18n("input.not_empty"));
                return;
            }

            setting.nameProperty().setValue(LocalizedText.plain(name.trim()));
            setting.autoNameNumberProperty().setValue(null);
            handler.resolve();
        }).addQuestion(nameQuestion));
    }

    /// Asks the user to confirm removing the given preset.
    private void confirmRemovePreset(GameSettings.Preset setting) {
        if (SettingsManager.getGameSettings().size() <= 1) {
            return;
        }

        Controllers.confirm(
                i18n("settings.type.global.preset.remove.confirm", getPresetDisplayName(setting)),
                i18n("settings.type.global.preset.remove"),
                () -> removePreset(setting),
                null);
    }

    /// Removes a preset and selects another preset for editing.
    private void removePreset(GameSettings.Preset setting) {
        ObservableList<GameSettings.Preset> settings = SettingsManager.getGameSettings();
        int index = settings.indexOf(setting);
        if (index < 0 || settings.size() <= 1) {
            return;
        }

        boolean removedCurrentPreset = Objects.equals(getCurrentPreset(), setting);
        GameSettings.Preset next = settings.get(index == 0 ? 1 : index - 1);
        SettingId removedId = setting.idProperty().getValue();
        if (Objects.equals(SettingsManager.getDefaultGameSettingsPreset(), removedId)) {
            SettingsManager.setDefaultGameSettingsPreset(next.idProperty().getValue());
        }

        settings.remove(index);
        if (SettingsManager.getGameSettings(SettingsManager.getDefaultGameSettingsPreset()) == null) {
            SettingsManager.setDefaultGameSettingsPreset(next.idProperty().getValue());
        }
        if (removedCurrentPreset) {
            selectPreset.accept(next);
        }
    }

    /// Preset option with an inline remove button.
    private final class PresetChoice extends RadioChoiceList.Choice<GameSettings.Preset> {
        /// Creates a preset option.
        private PresetChoice(GameSettings.Preset setting) {
            super(getPresetDisplayName(setting), setting);
        }

        /// Creates the remove button shown on the right side of the option.
        @Override
        protected Node createRightNode() {
            JFXButton renameButton = FXUtils.newToggleButton4(SVG.EDIT, 14);
            renameButton.setOnAction(event -> {
                renamePreset(getValue());
                event.consume();
            });
            FXUtils.installFastTooltip(renameButton, i18n("settings.type.global.preset.rename"));

            JFXButton removeButton = FXUtils.newToggleButton4(SVG.DELETE_FOREVER, 14);
            removeButton.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> SettingsManager.getGameSettings().size() <= 1,
                    SettingsManager.getGameSettings()));
            removeButton.setOnAction(event -> {
                confirmRemovePreset(getValue());
                event.consume();
            });
            FXUtils.installFastTooltip(removeButton, i18n("settings.type.global.preset.remove"));

            HBox buttons = new HBox(8, renameButton, removeButton);
            buttons.setAlignment(Pos.CENTER_RIGHT);
            return buttons;
        }

        /// Keeps the remove button available on every preset option, not only the selected one.
        @Override
        protected boolean shouldDisableRightNodeWhenUnselected() {
            return false;
        }
    }
}
