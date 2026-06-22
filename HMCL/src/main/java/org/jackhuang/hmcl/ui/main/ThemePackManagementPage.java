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
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.theme.Theme;
import org.jackhuang.hmcl.theme.ThemePackExporter;
import org.jackhuang.hmcl.theme.ThemePackManager;
import org.jackhuang.hmcl.theme.ThemePackManifest;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Shows installed theme packs and common theme-pack actions.
@NotNullByDefault
public final class ThemePackManagementPage extends ListPageBase<ThemePackManager.InstalledThemePack> implements DecoratorPage {
    /// The decorator title state for this page.
    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.fromTitle(i18n("theme_pack.manage")));

    /// Source list containing all installed theme packs.
    private final ObservableList<ThemePackManager.InstalledThemePack> sourceList =
            FXCollections.observableArrayList();

    /// Filtered list shown by the current search query.
    private final FilteredList<ThemePackManager.InstalledThemePack> filteredList = new FilteredList<>(sourceList);

    /// Callback invoked after the installed theme-pack set changes.
    private final Runnable onThemePacksChanged;

    /// Creates the theme-pack management page and loads installed packages.
    public ThemePackManagementPage() {
        this(() -> {
        });
    }

    /// Creates the theme-pack management page and loads installed packages.
    ///
    /// @param onThemePacksChanged callback invoked after importing or deleting a theme pack
    public ThemePackManagementPage(Runnable onThemePacksChanged) {
        this.onThemePacksChanged = Objects.requireNonNull(onThemePacksChanged);
        setItems(filteredList);
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
            sourceList.setAll(themePacks);
            setFailedReason(themePacks.isEmpty() ? i18n("theme_pack.empty") : null);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to load installed theme packs", e);
            sourceList.clear();
            setFailedReason(i18n("theme_pack.load.failed"));
        }
    }

    /// Creates a predicate used by the theme-pack search field.
    private Predicate<ThemePackManager.InstalledThemePack> createPredicate(String searchText) {
        if (StringUtils.isBlank(searchText)) {
            return themePack -> true;
        }

        String query = searchText.toLowerCase(Locale.ROOT);
        return themePack -> {
            ThemePackManifest manifest = themePack.manifest();
            return containsIgnoreCase(manifest.displayName(), query)
                    || containsIgnoreCase(manifest.id(), query)
                    || containsIgnoreCase(manifest.version(), query)
                    || containsIgnoreCase(manifest.displayDescription(), query)
                    || manifest.authors().stream().anyMatch(author -> containsIgnoreCase(author, query));
        };
    }

    /// Opens the managed theme-pack directory in the platform file manager.
    private void openThemePackDirectory() {
        FXUtils.openFolder(ThemePackManager.THEME_PACKS_DIRECTORY);
    }

    /// Opens a theme-pack file and installs it.
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

        onThemePacksChanged.run();
        Controllers.showToast(i18n("theme_pack.import.success", themePack.manifest().displayName()));
    }

    /// Shows the installed package directory in the platform file manager.
    private static void revealThemePack(ThemePackManager.InstalledThemePack themePack) {
        FXUtils.showFileInExplorer(themePack.directory());
    }

    /// Asks for confirmation and deletes an installed theme pack.
    private void deleteThemePack(ThemePackManager.InstalledThemePack themePack) {
        Controllers.confirm(
                i18n("theme_pack.delete.confirm", themePack.manifest().displayName()),
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
            onThemePacksChanged.run();
            Controllers.showToast(i18n("theme_pack.delete.success", themePack.manifest().displayName()));
        } catch (IOException | RuntimeException e) {
            showThemePackError(i18n("theme_pack.delete.failed"), e);
        }
    }

    /// Shows all themes declared by an installed theme pack.
    private void showThemePackInfo(ThemePackManager.InstalledThemePack themePack) {
        Controllers.dialog(new ThemePackInfoDialog(themePack));
    }

    /// Returns a subtitle for one installed theme pack.
    private static String getThemePackSubtitle(ThemePackManifest manifest) {
        @Nullable String description = manifest.displayDescription();
        if (StringUtils.isBlank(description)) {
            return manifest.id();
        }
        return description;
    }

    /// Returns the effective display name for one theme.
    private static String getThemeDisplayName(ThemePackManifest manifest, Theme theme) {
        return Objects.requireNonNullElse(theme.displayName(), manifest.displayName());
    }

    /// Returns the description shown below one theme in the theme list dialog.
    private static @Nullable String getThemeDescription(Theme theme) {
        @Nullable String description = theme.displayDescription();
        return StringUtils.isBlank(description) ? null : description;
    }

    /// Creates a read-only theme information row.
    private static TwoLineListItem createThemeInfoItem(ThemePackManifest manifest, Theme theme) {
        TwoLineListItem item = new TwoLineListItem();
        item.setTitle(getThemeDisplayName(manifest, theme));
        item.setSubtitle(getThemeDescription(theme));
        addThemeTags(item, theme);
        return item;
    }

    /// Adds metadata tags for one theme.
    private static void addThemeTags(TwoLineListItem item, Theme theme) {
        if (theme.id() != null) {
            item.addTag(i18n("theme_pack.theme.id", theme.id()));
        }
    }

    /// Returns whether a nullable value contains the lower-cased query.
    private static boolean containsIgnoreCase(@Nullable String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    /// Shows a theme-pack operation error dialog.
    private static void showThemePackError(String title, Exception exception) {
        Controllers.dialog(
                title + "\n\n" + StringUtils.getStackTrace(exception),
                i18n("message.error"),
                MessageType.ERROR);
    }

    /// Dialog showing all themes declared by one installed theme pack.
    @NotNullByDefault
    private static final class ThemePackInfoDialog extends JFXDialogLayout {
        /// Creates the theme-pack information dialog.
        ///
        /// @param themePack the installed theme pack to display
        private ThemePackInfoDialog(ThemePackManager.InstalledThemePack themePack) {
            ThemePackManifest manifest = themePack.manifest();
            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            setHeading(createHeading(manifest));
            setBody(createBody(manifest));

            JFXButton okButton = new JFXButton(i18n("button.ok"));
            okButton.getStyleClass().add("dialog-accept");
            okButton.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            setActions(okButton);
            onEscPressed(this, okButton::fire);
        }

        /// Creates the dialog heading.
        private static HBox createHeading(ThemePackManifest manifest) {
            HBox heading = new HBox(8);
            heading.setAlignment(Pos.CENTER_LEFT);

            Node icon = SVG.STYLE.createIcon(40);
            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(manifest.displayName());
            title.setSubtitle(manifest.id());
            title.addTag(i18n("theme_pack.version", manifest.version()));
            title.addTag(i18n("theme_pack.themes", manifest.themes().size()));
            if (!manifest.authors().isEmpty()) {
                title.addTag(i18n("archive.author") + ": " + String.join(", ", manifest.authors()));
            }

            heading.getChildren().setAll(icon, title);
            return heading;
        }

        /// Creates the scrollable dialog body.
        private static StackPane createBody(ThemePackManifest manifest) {
            ComponentList themes = new ComponentList();
            for (Theme theme : manifest.themes()) {
                themes.getContent().add(createThemeInfoItem(manifest, theme));
            }

            ScrollPane scrollPane = new ScrollPane(themes);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setPrefViewportWidth(520);
            scrollPane.setPrefViewportHeight(Math.min(360, manifest.themes().size() * 86));
            scrollPane.maxHeightProperty().bind(Controllers.getStage().heightProperty().multiply(0.55));

            StackPane body = new StackPane(scrollPane);
            body.setPadding(new Insets(10, 0, 0, 0));
            return body;
        }
    }

    /// Skin for the theme-pack management list page.
    @NotNullByDefault
    private static final class ThemePackManagementPageSkin extends SkinBase<ThemePackManagementPage> {
        /// Toolbar container used to switch between normal and search actions.
        private final TransitionPane toolbarPane = new TransitionPane();

        /// Search toolbar.
        private final HBox searchBar = new HBox();

        /// Toolbar shown during normal browsing.
        private final HBox toolbarNormal = new HBox();

        /// Search input.
        private final JFXTextField searchField = new JFXTextField();

        /// List of installed theme packs.
        private final JFXListView<ThemePackManager.InstalledThemePack> listView = new JFXListView<>();

        /// Creates the management page skin.
        ///
        /// @param skinnable the page controlled by this skin
        private ThemePackManagementPageSkin(ThemePackManagementPage skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            initializeToolbar(skinnable, root);
            initializeList(skinnable, root);

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
        }

        /// Initializes toolbar actions and search.
        private void initializeToolbar(ThemePackManagementPage skinnable, ComponentList root) {
            searchBar.setAlignment(Pos.CENTER);
            searchBar.setPadding(new Insets(0, 5, 0, 5));
            searchField.setPromptText(i18n("search"));
            HBox.setHgrow(searchField, Priority.ALWAYS);

            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished(event ->
                    skinnable.filteredList.setPredicate(skinnable.createPredicate(searchField.getText())));
            searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                pause.setRate(1);
                pause.playFromStart();
            });

            JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE, () -> {
                changeToolbar(toolbarNormal);
                searchField.clear();
            });
            onEscPressed(searchField, closeSearchBar::fire);

            searchBar.getChildren().setAll(searchField, closeSearchBar);

            toolbarNormal.setAlignment(Pos.CENTER_LEFT);
            toolbarNormal.setPickOnBounds(false);
            toolbarNormal.getChildren().setAll(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshThemePacks),
                    createToolbarButton2(i18n("theme_pack.import"), SVG.FILE_OPEN, skinnable::importThemePack),
                    createToolbarButton2(i18n("theme_pack.directory"), SVG.FOLDER_OPEN, skinnable::openThemePackDirectory),
                    createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar)));

            toolbarPane.setContent(toolbarNormal, ContainerAnimations.FADE);
            FXUtils.setOverflowHidden(toolbarPane, 8);

            root.getContent().add(toolbarPane);
        }

        /// Initializes the installed theme-pack list.
        private void initializeList(ThemePackManagementPage skinnable, ComponentList root) {
            SpinnerPane center = new SpinnerPane();
            ComponentList.setVgrow(center, Priority.ALWAYS);
            center.loadingProperty().bind(skinnable.loadingProperty());
            center.failedReasonProperty().bind(skinnable.failedReasonProperty());
            center.onFailedActionProperty().bind(skinnable.onFailedActionProperty());

            listView.setPadding(Insets.EMPTY);
            listView.setCellFactory(x -> new ThemePackItemCell(skinnable));
            listView.setItems(skinnable.getItems());
            listView.getStyleClass().add("no-horizontal-scrollbar");
            ignoreEvent(listView, KeyEvent.KEY_PRESSED, event -> event.getCode() == KeyCode.ESCAPE);

            center.setContent(listView);
            root.getContent().add(center);
        }

        /// Switches the visible toolbar.
        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    runInFX(searchField::requestFocus);
                }
            }
        }
    }

    /// List cell that renders one installed theme pack.
    @NotNullByDefault
    private static final class ThemePackItemCell extends ListCell<ThemePackManager.InstalledThemePack> {
        /// Owning management page.
        private final ThemePackManagementPage page;

        /// Root graphic reused by this cell.
        private final Region graphic;

        /// The text content shown for the current theme pack.
        private final TwoLineListItem content = new TwoLineListItem();

        /// Right-side action container.
        private final HBox right = new HBox();

        /// Shows the package directory.
        private final JFXButton revealButton;

        /// Deletes the package.
        private final JFXButton deleteButton;

        /// Creates a reusable theme-pack list cell.
        ///
        /// @param page the owning management page
        private ThemePackItemCell(ThemePackManagementPage page) {
            this.page = page;

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));
            this.graphic = root;

            HBox center = new HBox();
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);
            root.setCenter(center);

            Node icon = SVG.STYLE.createIcon(32);
            icon.setMouseTransparent(true);
            BorderPane.setAlignment(content, Pos.CENTER);
            content.setMouseTransparent(true);
            center.getChildren().setAll(icon, content);
            HBox.setHgrow(content, Priority.ALWAYS);
            center.setCursor(Cursor.HAND);
            FXUtils.onClicked(center, () -> {
                ThemePackManager.InstalledThemePack themePack = getItem();
                if (themePack != null) {
                    page.showThemePackInfo(themePack);
                }
            });

            right.setAlignment(Pos.CENTER_RIGHT);
            root.setRight(right);

            revealButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN);
            revealButton.setOnAction(event -> {
                ThemePackManager.InstalledThemePack themePack = getItem();
                if (themePack != null) {
                    revealThemePack(themePack);
                }
            });
            FXUtils.installFastTooltip(revealButton, i18n("reveal.in_file_manager"));

            deleteButton = FXUtils.newToggleButton4(SVG.DELETE);
            deleteButton.setOnAction(event -> {
                ThemePackManager.InstalledThemePack themePack = getItem();
                if (themePack != null) {
                    page.deleteThemePack(themePack);
                }
            });
            FXUtils.installFastTooltip(deleteButton, i18n("theme_pack.delete"));
        }

        /// Updates this cell for one installed theme pack.
        @Override
        protected void updateItem(ThemePackManager.InstalledThemePack themePack, boolean empty) {
            super.updateItem(themePack, empty);

            content.getTags().clear();
            if (empty || themePack == null) {
                setGraphic(null);
                return;
            }

            setGraphic(graphic);

            ThemePackManifest manifest = themePack.manifest();
            content.setTitle(manifest.displayName());
            content.setSubtitle(getThemePackSubtitle(manifest));
            content.addTag(i18n("theme_pack.version", manifest.version()));
            content.addTag(i18n("theme_pack.themes", manifest.themes().size()));

            right.getChildren().setAll(revealButton, deleteButton);
        }
    }
}
