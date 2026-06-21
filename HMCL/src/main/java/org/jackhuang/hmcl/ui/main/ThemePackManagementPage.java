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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.theme.ThemePackExporter;
import org.jackhuang.hmcl.theme.ThemePackManager;
import org.jackhuang.hmcl.theme.ThemePackManifest;
import org.jackhuang.hmcl.theme.ThemeSelection;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Shows installed theme packs and common theme-pack actions.
@NotNullByDefault
public final class ThemePackManagementPage extends ListPageBase<ThemePackManager.InstalledThemePack> implements DecoratorPage {
    /// The decorator title state for this page.
    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.fromTitle(i18n("theme_pack.manage")));

    /// Creates the theme-pack management page and loads installed packages.
    public ThemePackManagementPage() {
        setOnFailedAction(event -> refreshThemePacks());
        refreshThemePacks();
    }

    /// Creates the default list skin for the management page.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new ThemePackManagementPageSkin(this);
    }

    /// Returns the decorator title state for this page.
    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    /// Returns the file chooser filter for HMCL theme-pack files.
    private static FileChooser.ExtensionFilter getThemePackExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("theme_pack.file"), "*" + ThemePackExporter.FILE_EXTENSION);
    }

    /// Reloads installed theme packs from disk.
    private void refreshThemePacks() {
        try {
            List<ThemePackManager.InstalledThemePack> themePacks = ThemePackManager.listInstalled();
            setItems(FXCollections.observableArrayList(themePacks));
            setFailedReason(themePacks.isEmpty() ? i18n("theme_pack.empty") : null);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to load installed theme packs", e);
            setItems(FXCollections.observableArrayList());
            setFailedReason(i18n("theme_pack.load.failed"));
        }
    }

    /// Opens the managed theme-pack directory in the platform file manager.
    private void openThemePackDirectory() {
        FXUtils.openFolder(ThemePackManager.THEME_PACKS_DIRECTORY);
    }

    /// Opens a theme-pack file, installs it, and applies one theme from it.
    private void importThemePack() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("theme_pack.import.title"));
        chooser.getExtensionFilters().setAll(getThemePackExtensionFilter());

        @Nullable Path file = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
        if (file == null) {
            return;
        }

        ThemePackManager.InstalledThemePack themePack;
        try {
            themePack = ThemePackManager.install(file);
            refreshThemePacks();
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.import.failed"), e);
            return;
        }

        selectAndApplyThemePack(themePack);
    }

    /// Prompts for a theme when needed and applies it.
    private void selectAndApplyThemePack(ThemePackManager.InstalledThemePack themePack) {
        List<Theme> themes = themePack.manifest().themes();
        if (themes.size() == 1) {
            applyThemePack(themePack, themes.get(0));
            return;
        }

        ThemePackManifest manifest = themePack.manifest();
        String[] themeNames = themes.stream()
                .map(theme -> getThemeDisplayName(manifest, theme))
                .toArray(String[]::new);
        PromptDialogPane.Builder.CandidatesQuestion question =
                new PromptDialogPane.Builder.CandidatesQuestion(i18n("theme_pack.select.theme"), themeNames);
        Controllers.prompt(new PromptDialogPane.Builder(i18n("theme_pack.select"), (questions, handler) -> handler.resolve())
                .addQuestion(question)).thenAccept(questions -> {
                    int selectedIndex = ((PromptDialogPane.Builder.CandidatesQuestion) questions.get(0)).getValue();
                    applyThemePack(themePack, themes.get(selectedIndex));
                });
    }

    /// Applies a selected theme and reports the result.
    private void applyThemePack(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        try {
            ThemePackManager.apply(themePack, theme);
            refreshThemePacks();
            Controllers.showToast(i18n("theme_pack.apply.success", getThemeDisplayName(themePack.manifest(), theme)));
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.apply.failed"), e);
        }
    }

    /// Shows the installed package directory in the platform file manager.
    private static void revealThemePack(ThemePackManager.InstalledThemePack themePack) {
        FXUtils.showFileInExplorer(themePack.directory());
    }

    /// Asks for confirmation and deletes an installed theme pack.
    private void deleteThemePack(ThemePackManager.InstalledThemePack themePack) {
        Controllers.confirm(
                i18n("theme_pack.delete.confirm", themePack.manifest().name()),
                i18n("theme_pack.delete"),
                MessageType.WARNING,
                () -> deleteThemePackConfirmed(themePack),
                null);
    }

    /// Deletes an installed theme pack after confirmation.
    private void deleteThemePackConfirmed(ThemePackManager.InstalledThemePack themePack) {
        try {
            ThemePackManager.uninstall(themePack);
            refreshThemePacks();
            Controllers.showToast(i18n("theme_pack.delete.success", themePack.manifest().name()));
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.delete.failed"), e);
        }
    }

    /// Returns whether a package contains the currently selected launcher theme.
    private static boolean isCurrentThemePack(ThemePackManager.InstalledThemePack themePack) {
        @Nullable ThemeSelection selection = settings().themeProperty().get();
        ThemePackManifest manifest = themePack.manifest();
        return selection != null
                && selection.packId().equals(manifest.id())
                && selection.version().equals(manifest.version());
    }

    /// Returns a display name for one theme-pack theme.
    private static String getThemeDisplayName(ThemePackManifest manifest, Theme theme) {
        String name = theme.name() != null ? theme.name() : manifest.name();
        if (StringUtils.isBlank(theme.description())) {
            return name;
        }
        return name + " - " + theme.description();
    }

    /// Returns a subtitle for one installed theme pack.
    private static String getThemePackSubtitle(ThemePackManifest manifest) {
        if (StringUtils.isBlank(manifest.description())) {
            return manifest.id();
        }
        return manifest.description();
    }

    /// Shows a theme-pack operation error dialog.
    private static void showThemePackError(String title, Exception exception) {
        Controllers.dialog(
                title + "\n\n" + StringUtils.getStackTrace(exception),
                i18n("message.error"),
                MessageType.ERROR);
    }

    /// Skin for the theme-pack management list page.
    @NotNullByDefault
    private static final class ThemePackManagementPageSkin extends ToolbarListPageSkin<ThemePackManager.InstalledThemePack, ThemePackManagementPage> {
        /// Creates the management page skin.
        ///
        /// @param skinnable the page controlled by this skin
        private ThemePackManagementPageSkin(ThemePackManagementPage skinnable) {
            super(skinnable);
        }

        /// Creates toolbar buttons for page-level theme-pack actions.
        @Override
        protected List<Node> initializeToolbar(ThemePackManagementPage skinnable) {
            ArrayList<Node> result = new ArrayList<>(3);
            result.add(createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshThemePacks));
            result.add(createToolbarButton2(i18n("theme_pack.import"), SVG.FILE_OPEN, skinnable::importThemePack));
            result.add(createToolbarButton2(i18n("theme_pack.directory"), SVG.FOLDER_OPEN, skinnable::openThemePackDirectory));
            return result;
        }

        /// Creates a list cell for one installed theme pack.
        @Override
        protected ListCell<ThemePackManager.InstalledThemePack> createListCell(JFXListView<ThemePackManager.InstalledThemePack> listView) {
            return new ThemePackItemCell(getSkinnable(), listView);
        }
    }

    /// List cell that renders one installed theme pack and its actions.
    @NotNullByDefault
    private static final class ThemePackItemCell extends ListCell<ThemePackManager.InstalledThemePack> {
        /// The root node reused by this cell.
        private final Node graphic;

        /// The text content shown for the current theme pack.
        private final TwoLineListItem content;

        /// The tooltip shown on the apply button.
        private final Tooltip applyTooltip = new Tooltip();

        /// Creates a reusable theme-pack list cell.
        ///
        /// @param page the owning management page
        /// @param listView the list view that owns this cell
        private ThemePackItemCell(ThemePackManagementPage page, JFXListView<ThemePackManager.InstalledThemePack> listView) {
            BorderPane root = new BorderPane();

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);

            this.content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            center.getChildren().setAll(content);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXButton applyButton = FXUtils.newToggleButton4(SVG.CHECK);
            applyButton.setOnAction(event -> {
                if (!isEmpty()) {
                    page.selectAndApplyThemePack(getItem());
                }
            });
            FXUtils.installFastTooltip(applyButton, applyTooltip);

            JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN);
            revealButton.setOnAction(event -> {
                if (!isEmpty()) {
                    revealThemePack(getItem());
                }
            });
            FXUtils.installFastTooltip(revealButton, i18n("reveal.in_file_manager"));

            JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE_FOREVER);
            deleteButton.setOnAction(event -> {
                if (!isEmpty()) {
                    page.deleteThemePack(getItem());
                }
            });
            FXUtils.installFastTooltip(deleteButton, i18n("theme_pack.delete"));

            right.getChildren().setAll(applyButton, revealButton, deleteButton);
            root.setRight(right);

            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            this.graphic = new RipplerContainer(root);
            FXUtils.limitCellWidth(listView, this);
        }

        /// Updates this cell for one installed theme pack.
        @Override
        protected void updateItem(ThemePackManager.InstalledThemePack themePack, boolean empty) {
            super.updateItem(themePack, empty);
            if (empty) {
                setGraphic(null);
                return;
            }

            ThemePackManifest manifest = themePack.manifest();
            content.setTitle(manifest.name());
            content.setSubtitle(getThemePackSubtitle(manifest));
            content.getTags().clear();
            content.addTag(i18n("theme_pack.version", manifest.version()));
            content.addTag(i18n("theme_pack.themes", manifest.themes().size()));
            if (isCurrentThemePack(themePack)) {
                content.addTag(i18n("theme_pack.current"));
            }

            applyTooltip.setText(i18n("theme_pack.apply"));
            setGraphic(graphic);
        }
    }
}
