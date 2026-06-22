/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.*;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.stage.FileChooser;
import org.glavo.monetfx.ColorStyle;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.setting.UserSettings;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemePackExporter;
import org.jackhuang.hmcl.theme.ThemePackManifest;
import org.jackhuang.hmcl.theme.ThemePackManager;
import org.jackhuang.hmcl.theme.ThemeSelection;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.userSettings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Configures launcher appearance, background, and launcher font settings.
@NotNullByDefault
public class PersonalizationPage extends StackPane {
    /// Internal selection reference for the built-in default theme.
    private static final ThemeSelection DEFAULT_THEME_SELECTION =
            new ThemeSelection("hmcl.builtin.default", null);

    /// Time format used by default exported theme names.
    private static final DateTimeFormatter EXPORTED_THEME_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    /// Whether launcher theme fields are currently being changed by applying a theme.
    private static boolean applyingTheme = false;

    /// Snaps a percent value to the nearest 5% opacity tick.
    private static double snapOpacity(double val) {
        if (val <= 0) {
            return 0.;
        } else if (Double.isNaN(val) || val >= 100.) {
            return 1.;
        }

        int prevTick = (int) (val / 5);
        int prevTickValue = prevTick * 5;
        int nextTickValue = (prevTick + 1) * 5;

        int percent = (val - prevTickValue) > (nextTickValue - val) ? nextTickValue : prevTickValue;
        return percent / 100.;
    }

    /// Loads all theme choices shown by the theme selector.
    private static List<ThemeChoice> loadThemeChoices() {
        ArrayList<ThemeChoice> choices = new ArrayList<>();
        choices.add(ThemeChoice.defaultTheme());

        try {
            for (ThemePackManager.InstalledThemePack themePack : ThemePackManager.listInstalled()) {
                for (Theme theme : themePack.manifest().themes()) {
                    choices.add(ThemeChoice.installed(themePack, theme));
                }
            }
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.load.failed"), e);
        }

        @Nullable ThemeSelection selection = settings().themeProperty().get();
        if (selection != null && findThemeChoice(choices, selection) == null) {
            choices.add(ThemeChoice.missing(selection));
        }

        return choices;
    }

    /// Returns the choice that matches the current launcher theme selection.
    private static ThemeChoice getSelectedThemeChoice(List<ThemeChoice> choices) {
        @Nullable ThemeSelection selection = settings().themeProperty().get();
        if (selection != null) {
            @Nullable ThemeChoice choice = findThemeChoice(choices, selection);
            if (choice != null) {
                return choice;
            }
        }

        return selection != null ? ThemeChoice.missing(selection) : ThemeChoice.custom();
    }

    /// Finds a selectable theme choice by selection reference.
    private static @Nullable ThemeChoice findThemeChoice(List<ThemeChoice> choices, ThemeSelection selection) {
        for (ThemeChoice choice : choices) {
            if (Objects.equals(choice.selection(), selection)) {
                return choice;
            }
        }
        return null;
    }

    /// Returns a concise title for a theme choice from an installed theme pack.
    private static String getThemeChoiceTitle(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        if (themePack.manifest().themes().size() == 1) {
            return themePack.manifest().name();
        }
        return themePack.manifest().name() + " - " + getThemeDisplayName(themePack.manifest(), theme);
    }

    /// Returns a subtitle for a theme choice from an installed theme pack.
    private static String getThemeChoiceDescription(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        if (!StringUtils.isBlank(theme.description())) {
            return theme.description();
        }
        if (!StringUtils.isBlank(themePack.manifest().description())) {
            return themePack.manifest().description();
        }
        return themePack.manifest().id();
    }

    /// Returns the effective display name for a theme.
    private static String getThemeDisplayName(ThemePackManifest manifest, Theme theme) {
        return theme.name() != null ? theme.name() : manifest.name();
    }

    /// Returns a subtitle for a missing theme selection.
    private static String getMissingThemeChoiceDescription(ThemeSelection selection) {
        if (selection.themeId() == null) {
            return selection.packId();
        }
        return selection.packId() + " - " + selection.themeId();
    }

    /// Returns the file chooser filter for HMCL theme-pack files.
    private static FileChooser.ExtensionFilter getThemePackExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("theme_pack.file"), "*" + ThemePackExporter.FILE_EXTENSION);
    }

    /// Returns a trimmed prompt value or falls back to the provided default value when blank.
    private static String getPromptValueOrDefault(PromptDialogPane.Builder.StringQuestion question, String defaultValue) {
        String value = question.getValue();
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }

    /// Returns a safe theme-pack file name derived from the package name.
    private static String getThemePackFileName(String packName) {
        String fileName = sanitizeThemePackFileName(packName) + ThemePackExporter.FILE_EXTENSION;
        return FileUtils.isNameValid(fileName) ? fileName : "theme-pack" + ThemePackExporter.FILE_EXTENSION;
    }

    /// Sanitizes a package name for use as a file name without changing the package metadata.
    private static String sanitizeThemePackFileName(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        value.trim().codePoints().forEach(codePoint -> {
            if (isUnsafeThemePackFileNameCodePoint(codePoint)) {
                builder.append('_');
            } else {
                builder.appendCodePoint(codePoint);
            }
        });

        String sanitized = builder.toString().replaceAll("[.\\s]+$", "");
        return sanitized.isBlank() ? "theme-pack" : sanitized;
    }

    /// Returns `true` when the code point is unsafe in cross-platform file names.
    private static boolean isUnsafeThemePackFileNameCodePoint(int codePoint) {
        return !Character.isValidCodePoint(codePoint)
                || Character.isISOControl(codePoint)
                || codePoint == '/'
                || codePoint == '\\'
                || codePoint == ':'
                || codePoint == '<'
                || codePoint == '>'
                || codePoint == '"'
                || codePoint == '|'
                || codePoint == '?'
                || codePoint == '*'
                || codePoint == 0xfffd
                || codePoint == 0xfffe
                || codePoint == 0xffff;
    }

    /// Ensures the selected output file uses the theme-pack extension.
    private static Path ensureThemePackExtension(Path output) {
        String fileName = output.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).endsWith(ThemePackExporter.FILE_EXTENSION)) {
            return output;
        }
        return output.resolveSibling(fileName + ThemePackExporter.FILE_EXTENSION);
    }

    /// Shows a theme-pack operation error dialog.
    private static void showThemePackError(String title, Exception exception) {
        Controllers.dialog(
                title + "\n\n" + StringUtils.getStackTrace(exception),
                i18n("message.error"),
                MessageType.ERROR);
    }

    /// Applies the built-in default launcher theme.
    private static void applyDefaultTheme() {
        settings().themeBrightnessProperty().set("light");
        settings().customThemeColorProperty().set(ThemeColor.DEFAULT);
        settings().themeColorTypeProperty().set(ThemeColorType.CUSTOM);
        settings().themeColorStyleProperty().set(ColorStyle.FIDELITY);
        settings().backgroundTypeProperty().set(BackgroundType.DEFAULT);
        settings().customBackgroundImagePathProperty().set(null);
        settings().backgroundImageUrlProperty().set(null);
        settings().backgroundPaintProperty().set(null);
        settings().backgroundOpacityProperty().set(1.0);
        settings().titleTransparentProperty().set(false);
        settings().themeProperty().set(DEFAULT_THEME_SELECTION);
    }

    /// Registers a listener for fields that are controlled by launcher themes.
    private static void installThemeCustomizationListener(InvalidationListener listener) {
        settings().themeBrightnessProperty().addListener(listener);
        settings().customThemeColorProperty().addListener(listener);
        settings().themeColorTypeProperty().addListener(listener);
        settings().themeColorStyleProperty().addListener(listener);
        settings().backgroundTypeProperty().addListener(listener);
        settings().customBackgroundImagePathProperty().addListener(listener);
        settings().backgroundImageUrlProperty().addListener(listener);
        settings().backgroundPaintProperty().addListener(listener);
        settings().backgroundOpacityProperty().addListener(listener);
        settings().titleTransparentProperty().addListener(listener);
    }

    /// A selectable launcher theme or the local custom appearance.
    ///
    /// @param title the label shown by the selector
    /// @param description the optional secondary text shown in the selector popup
    /// @param customAppearance whether this item represents the local custom appearance
    /// @param defaultAppearance whether this item represents the built-in default appearance
    /// @param themePack the installed theme pack, or `null` for non-pack choices
    /// @param theme the installed theme, or `null` for non-pack choices
    /// @param selection the stored selection reference, or `null` for the local custom appearance
    private record ThemeChoice(
            String title,
            @Nullable String description,
            boolean customAppearance,
            boolean defaultAppearance,
            @Nullable ThemePackManager.InstalledThemePack themePack,
            @Nullable Theme theme,
            @Nullable ThemeSelection selection) {
        /// Creates a theme selector choice.
        private ThemeChoice {
            Objects.requireNonNull(title);
        }

        /// Creates the local custom appearance choice.
        private static ThemeChoice custom() {
            return new ThemeChoice(i18n("theme_pack.current.custom"), null, true, false, null, null, null);
        }

        /// Creates the built-in default theme choice.
        private static ThemeChoice defaultTheme() {
            return new ThemeChoice(
                    i18n("theme_pack.default"),
                    i18n("theme_pack.default.description"),
                    false,
                    true,
                    null,
                    null,
                    DEFAULT_THEME_SELECTION);
        }

        /// Creates a choice for an installed theme-pack theme.
        private static ThemeChoice installed(ThemePackManager.InstalledThemePack themePack, Theme theme) {
            ThemeSelection selection = new ThemeSelection(
                    themePack.manifest().id(),
                    theme.id());
            return new ThemeChoice(
                    getThemeChoiceTitle(themePack, theme),
                    getThemeChoiceDescription(themePack, theme),
                    false,
                    false,
                    themePack,
                    theme,
                    selection);
        }

        /// Creates the placeholder shown when the stored theme selection cannot be resolved.
        private static ThemeChoice missing(ThemeSelection selection) {
            return new ThemeChoice(
                    i18n("theme_pack.current.missing"),
                    getMissingThemeChoiceDescription(selection),
                    false,
                    false,
                    null,
                    null,
                    selection);
        }

        /// Applies this choice to launcher settings.
        ///
        /// @return `true` if a selection was applied, otherwise `false`
        /// @throws IOException if an installed theme cannot be applied
        private boolean apply() throws IOException {
            if (customAppearance) {
                settings().themeProperty().set(null);
                return true;
            }
            if (defaultAppearance) {
                applyDefaultTheme();
                return true;
            }
            if (themePack == null || theme == null) {
                return false;
            }

            ThemePackManager.apply(themePack, theme);
            return true;
        }

        /// Returns a display name for apply-result messages.
        private String applyDisplayName() {
            return theme != null && themePack != null ? getThemeDisplayName(themePack.manifest(), theme) : title;
        }
    }

    /// Asks for theme-pack metadata and then saves the current launcher appearance as a theme-pack file.
    private void exportCurrentThemePack() {
        Instant exportTimestamp = Instant.now();
        String defaultPackId = UUIDs.toCompactString(UUIDs.generateV7(exportTimestamp));
        String defaultPackName = LocalDateTime.ofInstant(exportTimestamp, ZoneId.systemDefault()).format(EXPORTED_THEME_NAME_FORMATTER);

        String userName = System.getProperty("user.name").trim();
        String defaultAuthorName = StringUtils.isBlank(userName) ? "Unknown" : userName;

        PromptDialogPane.Builder.StringQuestion packIdQuestion = new PromptDialogPane.Builder.StringQuestion(
                i18n("theme_pack.export.id"),
                "")
                .setPromptText(defaultPackId);
        PromptDialogPane.Builder.StringQuestion packNameQuestion = new PromptDialogPane.Builder.StringQuestion(
                i18n("theme_pack.export.name"),
                "")
                .setPromptText(defaultPackName);
        PromptDialogPane.Builder.StringQuestion authorNameQuestion = new PromptDialogPane.Builder.StringQuestion(
                i18n("theme_pack.export.author"),
                "")
                .setPromptText(defaultAuthorName);

        Controllers.prompt(new PromptDialogPane.Builder(i18n("theme_pack.export.title"), (questions, handler) -> handler.resolve())
                .addQuestion(packIdQuestion)
                .addQuestion(packNameQuestion)
                .addQuestion(authorNameQuestion)).thenAccept(questions -> exportCurrentThemePack(
                getPromptValueOrDefault(packIdQuestion, defaultPackId),
                getPromptValueOrDefault(packNameQuestion, defaultPackName),
                getPromptValueOrDefault(authorNameQuestion, defaultAuthorName)));
    }

    /// Saves current launcher appearance as a theme-pack file with the given package metadata.
    private void exportCurrentThemePack(String packId, String packName, String authorName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("theme_pack.export.title"));
        chooser.setInitialFileName(getThemePackFileName(packName));
        chooser.getExtensionFilters().setAll(getThemePackExtensionFilter());

        @Nullable Path output = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (output == null) {
            return;
        }

        output = ensureThemePackExtension(output);
        try {
            ThemePackManager.exportCurrent(
                    output,
                    packId,
                    packName,
                    authorName);
            Controllers.dialog(
                    i18n("theme_pack.export.success", output),
                    i18n("message.success"),
                    MessageType.SUCCESS);
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.export.failed"), e);
        }
    }

    /// Creates the launcher appearance settings page.
    public PersonalizationPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        ComponentList themeList = new ComponentList();
        {
            Holder<List<ThemeChoice>> themeChoices = new Holder<>(List.of(ThemeChoice.defaultTheme()));
            var themeSelectButton = new LineSelectButton<ThemeChoice>();
            themeSelectButton.setTitle(i18n("theme_pack.current.title"));
            themeSelectButton.setNullSafeConverter(ThemeChoice::title);
            themeSelectButton.setDescriptionConverter(choice -> Objects.toString(choice.description(), ""));

            boolean[] updatingThemeChoice = {false};
            Runnable refreshSelectedTheme = () -> {
                updatingThemeChoice[0] = true;
                try {
                    themeSelectButton.setValue(getSelectedThemeChoice(themeChoices.value));
                } finally {
                    updatingThemeChoice[0] = false;
                }
            };
            Runnable reloadThemeChoices = () -> {
                updatingThemeChoice[0] = true;
                try {
                    themeChoices.value = loadThemeChoices();
                    themeSelectButton.setItems(themeChoices.value);
                    themeSelectButton.setValue(getSelectedThemeChoice(themeChoices.value));
                } finally {
                    updatingThemeChoice[0] = false;
                }
            };
            FXUtils.onChange(themeSelectButton.valueProperty(), choice -> {
                if (updatingThemeChoice[0]) {
                    return;
                }

                try {
                    applyingTheme = true;
                    boolean applied = choice.apply();
                    if (applied && !choice.customAppearance()) {
                        Controllers.showToast(i18n("theme_pack.apply.success", choice.applyDisplayName()));
                    }
                } catch (IOException | RuntimeException e) {
                    showThemePackError(i18n("theme_pack.apply.failed"), e);
                } finally {
                    applyingTheme = false;
                    refreshSelectedTheme.run();
                }
            });
            FXUtils.onChange(settings().themeProperty(), ignored -> reloadThemeChoices.run());
            reloadThemeChoices.run();
            themeList.getContent().add(themeSelectButton);

            LineButton manageThemeButton = LineButton.createNavigationButton();
            manageThemeButton.setTitle(i18n("theme_pack.manage"));
            manageThemeButton.setSubtitle(i18n("theme_pack.manage.subtitle"));
            manageThemeButton.setOnAction(event -> Controllers.navigateForward(new ThemePackManagementPage()));
            themeList.getContent().add(manageThemeButton);

            LineButton exportThemeButton = new LineButton();
            exportThemeButton.setTitle(i18n("theme_pack.export"));
            exportThemeButton.setSubtitle(i18n("theme_pack.export.subtitle"));
            exportThemeButton.setTrailingIcon(SVG.ARCHIVE);
            exportThemeButton.setOnAction(event -> exportCurrentThemePack());
            themeList.getContent().add(exportThemeButton);
        }
        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.theme")), themeList);

        ComponentList themeAppearanceList = new ComponentList();

        {
            var brightnessPane = new LineSelectButton<String>();
            brightnessPane.setTitle(i18n("settings.launcher.brightness"));
            brightnessPane.setNullSafeConverter(name -> i18n("settings.launcher.brightness." + name));
            brightnessPane.setItems("auto", "light", "dark");
            brightnessPane.valueProperty().bindBidirectional(settings().themeBrightnessProperty());

            themeAppearanceList.getContent().add(brightnessPane);
        }

        {
            ComponentSublist themeColorSublist = new ComponentSublist();
            themeColorSublist.setTitle(i18n("settings.launcher.theme_color"));
            themeColorSublist.setHasSubtitle(true);
            themeColorSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                        ThemeColorType type = Lang.requireNonNullElse(
                                settings().themeColorTypeProperty().get(),
                                ThemeColorType.CUSTOM);
                        return i18n("settings.launcher.theme_color_type." + type.name().toLowerCase(Locale.ROOT));
                    },
                    settings().themeColorTypeProperty()));

            ColorPicker picker = new JFXColorPicker();
            picker.getCustomColors().setAll(ThemeColor.STANDARD_COLORS.stream().map(ThemeColor::color).toList());
            ThemeColor.bindBidirectional(picker, settings().customThemeColorProperty());
            Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));

            var customColorChoice = new RadioChoiceList.Choice<>(
                    i18n("settings.launcher.theme_color_type.custom"),
                    ThemeColorType.CUSTOM) {
                @Override
                protected Node createRightNode() {
                    return picker;
                }
            };
            customColorChoice.setSubtitle(i18n("settings.launcher.theme_color_type.custom.description"));

            var backgroundColorChoice = new RadioChoiceList.Choice<>(
                    i18n("settings.launcher.theme_color_type.background"),
                    ThemeColorType.BACKGROUND);

            var themeColorChoiceList = new RadioChoiceList<ThemeColorType>();
            themeColorChoiceList.setFallbackValue(ThemeColorType.CUSTOM);
            themeColorChoiceList.setChoices(Arrays.asList(customColorChoice, backgroundColorChoice));
            themeColorChoiceList.selectedValueProperty().bindBidirectional(settings().themeColorTypeProperty());

            themeColorSublist.getContent().setAll(themeColorChoiceList);
            themeAppearanceList.getContent().add(themeColorSublist);
        }

        {
            var colorStylePane = new LineSelectButton<ColorStyle>();
            colorStylePane.setTitle(i18n("settings.launcher.theme_color_style"));
            colorStylePane.setNullSafeConverter(style ->
                    i18n("settings.launcher.theme_color_style." + style.name().toLowerCase(Locale.ROOT)));
            colorStylePane.setItems(ColorStyle.values());
            colorStylePane.valueProperty().bindBidirectional(settings().themeColorStyleProperty());

            themeAppearanceList.getContent().add(colorStylePane);
        }

        {
            MultiFileItem<BackgroundType> backgroundItem = new MultiFileItem<>();
            ComponentSublist backgroundSublist = new ComponentSublist();
            backgroundSublist.setTitle(i18n("launcher.background"));
            backgroundSublist.setHasSubtitle(true);

            backgroundItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("launcher.background.default"), BackgroundType.DEFAULT)
                            .setTooltip(i18n("launcher.background.default.tooltip")),
                    new MultiFileItem.Option<>(i18n("launcher.background.classic"), BackgroundType.CLASSIC),
                    new MultiFileItem.FileOption<>(i18n("settings.custom"), BackgroundType.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .setSelectionMode(FileSelector.SelectionMode.FILE_OR_DIRECTORY)
                            .bindBidirectional(settings().customBackgroundImagePathProperty()),
                    new MultiFileItem.StringOption<>(i18n("launcher.background.network"), BackgroundType.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindBidirectional(settings().backgroundImageUrlProperty()),
                    new MultiFileItem.PaintOption<>(i18n("launcher.background.paint"), BackgroundType.PAINT)
                            .bindBidirectional(settings().backgroundPaintProperty())
            ));
            backgroundItem.selectedDataProperty().bindBidirectional(settings().backgroundTypeProperty());
            backgroundSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                        BackgroundType type = Lang.requireNonNullElse(
                                backgroundItem.selectedDataProperty().get(),
                                BackgroundType.DEFAULT);

                        return switch (type) {
                            case DEFAULT -> i18n("launcher.background.default");
                            case CLASSIC -> i18n("launcher.background.classic");
                            case CUSTOM -> settings().customBackgroundImagePathProperty().get();
                            case NETWORK -> settings().backgroundImageUrlProperty().get();
                            case PAINT -> {
                                @Nullable Paint backgroundPaint = settings().backgroundPaintProperty().get();
                                yield backgroundPaint != null ? backgroundPaint.toString() : i18n("launcher.background.paint");
                            }
                        };
                    },
                    backgroundItem.selectedDataProperty(),
                    settings().customBackgroundImagePathProperty(),
                    settings().backgroundImageUrlProperty(),
                    settings().backgroundPaintProperty()));

            HBox opacityItem = new HBox(8);
            {
                opacityItem.setAlignment(Pos.CENTER);

                Label label = new Label(i18n("settings.launcher.background.settings.opacity"));

                JFXSlider slider = new JFXSlider(0, 100, settings().backgroundOpacityProperty().get() * 100);
                slider.setShowTickMarks(true);
                slider.setMajorTickUnit(10);
                slider.setMinorTickCount(1);
                slider.setBlockIncrement(5);
                slider.setSnapToTicks(true);
                slider.setPadding(new Insets(9, 0, 0, 0));
                HBox.setHgrow(slider, Priority.ALWAYS);

                Label textOpacity = new Label();
                FXUtils.setLimitWidth(textOpacity, 50);

                StringBinding valueBinding = Bindings.createStringBinding(() -> ((int) slider.getValue()) + "%", slider.valueProperty());
                textOpacity.textProperty().bind(valueBinding);
                slider.setValueFactory(s -> valueBinding);

                slider.valueProperty().addListener((observable, oldValue, newValue) ->
                        settings().backgroundOpacityProperty().set(snapOpacity(newValue.doubleValue())));

                opacityItem.getChildren().setAll(label, slider, textOpacity);
            }

            backgroundSublist.getContent().setAll(backgroundItem);
            themeAppearanceList.getContent().addAll(backgroundSublist, opacityItem);
        }

        {
            LineToggleButton titleTransparentButton = new LineToggleButton();
            themeAppearanceList.getContent().add(titleTransparentButton);
            titleTransparentButton.selectedProperty().bindBidirectional(settings().titleTransparentProperty());
            titleTransparentButton.setTitle(i18n("settings.launcher.title_transparent"));
        }
        installThemeCustomizationListener(ignored -> {
            if (!applyingTheme && settings().themeProperty().get() != null) {
                settings().themeProperty().set(null);
            }
        });
        content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.theme_appearance")), themeAppearanceList);

        {
            ComponentList appearanceList = new ComponentList();

            LineToggleButton animationButton = new LineToggleButton();
            appearanceList.getContent().add(animationButton);
            animationButton.selectedProperty().bindBidirectional(settings().animationDisabledProperty());
            animationButton.setTitle(i18n("settings.launcher.turn_off_animations"));
            animationButton.setSubtitle(i18n("settings.take_effect_after_restart"));

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")), appearanceList);
        }

        {
            ComponentList fontPane = new ComponentList();

            {
                VBox vbox = new VBox();
                vbox.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    vbox.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(8);

                        FontComboBox cboFont = new FontComboBox();
                        cboFont.setValue(settings().launcherFontFamilyProperty().get());
                        FXUtils.onChange(cboFont.valueProperty(), FontManager::setFontFamily);

                        JFXButton clearButton = FXUtils.newToggleButton4(SVG.RESTORE);
                        clearButton.setOnAction(e -> cboFont.setValue(null));

                        FXUtils.installFastTooltip(clearButton, i18n("button.reset"));

                        hBox.getChildren().setAll(cboFont, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                vbox.getChildren().add(new Label("Hello Minecraft! Launcher"));

                fontPane.getContent().add(vbox);
            }

            {
                VBox logFontPane = new VBox();
                logFontPane.setSpacing(5);

                {
                    BorderPane borderPane = new BorderPane();
                    logFontPane.getChildren().add(borderPane);
                    {
                        Label left = new Label(i18n("settings.launcher.log.font"));
                        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
                        borderPane.setLeft(left);
                    }

                    {
                        HBox hBox = new HBox();
                        hBox.setSpacing(3);

                        FontComboBox cboLogFont = new FontComboBox();
                        cboLogFont.valueProperty().bindBidirectional(settings().logFontFamilyProperty());

                        JFXTextField txtLogFontSize = new JFXTextField();
                        FXUtils.setLimitWidth(txtLogFontSize, 50);
                        FXUtils.bind(txtLogFontSize, settings().logFontSizeProperty(), SafeStringConverter.fromFiniteDouble()
                                .restrict(it -> it > 0)
                                .fallbackTo(12.0)
                                .asPredicate(Validator.addTo(txtLogFontSize)));

                        JFXButton clearButton = FXUtils.newToggleButton4(SVG.RESTORE);
                        clearButton.setOnAction(e -> cboLogFont.setValue(null));

                        FXUtils.installFastTooltip(clearButton, i18n("button.reset"));

                        hBox.getChildren().setAll(cboLogFont, txtLogFontSize, clearButton);

                        borderPane.setRight(hBox);
                    }
                }

                Label lblLogFontDisplay = new Label("[23:33:33] [Client Thread/INFO] [WaterPower]: Loaded mod WaterPower.");
                lblLogFontDisplay.fontProperty().bind(Bindings.createObjectBinding(
                        () -> Font.font(Lang.requireNonNullElse(settings().logFontFamilyProperty().get(), FXUtils.DEFAULT_MONOSPACE_FONT), settings().logFontSizeProperty().get()),
                        settings().logFontFamilyProperty(), settings().logFontSizeProperty()));

                logFontPane.getChildren().add(lblLogFontDisplay);

                fontPane.getContent().add(logFontPane);
            }

            {
                var fontAntiAliasingPane = new LineSelectButton<Optional<FontSmoothingType>>();
                fontAntiAliasingPane.setTitle(i18n("settings.launcher.font.anti_aliasing"));
                fontAntiAliasingPane.setSubtitle(i18n("settings.take_effect_after_restart"));
                fontAntiAliasingPane.setNullSafeConverter(value ->
                        value.isPresent()
                                ? i18n("settings.launcher.font.anti_aliasing." + value.get().name().toLowerCase(Locale.ROOT))
                                : i18n("settings.launcher.font.anti_aliasing.auto")
                );
                fontAntiAliasingPane.setItems(
                        Optional.empty(),
                        Optional.of(FontSmoothingType.LCD),
                        Optional.of(FontSmoothingType.GRAY)
                );

                @Nullable String fontAntiAliasing = SettingsManager.userSettings().fontAntiAliasingProperty().get();
                if ("lcd".equalsIgnoreCase(fontAntiAliasing)) {
                    fontAntiAliasingPane.setValue(Optional.of(FontSmoothingType.LCD));
                } else if ("gray".equalsIgnoreCase(fontAntiAliasing)) {
                    fontAntiAliasingPane.setValue(Optional.of(FontSmoothingType.GRAY));
                } else {
                    fontAntiAliasingPane.setValue(Optional.empty());
                }
                fontAntiAliasingPane.setDisable(SettingsManager.isUserSettingsReadOnly());

                FXUtils.onChange(fontAntiAliasingPane.valueProperty(), value ->
                {
                    if (SettingsManager.isUserSettingsReadOnly()) {
                        return;
                    }
                    UserSettings userSettings = userSettings();
                    userSettings.fontAntiAliasingProperty().set(value.map(it -> it.name().toLowerCase(Locale.ROOT))
                                            .orElse(null));
                });

                fontPane.getContent().add(fontAntiAliasingPane);
            }

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.fonts")), fontPane);
        }
    }
}
