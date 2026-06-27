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
import javafx.beans.property.Property;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.css.PseudoClass;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;
import javafx.stage.FileChooser;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.uuid.UUIDs;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.setting.FontManager;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.setting.ThemeColorType;
import org.jackhuang.hmcl.setting.UserSettings;
import org.jackhuang.hmcl.theme.BackgroundLoadPolicy;
import org.jackhuang.hmcl.theme.BuiltinBackground;
import org.jackhuang.hmcl.theme.NetworkBackgroundImageCachePolicy;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.theme.ThemeColorSource;
import org.jackhuang.hmcl.theme.ThemePackExporter;
import org.jackhuang.hmcl.theme.ThemePackAuthor;
import org.jackhuang.hmcl.theme.ThemePackManifest;
import org.jackhuang.hmcl.theme.ThemePackManager;
import org.jackhuang.hmcl.theme.ThemeReference;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.userSettings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Configures launcher appearance, background, and launcher font settings.
@NotNullByDefault
public class PersonalizationPage extends StackPane {
    /// Property key for override-state button tooltips.
    private static final Object INHERIT_BUTTON_TOOLTIP_KEY = new Object();

    /// Pseudo class used when an appearance setting overrides the selected theme.
    private static final PseudoClass PSEUDO_OVERRIDDEN = PseudoClass.getPseudoClass("overridden");

    /// Style class shared with compact setting-state icon buttons.
    private static final String INHERIT_BUTTON_STYLE_CLASS = "toggle-icon-tiny";

    /// Size of compact setting-state icons.
    private static final int INHERIT_BUTTON_ICON_SIZE = 12;

    /// Time format used by default exported theme names.
    private static final DateTimeFormatter EXPORTED_THEME_NAME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

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

    /// Creates a compact button that toggles inherited or overridden state.
    private static JFXButton createThemeAppearanceOverrideButton() {
        var button = new JFXButton();
        button.getStyleClass().add(INHERIT_BUTTON_STYLE_CLASS);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        Tooltip tooltip = new Tooltip();
        button.getProperties().put(INHERIT_BUTTON_TOOLTIP_KEY, tooltip);
        FXUtils.installFastTooltip(button, tooltip);
        updateThemeAppearanceOverrideButton(button, true);
        return button;
    }

    /// Updates the icon and pseudo class of a theme appearance override button.
    private static void updateThemeAppearanceOverrideButton(JFXButton button, boolean inherited) {
        button.setGraphic((inherited ? SVG.STYLE : SVG.EDIT).createIcon(INHERIT_BUTTON_ICON_SIZE));
        button.pseudoClassStateChanged(PSEUDO_OVERRIDDEN, !inherited);

        Object tooltip = button.getProperties().get(INHERIT_BUTTON_TOOLTIP_KEY);
        if (tooltip instanceof Tooltip inheritTooltip) {
            inheritTooltip.setText(i18n(inherited
                    ? "theme_pack.appearance.follow_theme.tooltip"
                    : "theme_pack.appearance.custom.tooltip"));
        }
    }

    /// Adds listeners for state that can change effective inherited appearance values.
    private static void addThemeAppearanceRefreshListener(InvalidationListener listener) {
        settings().selectedThemeProperty().addListener(listener);
        settings().getThemeAppearanceOverrides().addListener(listener);
        settings().themeBrightnessModeProperty().addListener(listener);
        settings().backgroundTypeProperty().addListener(listener);
        if (FXUtils.DARK_MODE != null) {
            FXUtils.DARK_MODE.addListener(listener);
        }
    }

    /// Installs an inheritance button on a line select button.
    private static <T> void bindThemeAppearanceLineSelectButton(
            LineSelectButton<T> button,
            String setting,
            Property<T> directProperty,
            Supplier<T> effectiveValueSupplier) {
        JFXButton inheritButton = createThemeAppearanceOverrideButton();
        button.setTitleTrailing(inheritButton);

        Holder<Boolean> updating = new Holder<>(false);
        InvalidationListener refresh = ignored -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                boolean overridden = settings().getThemeAppearanceOverrides().contains(setting);
                button.setValue(overridden ? directProperty.getValue() : effectiveValueSupplier.get());
                updateThemeAppearanceOverrideButton(inheritButton, !overridden);
            } finally {
                updating.value = false;
            }
        };

        button.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                directProperty.setValue(newValue);
                settings().getThemeAppearanceOverrides().add(setting);
                updateThemeAppearanceOverrideButton(inheritButton, false);
            } finally {
                updating.value = false;
            }
        });
        directProperty.addListener(refresh);
        addThemeAppearanceRefreshListener(refresh);

        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!settings().getThemeAppearanceOverrides().contains(setting)) {
                directProperty.setValue(effectiveValueSupplier.get());
                settings().getThemeAppearanceOverrides().add(setting);
            } else {
                settings().getThemeAppearanceOverrides().remove(setting);
            }
            refresh.invalidated(null);
            event.consume();
        });
        refresh.invalidated(null);
    }

    /// Installs an inheritance button on a radio choice list inside a sublist.
    private static <T> void bindThemeAppearanceChoiceList(
            ComponentSublist sublist,
            RadioChoiceList<T> choiceList,
            String setting,
            Property<T> directProperty,
            Supplier<T> effectiveValueSupplier) {
        JFXButton inheritButton = createThemeAppearanceOverrideButton();
        sublist.setTitleRight(inheritButton);

        Holder<Boolean> updating = new Holder<>(false);
        InvalidationListener refresh = ignored -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                boolean overridden = settings().getThemeAppearanceOverrides().contains(setting);
                choiceList.selectedValueProperty().set(overridden
                        ? directProperty.getValue()
                        : effectiveValueSupplier.get());
                updateThemeAppearanceOverrideButton(inheritButton, !overridden);
            } finally {
                updating.value = false;
            }
        };

        choiceList.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                directProperty.setValue(newValue);
                settings().getThemeAppearanceOverrides().add(setting);
                updateThemeAppearanceOverrideButton(inheritButton, false);
            } finally {
                updating.value = false;
            }
        });
        directProperty.addListener(refresh);
        addThemeAppearanceRefreshListener(refresh);

        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!settings().getThemeAppearanceOverrides().contains(setting)) {
                directProperty.setValue(effectiveValueSupplier.get());
                settings().getThemeAppearanceOverrides().add(setting);
            } else {
                settings().getThemeAppearanceOverrides().remove(setting);
            }
            refresh.invalidated(null);
            event.consume();
        });
        refresh.invalidated(null);
    }

    /// Installs an inheritance button on a binary toggle button.
    private static <T> void bindThemeAppearanceToggleButton(
            LineToggleButton button,
            String setting,
            Property<T> directProperty,
            Supplier<T> effectiveValueSupplier,
            T selectedValue,
            T unselectedValue,
            @Nullable Function<T, String> valueConverter) {
        JFXButton inheritButton = createThemeAppearanceOverrideButton();
        button.setTitleTrailing(inheritButton);

        Holder<Boolean> updating = new Holder<>(false);
        InvalidationListener refresh = ignored -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                boolean overridden = settings().getThemeAppearanceOverrides().contains(setting);
                T value = overridden ? directProperty.getValue() : effectiveValueSupplier.get();
                button.setSelected(Objects.equals(value, selectedValue));
                button.setSubtitle(valueConverter != null ? valueConverter.apply(value) : null);
                updateThemeAppearanceOverrideButton(inheritButton, !overridden);
            } finally {
                updating.value = false;
            }
        };

        button.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (updating.value) {
                return;
            }
            updating.value = true;
            try {
                directProperty.setValue(Boolean.TRUE.equals(newValue) ? selectedValue : unselectedValue);
                settings().getThemeAppearanceOverrides().add(setting);
                updateThemeAppearanceOverrideButton(inheritButton, false);
            } finally {
                updating.value = false;
            }
            refresh.invalidated(null);
        });
        directProperty.addListener(refresh);
        addThemeAppearanceRefreshListener(refresh);

        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!settings().getThemeAppearanceOverrides().contains(setting)) {
                directProperty.setValue(button.isSelected() ? selectedValue : unselectedValue);
                settings().getThemeAppearanceOverrides().add(setting);
            } else {
                settings().getThemeAppearanceOverrides().remove(setting);
            }
            refresh.invalidated(null);
            event.consume();
        });
        refresh.invalidated(null);
    }

    /// Loads all theme choices shown by the theme selector.
    private static List<ThemeChoice> loadThemeChoices() {
        ArrayList<ThemeChoice> choices = new ArrayList<>();

        try {
            for (ThemePackManager.InstalledThemePack themePack : ThemePackManager.listInstalled()) {
                for (Theme theme : themePack.manifest().themes()) {
                    choices.add(ThemeChoice.installed(themePack, theme));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to load installed theme packs", e);
        }

        ThemeReference reference = settings().getSelectedThemeOrDefault();
        if (findThemeChoice(choices, reference) == null) {
            choices.add(ThemeChoice.missing(reference));
        }

        return choices;
    }

    /// Returns the choice that matches the current launcher theme reference.
    private static ThemeChoice getSelectedThemeChoice(List<ThemeChoice> choices) {
        ThemeReference reference = settings().getSelectedThemeOrDefault();
        @Nullable ThemeChoice choice = findThemeChoice(choices, reference);
        if (choice != null) {
            return choice;
        }

        return ThemeChoice.missing(reference);
    }

    /// Finds a selectable theme choice by theme reference.
    private static @Nullable ThemeChoice findThemeChoice(List<ThemeChoice> choices, ThemeReference reference) {
        for (ThemeChoice choice : choices) {
            if (Objects.equals(choice.reference(), reference)) {
                return choice;
            }
        }
        return null;
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

    /// Shows a theme-pack operation error dialog.
    private static void showThemePackError(String title, Exception exception) {
        Controllers.dialog(
                title + "\n\n" + StringUtils.getStackTrace(exception),
                i18n("message.error"),
                MessageType.ERROR);
    }

    /// A selectable launcher theme.
    ///
    /// @param title       the label shown by the selector
    /// @param description the optional secondary text shown in the selector popup
    /// @param themePack   the installed theme pack, or `null` for non-pack choices
    /// @param theme       the installed theme, or `null` for non-pack choices
    /// @param reference   the stored theme reference, or `null` for unresolved placeholder choices
    private record ThemeChoice(
            String title,
            @Nullable String description,
            @Nullable ThemePackManager.InstalledThemePack themePack,
            @Nullable Theme theme,
            @Nullable ThemeReference reference) {
        /// Creates a theme selector choice.
        private ThemeChoice {
            Objects.requireNonNull(title);
        }

        /// Creates a choice for an installed theme-pack theme.
        private static ThemeChoice installed(ThemePackManager.InstalledThemePack themePack, Theme theme) {
            ThemePackManifest manifest = themePack.manifest();
            String themeName = Objects.requireNonNullElse(theme.displayName(), manifest.displayName());
            String title = manifest.themes().size() == 1
                    ? manifest.displayName()
                    : manifest.displayName() + " - " + themeName;

            @Nullable String description = theme.displayDescription();
            if (StringUtils.isBlank(description)) {
                List<ThemePackAuthor> authors = theme.authors().isEmpty() ? manifest.authors() : theme.authors();
                String authorNames = authors.stream()
                        .map(ThemePackAuthor::displayName)
                        .filter(author -> !StringUtils.isBlank(author))
                        .collect(Collectors.joining(", "));
                if (!StringUtils.isBlank(authorNames)) {
                    description = i18n("archive.author") + ": " + authorNames;
                }
            }
            if (StringUtils.isBlank(description)) {
                description = manifest.displayDescription();
            }
            if (StringUtils.isBlank(description)) {
                description = manifest.id();
            }

            ThemeReference reference = new ThemeReference(
                    manifest.id(),
                    theme.id());
            return new ThemeChoice(
                    title,
                    description,
                    themePack,
                    theme,
                    reference);
        }

        /// Creates the placeholder shown when the stored theme reference cannot be resolved.
        private static ThemeChoice missing(ThemeReference reference) {
            return new ThemeChoice(
                    i18n("theme_pack.current.missing"),
                    reference.themeId() == null
                            ? reference.packId()
                            : reference.packId() + " - " + reference.themeId(),
                    null,
                    null,
                    reference);
        }

    }

    /// Asks for theme-pack metadata and then saves the current launcher appearance as a theme-pack file.
    private void exportCurrentThemePack() {
        Instant exportTimestamp = Instant.now();
        String defaultPackId = "com.example.hmcl.theme-pack." + UUIDs.toCompactString(UUIDs.generateV7(exportTimestamp));
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
                StringUtils.isBlank(packIdQuestion.getValue()) ? defaultPackId : packIdQuestion.getValue().trim(),
                StringUtils.isBlank(packNameQuestion.getValue()) ? defaultPackName : packNameQuestion.getValue().trim(),
                StringUtils.isBlank(authorNameQuestion.getValue()) ? defaultAuthorName : authorNameQuestion.getValue().trim()));
    }

    /// Saves current launcher appearance as a theme-pack file with the given package metadata.
    private void exportCurrentThemePack(String packId, String packName, String authorName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("theme_pack.export.title"));
        String initialFileName = sanitizeThemePackFileName(packName) + ThemePackExporter.FILE_EXTENSION;
        chooser.setInitialFileName(FileUtils.isNameValid(initialFileName)
                ? initialFileName
                : "theme-pack" + ThemePackExporter.FILE_EXTENSION);
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(i18n("theme_pack.file"), "*" + ThemePackExporter.FILE_EXTENSION));

        @Nullable Path output = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (output == null) {
            return;
        }

        String fileName = output.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(ThemePackExporter.FILE_EXTENSION)) {
            output = output.resolveSibling(fileName + ThemePackExporter.FILE_EXTENSION);
        }
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
            Holder<List<ThemeChoice>> themeChoices = new Holder<>(List.of());
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
                    if (choice.themePack() != null && choice.theme() != null) {
                        if (Themes.shouldWaitForThemeBackground()) {
                            Controllers.taskDialog(
                                    Themes.applyTheme(choice.themePack(), choice.theme()),
                                    i18n("launcher.background.loading"),
                                    TaskCancellationAction.NO_CANCEL);
                        } else {
                            ThemePackManager.apply(choice.themePack(), choice.theme());
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    showThemePackError(i18n("theme_pack.apply.failed"), e);
                } finally {
                    refreshSelectedTheme.run();
                }
            });
            FXUtils.onChange(settings().selectedThemeProperty(), ignored -> reloadThemeChoices.run());
            reloadThemeChoices.run();
            themeList.getContent().add(themeSelectButton);

            LineButton manageThemeButton = LineButton.createNavigationButton();
            manageThemeButton.setTitle(i18n("theme_pack.manage"));
            manageThemeButton.setOnAction(event -> Controllers.navigateForward(new ThemePackManagementPage(reloadThemeChoices)));
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
        ComponentList backgroundLoadingList = new ComponentList();

        {
            LineSelectButton<String> brightnessPane = new LineSelectButton<>();
            brightnessPane.setTitle(i18n("settings.launcher.brightness"));
            brightnessPane.setConverter(name -> i18n("settings.launcher.brightness."
                    + Objects.requireNonNullElse(name, "auto")));
            brightnessPane.setItems(Arrays.asList("auto", "light", "dark"));
            bindThemeAppearanceLineSelectButton(
                    brightnessPane,
                    LauncherSettings.THEME_APPEARANCE_BRIGHTNESS_MODE,
                    settings().themeBrightnessModeProperty(),
                    () -> {
                        try {
                            @Nullable Brightness brightness = ThemePackManager.resolveCurrentThemeBrightness(
                                    ThemePackManager.currentResolveContext());
                            return brightness == null
                                    ? "auto"
                                    : brightness == Brightness.DARK ? "dark" : "light";
                        } catch (IOException | RuntimeException e) {
                            return "auto";
                        }
                    });

            themeAppearanceList.getContent().add(brightnessPane);
        }

        {
            ComponentSublist themeColorSublist = new ComponentSublist();
            themeColorSublist.setTitle(i18n("settings.launcher.theme_color"));
            themeColorSublist.setHasSubtitle(true);
            Supplier<ThemeColorType> effectiveThemeColorType = () -> {
                try {
                    @Nullable ThemeColorSource source = ThemePackManager.resolveCurrentThemeColorSource(
                            ThemePackManager.currentResolveContext());
                    if (source instanceof ThemeColorSource.Wallpaper) {
                        return ThemeColorType.BACKGROUND;
                    }
                    if (source instanceof ThemeColorSource.Custom) {
                        return ThemeColorType.CUSTOM;
                    }
                    return ThemeColorType.DEFAULT;
                } catch (IOException | RuntimeException e) {
                    return ThemeColorType.DEFAULT;
                }
            };
            themeColorSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                        ThemeColorType type = settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_COLOR)
                                ? Objects.requireNonNullElse(settings().themeColorTypeProperty().get(), ThemeColorType.DEFAULT)
                                : effectiveThemeColorType.get();
                        return i18n("settings.launcher.theme_color_type." + type.name().toLowerCase(Locale.ROOT));
                    },
                    settings().themeColorTypeProperty(),
                    settings().getThemeAppearanceOverrides(),
                    settings().selectedThemeProperty(),
                    settings().themeBrightnessModeProperty()));

            ColorPicker picker = new JFXColorPicker();
            picker.getCustomColors().setAll(ThemeColor.STANDARD_COLORS.stream().map(ThemeColor::color).toList());
            ThemeColor.bindBidirectional(picker, settings().customThemeColorProperty());
            Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));

            var defaultColorChoice = new RadioChoiceList.Choice<ThemeColorType>(
                    i18n("settings.launcher.theme_color_type.default"),
                    ThemeColorType.DEFAULT);

            var customColorChoice = new RadioChoiceList.Choice<ThemeColorType>(
                    i18n("settings.launcher.theme_color_type.custom"),
                    ThemeColorType.CUSTOM) {
                @Override
                protected Node createRightNode() {
                    return picker;
                }
            };
            customColorChoice.setSubtitle(i18n("settings.launcher.theme_color_type.custom.description"));

            var backgroundColorChoice = new RadioChoiceList.Choice<ThemeColorType>(
                    i18n("settings.launcher.theme_color_type.background"),
                    ThemeColorType.BACKGROUND);

            RadioChoiceList<ThemeColorType> themeColorChoiceList = new RadioChoiceList<>();
            themeColorChoiceList.setFallbackValue(ThemeColorType.DEFAULT);
            themeColorChoiceList.setChoices(Arrays.asList(defaultColorChoice, customColorChoice, backgroundColorChoice));
            bindThemeAppearanceChoiceList(
                    themeColorSublist,
                    themeColorChoiceList,
                    LauncherSettings.THEME_APPEARANCE_COLOR,
                    settings().themeColorTypeProperty(),
                    effectiveThemeColorType);

            themeColorSublist.getContent().setAll(themeColorChoiceList);
            themeAppearanceList.getContent().add(themeColorSublist);
        }

        {
            LineSelectButton<ColorStyle> colorStylePane = new LineSelectButton<>();
            colorStylePane.setTitle(i18n("settings.launcher.theme_color_style"));
            colorStylePane.setConverter(style -> i18n("settings.launcher.theme_color_style."
                    + Objects.requireNonNullElse(style, ColorStyle.FIDELITY).name().toLowerCase(Locale.ROOT)));
            colorStylePane.setDescriptionConverter(style -> i18n("settings.launcher.theme_color_style."
                    + Objects.requireNonNullElse(style, ColorStyle.FIDELITY).name().toLowerCase(Locale.ROOT)
                    + ".desc"));
            colorStylePane.setItems(Arrays.asList(
                    ColorStyle.FIDELITY,
                    ColorStyle.TONAL_SPOT,
                    ColorStyle.VIBRANT,

                    ColorStyle.NEUTRAL,

                    ColorStyle.FRUIT_SALAD,
                    ColorStyle.RAINBOW
            ));
            bindThemeAppearanceLineSelectButton(
                    colorStylePane,
                    LauncherSettings.THEME_APPEARANCE_COLOR_STYLE,
                    settings().themeColorStyleProperty(),
                    () -> {
                        try {
                            return Objects.requireNonNullElse(
                                    ThemePackManager.resolveCurrentThemeColorStyle(ThemePackManager.currentResolveContext()),
                                    ColorStyle.FIDELITY);
                        } catch (IOException | RuntimeException e) {
                            return ColorStyle.FIDELITY;
                        }
                    });

            themeAppearanceList.getContent().add(colorStylePane);
        }

        {
            RadioChoiceList<BackgroundType> backgroundItem = new RadioChoiceList<>();
            backgroundItem.setFallbackValue(BackgroundType.DEFAULT);
            ComponentSublist backgroundSublist = new ComponentSublist();
            backgroundSublist.setTitle(i18n("launcher.background"));
            backgroundSublist.setHasSubtitle(true);

            JFXComboBox<String> builtinBackgroundComboBox = new JFXComboBox<>();
            builtinBackgroundComboBox.getItems().setAll(BuiltinBackground.BUILTIN_BACKGROUND_IDS);
            builtinBackgroundComboBox.valueProperty().bindBidirectional(settings().builtinBackgroundIdProperty());
            FXUtils.setLimitWidth(builtinBackgroundComboBox, 160);

            RadioChoiceList.Choice<BackgroundType> builtinBackgroundOption =
                    new RadioChoiceList.Choice<>(i18n("launcher.background.builtin"), BackgroundType.BUILTIN) {
                        @Override
                        protected Node createRightNode() {
                            return builtinBackgroundComboBox;
                        }
                    };

            backgroundItem.setChoices(Arrays.asList(
                    new RadioChoiceList.Choice<>(i18n("message.default"), BackgroundType.DEFAULT)
                            .setTooltip(i18n("launcher.background.default.tooltip")),
                    builtinBackgroundOption,
                    new RadioChoiceList.Choice<>(i18n("launcher.background.theme_color"), BackgroundType.THEME_COLOR),
                    new RadioChoiceList.FileChoice<>(i18n("settings.custom"), BackgroundType.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .setSelectionMode(FileSelector.SelectionMode.FILE_OR_DIRECTORY)
                            .bindPathBidirectional(settings().customBackgroundImagePathProperty()),
                    new RadioChoiceList.TextChoice<>(i18n("launcher.background.network"), BackgroundType.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindTextBidirectional(settings().networkBackgroundImageUrlProperty()),
                    new PaintChoice(i18n("launcher.background.paint"), BackgroundType.PAINT, settings().customBackgroundPaintProperty())
            ));
            backgroundItem.selectedValueProperty().bindBidirectional(settings().backgroundTypeProperty());
            JFXButton backgroundOverrideButton = createThemeAppearanceOverrideButton();
            backgroundSublist.setTitleRight(backgroundOverrideButton);
            InvalidationListener refreshBackgroundOverride = ignored -> updateThemeAppearanceOverrideButton(
                    backgroundOverrideButton,
                    !settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            addThemeAppearanceRefreshListener(refreshBackgroundOverride);
            backgroundItem.selectedValueProperty().addListener((observable, oldValue, newValue) ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            settings().builtinBackgroundIdProperty().addListener(ignored ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            settings().customBackgroundImagePathProperty().addListener(ignored ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            settings().networkBackgroundImageUrlProperty().addListener(ignored ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            settings().customBackgroundPaintProperty().addListener(ignored ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND));
            backgroundOverrideButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (!settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND)) {
                    try {
                        ThemePackManager.ResolvedBackground background =
                                ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext());
                        settings().backgroundTypeProperty().set(background.type());
                        switch (background.type()) {
                            case BUILTIN -> settings().builtinBackgroundIdProperty().set(Objects.requireNonNullElse(
                                    background.builtinBackgroundId(),
                                    BuiltinBackground.FALLBACK.id()));
                            case CUSTOM -> {
                                if (background.imagePath() != null) {
                                    settings().customBackgroundImagePathProperty().set(background.imagePath().toString());
                                } else {
                                    settings().backgroundTypeProperty().set(BackgroundType.DEFAULT);
                                }
                            }
                            case NETWORK -> {
                                settings().networkBackgroundImageUrlProperty().set(background.networkImageUrl());
                                settings().networkBackgroundImageCachePolicyProperty().set(Objects.requireNonNullElse(
                                        background.networkImageCachePolicy(),
                                        NetworkBackgroundImageCachePolicy.ENABLED));
                            }
                            case PAINT -> settings().customBackgroundPaintProperty().set(background.paint());
                            case DEFAULT, THEME_COLOR -> {
                            }
                        }
                    } catch (IOException | RuntimeException e) {
                        settings().backgroundTypeProperty().set(BackgroundType.DEFAULT);
                    }
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND);
                } else {
                    settings().getThemeAppearanceOverrides().remove(LauncherSettings.THEME_APPEARANCE_BACKGROUND);
                }
                refreshBackgroundOverride.invalidated(null);
                event.consume();
            });
            refreshBackgroundOverride.invalidated(null);

            LineToggleButton networkBackgroundCacheButton = new LineToggleButton();
            networkBackgroundCacheButton.setTitle(i18n("launcher.background.network.cache"));
            bindThemeAppearanceToggleButton(
                    networkBackgroundCacheButton,
                    LauncherSettings.THEME_APPEARANCE_NETWORK_BACKGROUND_IMAGE_CACHE_POLICY,
                    settings().networkBackgroundImageCachePolicyProperty(),
                    () -> {
                        try {
                            ThemePackManager.ResolvedBackground background =
                                    ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext());
                            if (background.type() == BackgroundType.NETWORK) {
                                return Objects.requireNonNullElse(
                                        background.networkImageCachePolicy(),
                                        NetworkBackgroundImageCachePolicy.ENABLED);
                            }
                        } catch (IOException | RuntimeException ignored) {
                        }
                        return NetworkBackgroundImageCachePolicy.ENABLED;
                    },
                    NetworkBackgroundImageCachePolicy.ENABLED,
                    NetworkBackgroundImageCachePolicy.DISABLED,
                    null);

            ComponentSublist backgroundFallbackSublist = new ComponentSublist();
            backgroundFallbackSublist.setTitle(i18n("launcher.background.fallback"));
            backgroundFallbackSublist.setHasSubtitle(true);

            RadioChoiceList<BackgroundType> backgroundFallbackItem = new RadioChoiceList<>();
            backgroundFallbackItem.setFallbackValue(BackgroundType.BUILTIN);
            backgroundFallbackItem.setChoices(Arrays.asList(
                    new RadioChoiceList.Choice<>(i18n("launcher.background.fallback.builtin"), BackgroundType.BUILTIN),
                    new RadioChoiceList.Choice<>(i18n("launcher.background.fallback.theme_color"), BackgroundType.THEME_COLOR),
                    new PaintChoice(i18n("launcher.background.fallback.paint"), BackgroundType.PAINT, settings().backgroundFallbackPaintProperty())
            ));
            backgroundFallbackItem.selectedValueProperty().bindBidirectional(settings().backgroundFallbackTypeProperty());
            JFXButton backgroundFallbackOverrideButton = createThemeAppearanceOverrideButton();
            backgroundFallbackSublist.setTitleRight(backgroundFallbackOverrideButton);
            InvalidationListener refreshBackgroundFallbackOverride = ignored -> updateThemeAppearanceOverrideButton(
                    backgroundFallbackOverrideButton,
                    !settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK));
            addThemeAppearanceRefreshListener(refreshBackgroundFallbackOverride);
            backgroundFallbackItem.selectedValueProperty().addListener((observable, oldValue, newValue) ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK));
            settings().backgroundFallbackPaintProperty().addListener(ignored ->
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK));
            backgroundFallbackOverrideButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (!settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK)) {
                    try {
                        ThemePackManager.ResolvedBackground background =
                                ThemePackManager.resolveCurrentBackgroundFallback(ThemePackManager.currentResolveContext());
                        switch (background.type()) {
                            case PAINT -> {
                                settings().backgroundFallbackTypeProperty().set(BackgroundType.PAINT);
                                settings().backgroundFallbackPaintProperty().set(Objects.requireNonNullElse(
                                        background.paint(),
                                        Color.WHITE));
                            }
                            case THEME_COLOR ->
                                    settings().backgroundFallbackTypeProperty().set(BackgroundType.THEME_COLOR);
                            default -> settings().backgroundFallbackTypeProperty().set(BackgroundType.BUILTIN);
                        }
                    } catch (IOException | RuntimeException e) {
                        settings().backgroundFallbackTypeProperty().set(BackgroundType.BUILTIN);
                    }
                    settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK);
                } else {
                    settings().getThemeAppearanceOverrides().remove(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK);
                }
                refreshBackgroundFallbackOverride.invalidated(null);
                event.consume();
            });
            refreshBackgroundFallbackOverride.invalidated(null);
            backgroundFallbackSublist.getContent().setAll(backgroundFallbackItem);
            backgroundFallbackSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                        if (settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_FALLBACK)) {
                            BackgroundType type = backgroundFallbackItem.selectedValueProperty().get();
                            return switch (type) {
                                case PAINT -> {
                                    Paint backgroundFallbackPaint = settings().backgroundFallbackPaintProperty().get();
                                    yield backgroundFallbackPaint.toString();
                                }
                                case THEME_COLOR -> i18n("launcher.background.fallback.theme_color");
                                default -> i18n("launcher.background.fallback.builtin");
                            };
                        }

                        try {
                            ThemePackManager.ResolvedBackground background =
                                    ThemePackManager.resolveCurrentBackgroundFallback(ThemePackManager.currentResolveContext());
                            return switch (background.type()) {
                                case PAINT -> background.paint() != null
                                        ? background.paint().toString()
                                        : i18n("launcher.background.fallback.paint");
                                case THEME_COLOR -> i18n("launcher.background.fallback.theme_color");
                                default -> i18n("launcher.background.fallback.builtin");
                            };
                        } catch (IOException | RuntimeException e) {
                            return i18n("launcher.background.fallback.builtin");
                        }
                    },
                    backgroundFallbackItem.selectedValueProperty(),
                    settings().getThemeAppearanceOverrides(),
                    settings().selectedThemeProperty(),
                    settings().themeBrightnessModeProperty(),
                    settings().backgroundFallbackPaintProperty()));

            LineToggleButton backgroundLoadPolicyButton = new LineToggleButton();
            backgroundLoadPolicyButton.setTitle(i18n("launcher.background.load_policy.show_fallback_while_loading"));
            bindThemeAppearanceToggleButton(
                    backgroundLoadPolicyButton,
                    LauncherSettings.THEME_APPEARANCE_BACKGROUND_LOAD_POLICY,
                    settings().backgroundLoadPolicyProperty(),
                    () -> {
                        try {
                            return ThemePackManager.resolveCurrentBackgroundLoadPolicy();
                        } catch (RuntimeException e) {
                            return BackgroundLoadPolicy.WAIT_FOR_BACKGROUND;
                        }
                    },
                    BackgroundLoadPolicy.SHOW_FALLBACK_WHILE_LOADING,
                    BackgroundLoadPolicy.WAIT_FOR_BACKGROUND,
                    policy -> i18n("launcher.background.load_policy."
                            + Objects.requireNonNullElse(policy, BackgroundLoadPolicy.WAIT_FOR_BACKGROUND)
                            .name()
                            .toLowerCase(Locale.ROOT)));

            backgroundSublist.descriptionProperty().bind(Bindings.createStringBinding(() -> {
                        if (settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND)) {
                            BackgroundType type = backgroundItem.selectedValueProperty().get();
                            return switch (type) {
                                case DEFAULT -> i18n("message.default");
                                case THEME_COLOR -> i18n("launcher.background.theme_color");
                                case BUILTIN -> {
                                    String id = settings().builtinBackgroundIdProperty().get();
                                    yield BuiltinBackground.fromIdOrFallback(id).id();
                                }
                                case CUSTOM -> settings().customBackgroundImagePathProperty().get();
                                case NETWORK -> settings().networkBackgroundImageUrlProperty().get();
                                case PAINT -> {
                                    @Nullable Paint customBackgroundPaint = settings().customBackgroundPaintProperty().get();
                                    yield customBackgroundPaint != null ? customBackgroundPaint.toString() : i18n("launcher.background.paint");
                                }
                            };
                        }

                        try {
                            ThemePackManager.ResolvedBackground background =
                                    ThemePackManager.resolveCurrentBackground(ThemePackManager.currentResolveContext());
                            return switch (background.type()) {
                                case DEFAULT -> i18n("message.default");
                                case BUILTIN -> Objects.requireNonNullElse(
                                        background.builtinBackgroundId(),
                                        BuiltinBackground.FALLBACK.id());
                                case CUSTOM -> background.imagePath() != null
                                        ? background.imagePath().toString()
                                        : background.imageResource() != null
                                          ? background.imageResource().name()
                                          : i18n("settings.custom");
                                case NETWORK ->
                                        Objects.toString(background.networkImageUrl(), i18n("launcher.background.network"));
                                case PAINT -> background.paint() != null
                                        ? background.paint().toString()
                                        : i18n("launcher.background.paint");
                                case THEME_COLOR -> i18n("launcher.background.theme_color");
                            };
                        } catch (IOException | RuntimeException e) {
                            return i18n("message.default");
                        }
                    },
                    backgroundItem.selectedValueProperty(),
                    settings().getThemeAppearanceOverrides(),
                    settings().selectedThemeProperty(),
                    settings().themeBrightnessModeProperty(),
                    settings().builtinBackgroundIdProperty(),
                    settings().customBackgroundImagePathProperty(),
                    settings().networkBackgroundImageUrlProperty(),
                    settings().customBackgroundPaintProperty()));

            LinePane opacityPane = new LinePane();
            {
                opacityPane.setTitle(i18n("settings.launcher.background.settings.opacity"));
                Supplier<Double> effectiveBackgroundOpacity = () -> {
                    try {
                        return ThemePackManager.resolveCurrentBackground(
                                ThemePackManager.currentResolveContext()).opacity();
                    } catch (IOException | RuntimeException e) {
                        return 1.0;
                    }
                };

                HBox sliderBox = new HBox(8);
                sliderBox.setAlignment(Pos.CENTER);

                JFXSlider slider = new JFXSlider(0, 100, settings().backgroundOpacityProperty().get() * 100);
                slider.setPrefWidth(220);
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
                sliderBox.getChildren().setAll(slider, textOpacity);

                JFXButton inheritButton = createThemeAppearanceOverrideButton();
                opacityPane.setTitleTrailing(inheritButton);
                Holder<Boolean> updatingOpacity = new Holder<>(false);
                InvalidationListener refreshOpacity = ignored -> {
                    if (updatingOpacity.value) {
                        return;
                    }
                    updatingOpacity.value = true;
                    try {
                        boolean overridden = settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY);
                        double opacity = overridden
                                ? settings().backgroundOpacityProperty().get()
                                : effectiveBackgroundOpacity.get();
                        slider.setValue(Math.round(Math.max(0., Math.min(opacity, 1.)) * 100.));
                        updateThemeAppearanceOverrideButton(inheritButton, !overridden);
                    } finally {
                        updatingOpacity.value = false;
                    }
                };
                settings().backgroundOpacityProperty().addListener(refreshOpacity);
                addThemeAppearanceRefreshListener(refreshOpacity);
                slider.valueProperty().addListener((observable, oldValue, newValue) -> {
                    if (updatingOpacity.value) {
                        return;
                    }
                    double opacity = snapOpacity(newValue.doubleValue());
                    updatingOpacity.value = true;
                    try {
                        settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY);
                        updateThemeAppearanceOverrideButton(inheritButton, false);
                    } finally {
                        updatingOpacity.value = false;
                    }
                    if (Double.compare(settings().backgroundOpacityProperty().get(), opacity) != 0) {
                        settings().backgroundOpacityProperty().set(opacity);
                    }
                });
                inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                    if (!settings().getThemeAppearanceOverrides().contains(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY)) {
                        settings().backgroundOpacityProperty().set(effectiveBackgroundOpacity.get());
                        settings().getThemeAppearanceOverrides().add(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY);
                    } else {
                        settings().getThemeAppearanceOverrides().remove(LauncherSettings.THEME_APPEARANCE_BACKGROUND_OPACITY);
                    }
                    refreshOpacity.invalidated(null);
                    event.consume();
                });

                opacityPane.setRight(sliderBox);
                refreshOpacity.invalidated(null);
            }

            backgroundSublist.getContent().setAll(backgroundItem);
            themeAppearanceList.getContent().addAll(
                    backgroundSublist,
                    opacityPane);
            backgroundLoadingList.getContent().setAll(
                    networkBackgroundCacheButton,
                    backgroundFallbackSublist,
                    backgroundLoadPolicyButton
            );
        }

        {
            LineToggleButton titleBarTransparentButton = new LineToggleButton();
            titleBarTransparentButton.setTitle(i18n("settings.launcher.title_transparent"));
            bindThemeAppearanceToggleButton(
                    titleBarTransparentButton,
                    LauncherSettings.THEME_APPEARANCE_TITLE_BAR_TRANSPARENT,
                    settings().titleBarTransparentProperty(),
                    () -> {
                        try {
                            return Objects.requireNonNullElse(
                                    ThemePackManager.resolveCurrentTitleBarTransparent(
                                            ThemePackManager.currentResolveContext()),
                                    false);
                        } catch (IOException | RuntimeException e) {
                            return false;
                        }
                    },
                    Boolean.TRUE,
                    Boolean.FALSE,
                    null);
            themeAppearanceList.getContent().add(titleBarTransparentButton);
        }
        content.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.launcher.appearance")),
                themeAppearanceList,
                ComponentList.createComponentListTitle(i18n("launcher.background.loading")),
                backgroundLoadingList);

        {
            ComponentList appearanceList = new ComponentList();

            LineToggleButton animationButton = new LineToggleButton();
            appearanceList.getContent().add(animationButton);
            animationButton.selectedProperty().bindBidirectional(settings().animationDisabledProperty());
            animationButton.setTitle(i18n("settings.launcher.turn_off_animations"));
            animationButton.setSubtitle(i18n("settings.take_effect_after_restart"));

            content.getChildren().addAll(ComponentList.createComponentListTitle(i18n("settings.launcher.animation")), appearanceList);
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

    /// Choice with a right-side paint picker.
    private static final class PaintChoice extends RadioChoiceList.Choice<BackgroundType> {
        /// The paint picker owned by this option.
        private final ColorPicker colorPicker = new JFXColorPicker();

        /// Creates a paint choice.
        ///
        /// @param title    the choice title
        /// @param value    the selected background type
        /// @param property the paint property edited by this choice
        private PaintChoice(String title, BackgroundType value, Property<Paint> property) {
            super(title, value);
            FXUtils.bindPaint(colorPicker, property);
        }

        /// Creates the right-side paint picker.
        @Override
        protected Node createRightNode() {
            return colorPicker;
        }
    }
}
