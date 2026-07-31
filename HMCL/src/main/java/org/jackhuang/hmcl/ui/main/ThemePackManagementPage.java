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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.theme.*;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Shows installed theme packs and common theme-pack actions.
@NotNullByDefault
public final class ThemePackManagementPage extends ListPageBase<ThemePackManager.InstalledThemePack> implements DecoratorPage {
    /// The icon container size used by theme-pack icons.
    private static final double ICON_SIZE = 40;

    /// The requested decoded icon image size used for HiDPI displays.
    private static final int ICON_IMAGE_SIZE = (int) (ICON_SIZE * 2);

    /// The decorator title state for this page.
    private final ObjectProperty<State> state = new SimpleObjectProperty<>(State.fromTitle(i18n("theme_pack.manage")));

    /// Page-scoped icon image cache cleared when installed theme packs are reloaded.
    private final IconCache iconCache = new IconCache();

    /// Callback invoked after the installed theme-pack set changes.
    private final Runnable onThemePacksChanged;

    /// Creates the theme-pack management page and loads installed packages.
    ///
    /// @param onThemePacksChanged callback invoked after importing or deleting a theme pack
    public ThemePackManagementPage(Runnable onThemePacksChanged) {
        getStyleClass().add("gray-background");

        this.onThemePacksChanged = Objects.requireNonNull(onThemePacksChanged);
        setOnFailedAction(event -> refreshThemePacks());
        refreshThemePacks();

        FXUtils.applyDragListener(this, it -> "hmcl-theme".equals(FileUtils.getExtension(it)), paths -> paths.forEach(this::importThemePackImpl));
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

    /// Reloads installed theme packs from disk.
    private void refreshThemePacks() {
        iconCache.clear();
        try {
            List<ThemePackManager.InstalledThemePack> themePacks = ThemePackManager.listInstalled();
            getItems().setAll(themePacks);
            setFailedReason(themePacks.isEmpty() ? i18n("theme_pack.empty") : null);
        } catch (IOException | RuntimeException e) {
            LOG.warning("Failed to load installed theme packs", e);
            getItems().clear();
            setFailedReason(i18n("theme_pack.load.failed"));
        }
    }

    /// Opens a theme-pack file and installs it.
    private void importThemePack() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("theme_pack.import.title"));
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(i18n("theme_pack.file"), "*" + ThemePackExporter.FILE_EXTENSION));

        @Nullable Path file = Controllers.showOpenDialog(chooser);
        if (file == null) {
            return;
        }

        importThemePackImpl(file);
    }

    private void importThemePackImpl(Path file) {
        ThemePackManager.InstalledThemePack themePack;
        try {
            themePack = ThemePackManager.install(file);
            refreshThemePacks();
        } catch (IOException | RuntimeException e) {
            String title = i18n("theme_pack.import.failed");
            Controllers.dialog(
                    title + "\n\n" + StringUtils.getStackTrace(e),
                    i18n("message.error"),
                    MessageType.ERROR);
            return;
        }

        onThemePacksChanged.run();
        Controllers.showToast(i18n("theme_pack.import.success", themePack.manifest().displayName()));
    }

    /// Shows the installed package file in the platform file manager.
    private static void revealThemePack(ThemePackManager.InstalledThemePack themePack) {
        if (themePack.builtin()) {
            return;
        }
        @Nullable Path file = themePack.file();
        if (file != null) {
            FXUtils.showFileInExplorer(file);
        }
    }

    /// Asks for confirmation and deletes an installed theme pack.
    private void deleteThemePack(ThemePackManager.InstalledThemePack themePack) {
        if (themePack.builtin()) {
            return;
        }
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
            Controllers.dialog(
                    i18n("theme_pack.delete.failed") + "\n\n" + StringUtils.getStackTrace(e),
                    i18n("message.error"),
                    MessageType.ERROR);
        }
    }

    /// Handles a theme-pack row click.
    private void selectThemePack(ThemePackManager.InstalledThemePack themePack) {
        ThemePackManifest manifest = themePack.manifest();
        if (manifest.themes().size() == 1 && manifest.themes().get(0).id() == null) {
            confirmApplyTheme(themePack, manifest.themes().get(0));
        } else {
            Controllers.dialog(new ThemePackInfoDialog(this, themePack));
        }
    }

    /// Asks for confirmation before applying one theme.
    private void confirmApplyTheme(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        Controllers.confirm(
                i18n("theme_pack.apply.confirm", getThemeTitle(themePack.manifest(), theme)),
                i18n("theme_pack.apply"),
                MessageType.QUESTION,
                () -> applyTheme(themePack, theme),
                null);
    }

    /// Applies one theme after confirmation.
    private void applyTheme(ThemePackManager.InstalledThemePack themePack, Theme theme) {
        try {
            if (Themes.shouldWaitForThemeBackground()) {
                Controllers.taskDialog(
                        Themes.applyTheme(themePack, theme),
                        i18n("launcher.background.loading"),
                        TaskCancellationAction.NO_CANCEL);
            } else {
                ThemePackManager.apply(themePack, theme);
            }
            onThemePacksChanged.run();
        } catch (IOException | RuntimeException e) {
            Controllers.dialog(
                    i18n("theme_pack.apply.failed") + "\n\n" + StringUtils.getStackTrace(e),
                    i18n("message.error"),
                    MessageType.ERROR);
        }
    }

    /// Returns comma-separated author display names.
    private static String getAuthorDisplayNames(List<ThemePackAuthor> authors) {
        return authors.stream()
                .map(ThemePackAuthor::displayName)
                .filter(author -> !StringUtils.isBlank(author))
                .collect(Collectors.joining(", "));
    }

    /// Returns the display title for one theme-pack theme.
    private static String getThemeTitle(ThemePackManifest manifest, Theme theme) {
        if (manifest.themes().size() == 1 && theme.id() == null) {
            return manifest.displayName();
        }
        return manifest.displayName() + " - " + getThemeDisplayName(manifest, theme);
    }

    /// Returns the display name for one theme inside a theme list.
    private static String getThemeDisplayName(ThemePackManifest manifest, Theme theme) {
        return Objects.requireNonNullElse(
                theme.displayName(),
                Objects.requireNonNullElse(theme.id(), manifest.displayName()));
    }

    /// Returns whether a nullable value contains the lower-cased query.
    static boolean containsIgnoreCase(@Nullable String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    /// Creates an icon node for a theme-pack asset path.
    private Node createIconNode(
            ThemePackManager.InstalledThemePack themePack,
            @Nullable String icon,
            SVG fallback) {
        ImageContainer imageContainer = new ImageContainer(ICON_SIZE);
        SVGContainer fallbackIcon = createFallbackIcon(fallback);
        updateIcon(imageContainer, fallbackIcon, themePack, icon);

        StackPane container = new StackPane(imageContainer, fallbackIcon);
        container.setMouseTransparent(true);
        return container;
    }

    /// Creates a vector fallback icon.
    private static SVGContainer createFallbackIcon(SVG fallback) {
        SVGContainer fallbackIcon = new SVGContainer(fallback, ICON_SIZE);
        fallbackIcon.setMouseTransparent(true);
        return fallbackIcon;
    }

    /// Updates reusable icon nodes for the supplied theme pack.
    private void updateIcon(
            ImageContainer imageContainer,
            SVGContainer fallbackIcon,
            ThemePackManager.InstalledThemePack themePack,
            @Nullable String icon) {
        @Nullable Image image = getIconImage(themePack, icon);
        imageContainer.setImage(image);
        fallbackIcon.setVisible(image == null);
    }

    /// Returns the icon image for a fixed-size icon container.
    private @Nullable Image getIconImage(
            ThemePackManager.InstalledThemePack themePack,
            @Nullable String icon) {
        @Nullable Image image = null;
        if (!StringUtils.isBlank(icon)) {
            image = iconCache.getThemePackIcon(themePack.location(), icon);
        }
        return image != null || !themePack.builtin() ? image : iconCache.getDefaultBuiltinIcon();
    }

    /// Cache key for one decoded icon image.
    @NotNullByDefault
    private record IconCacheKey(
            ThemePackManager.ThemePackLocation location,
            String icon) {
        private IconCacheKey {
            Objects.requireNonNull(location);
            Objects.requireNonNull(icon);
        }
    }

    /// Page-scoped cache for decoded icon images.
    @NotNullByDefault
    private static final class IconCache {
        private final Map<IconCacheKey, Optional<Image>> icons = new HashMap<>();
        private @Nullable Image defaultBuiltinIcon;

        private void clear() {
            icons.clear();
            defaultBuiltinIcon = null;
        }

        private @Nullable Image getThemePackIcon(
                ThemePackManager.ThemePackLocation location,
                String icon) {
            IconCacheKey key = new IconCacheKey(location, icon);
            return icons.computeIfAbsent(key, this::loadThemePackIcon).orElse(null);
        }

        private Image getDefaultBuiltinIcon() {
            if (defaultBuiltinIcon == null) {
                defaultBuiltinIcon = FXUtils.newBuiltinImage(
                        "/assets/img/icon@8x.png",
                        ICON_IMAGE_SIZE,
                        ICON_IMAGE_SIZE,
                        true,
                        true);
            }
            return defaultBuiltinIcon;
        }

        private Optional<Image> loadThemePackIcon(IconCacheKey key) {
            try {
                ThemePackResource resource = ThemePackManager.resolveInstalledAsset(key.location(), key.icon());
                return Optional.of(FXUtils.loadImage(
                        resource.openStream(),
                        resource.name(),
                        ICON_IMAGE_SIZE,
                        ICON_IMAGE_SIZE,
                        true,
                        true));
            } catch (Exception e) {
                LOG.warning("Failed to load theme-pack icon: " + key.icon(), e);
                return Optional.empty();
            }
        }
    }

    /// Dialog showing all themes declared by one installed theme pack.
    @NotNullByDefault
    private static final class ThemePackInfoDialog extends JFXDialogLayout {
        private ThemePackInfoDialog(ThemePackManagementPage page, ThemePackManager.InstalledThemePack themePack) {
            ThemePackManifest manifest = themePack.manifest();
            maxWidthProperty().bind(Controllers.windowWidthProperty().multiply(0.7));

            HBox heading = new HBox(8);
            heading.setAlignment(Pos.CENTER_LEFT);

            Node icon = page.createIconNode(themePack, manifest.icon(), SVG.PACKAGE2);
            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(manifest.displayName());
            title.setSubtitle(manifest.id());
            title.addTag(i18n("theme_pack.version", manifest.version()));
            title.addTag(i18n("theme_pack.themes", manifest.themes().size()));
            String authors = getAuthorDisplayNames(manifest.authors());
            if (!StringUtils.isBlank(authors)) {
                title.addTag(i18n("archive.author") + ": " + authors);
            }
            heading.getChildren().setAll(icon, title);
            setHeading(heading);

            ComponentList themes = new ComponentList();
            themes.getStyleClass().add("no-padding");
            for (Theme theme : manifest.themes()) {
                TwoLineListItem item = new TwoLineListItem();
                item.setTitle(getThemeDisplayName(manifest, theme));

                @Nullable String description = theme.displayDescription();
                item.setSubtitle(StringUtils.isBlank(description) ? null : description);

                if (theme.id() != null) {
                    item.addTag(i18n("theme_pack.theme.id", theme.id()));
                }

                List<ThemePackAuthor> themeAuthors = theme.authors().isEmpty() ? manifest.authors() : theme.authors();
                String authorNames = getAuthorDisplayNames(themeAuthors);
                if (!StringUtils.isBlank(authorNames)) {
                    item.addTag(i18n("archive.author") + ": " + authorNames);
                }

                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 16, 10, 16));

                Node themeIcon = page.createIconNode(themePack, theme.icon(), SVG.STYLE);
                HBox.setHgrow(item, Priority.ALWAYS);
                item.setMouseTransparent(true);
                row.getChildren().setAll(themeIcon, item);

                var rowRippler = new RipplerContainer(row);
                rowRippler.setCursor(Cursor.HAND);
                ComponentList.setNoPadding(rowRippler);

                FXUtils.onClicked(rowRippler, () -> {
                    fireEvent(new DialogCloseEvent());
                    page.confirmApplyTheme(themePack, theme);
                });

                themes.getContent().add(rowRippler);
            }

            ScrollPane scrollPane = new ScrollPane(themes);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setPrefViewportWidth(520);
            scrollPane.setPrefViewportHeight(Math.min(360, manifest.themes().size() * 86));
            scrollPane.maxHeightProperty().bind(Controllers.windowHeightProperty().multiply(0.55));

            StackPane body = new StackPane(scrollPane);
            body.setPadding(new Insets(10, 0, 0, 0));
            setBody(body);

            JFXButton okButton = new JFXButton(i18n("button.ok"));
            okButton.getStyleClass().add("dialog-accept");
            okButton.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            setActions(okButton);
            onEscPressed(this, okButton::fire);
        }
    }

    /// Skin for the theme-pack management list page.
    @NotNullByDefault
    private static final class ThemePackManagementPageSkin extends ToolbarListPageSkin<ThemePackManager.InstalledThemePack, ThemePackManagementPage> {

        private ThemePackManagementPageSkin(ThemePackManagementPage skinnable) {
            super(skinnable, true);

            listView.setCellFactory(x -> new ThemePackItemCell(skinnable));

            setupSkin(
                    new Node[]{
                            createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refreshThemePacks),
                            createToolbarButton2(i18n("theme_pack.import"), SVG.FILE_OPEN, skinnable::importThemePack),
                            createToolbarButton2(i18n("theme_pack.directory"), SVG.FOLDER_OPEN,
                                    () -> FXUtils.openFolder(ThemePackManager.THEME_PACKS_DIRECTORY)),
                            createToolbarButton2(i18n("search"), SVG.SEARCH, this::startSearch)
                    },
                    null
            );
        }

        @Override
        protected Predicate<ThemePackManager.InstalledThemePack> updateSearchPredicate(String searchText) {
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
                        || manifest.authors().stream().anyMatch(author -> containsIgnoreCase(author.displayName(), query))
                        || manifest.themes().stream()
                        .flatMap(theme -> theme.authors().stream())
                        .anyMatch(author -> containsIgnoreCase(author.displayName(), query));
            };
        }

        @Override
        protected String getEmptyPlaceholderText() {
            return i18n("theme_pack.empty");
        }
    }

    /// List cell that renders one installed theme pack.
    @NotNullByDefault
    private static final class ThemePackItemCell extends ListCell<ThemePackManager.@Nullable InstalledThemePack> {
        private final ThemePackManagementPage page;
        private final Region graphic;
        private final TwoLineListItem content = new TwoLineListItem();
        private final StackPane icon = new StackPane();
        private final ImageContainer iconImage = new ImageContainer(ICON_SIZE);
        private final SVGContainer iconFallback = createFallbackIcon(SVG.PACKAGE2);
        private final HBox right = new HBox();
        private final JFXButton revealButton;
        private final JFXButton deleteButton;

        private ThemePackItemCell(ThemePackManagementPage page) {
            this.page = page;

            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8));

            var ripplerContainer = new RipplerContainer(root);
            this.graphic = ripplerContainer;

            HBox center = new HBox();
            center.setSpacing(8);
            center.setAlignment(Pos.CENTER_LEFT);
            center.setMouseTransparent(true);
            root.setCenter(center);

            icon.setMouseTransparent(true);
            icon.getChildren().setAll(iconImage, iconFallback);
            BorderPane.setAlignment(content, Pos.CENTER);
            content.setMouseTransparent(true);
            center.getChildren().setAll(icon, content);
            HBox.setHgrow(content, Priority.ALWAYS);
            center.setCursor(Cursor.HAND);
            FXUtils.onClicked(ripplerContainer, () -> {
                ThemePackManager.InstalledThemePack themePack = getItem();
                if (themePack != null) {
                    page.selectThemePack(themePack);
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

        @Override
        protected void updateItem(ThemePackManager.@Nullable InstalledThemePack themePack, boolean empty) {
            var currentItem = getItem();

            super.updateItem(themePack, empty);

            if (Objects.equals(getItem(), currentItem)) return;

            content.getTags().clear();
            iconImage.setImage(null);
            iconFallback.setVisible(false);
            if (empty || themePack == null) {
                setGraphic(null);
                return;
            }

            setGraphic(graphic);

            ThemePackManifest manifest = themePack.manifest();
            page.updateIcon(iconImage, iconFallback, themePack, manifest.icon());
            content.setTitle(manifest.displayName());
            @Nullable String description = manifest.displayDescription();
            content.setSubtitle(StringUtils.isBlank(description) ? manifest.id() : description);
            if (themePack.builtin()) {
                content.addTag(i18n("theme_pack.builtin"));
            }
            content.addTag(i18n("theme_pack.version", manifest.version()));
            content.addTag(i18n("theme_pack.themes", manifest.themes().size()));

            if (themePack.builtin()) {
                right.getChildren().clear();
            } else {
                right.getChildren().setAll(revealButton, deleteButton);
            }
        }
    }
}
