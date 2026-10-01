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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadCandidates;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.FavoritesManager;
import org.jackhuang.hmcl.setting.FavoritesManager.Favorite;
import org.jackhuang.hmcl.setting.FavoritesManager.Item;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.RemoteImageLoader;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.javafx.ExtendedProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddonFavoritesPage extends Control implements DecoratorPage, PageAware {

    public static String getFavoriteDisplayName(Favorite favorite) {
        if (favorite.getName().isEmpty()) return i18n("addon.favorites.default");
        return favorite.getName();
    }

    private static final FavoritesManager manager = FavoritesManager.getInstance();

    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final ObjectProperty<HMCLGameInstance.Optional> instanceReference = new SimpleObjectProperty<>();
    private final ObservableList<GameInstanceID> instances = FXCollections.observableArrayList();
    private final ObjectProperty<GameInstanceID> selectedInstance = new SimpleObjectProperty<>();
    private final DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

    private final ListProperty<Favorite> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());

    private final TransitionPane body = new TransitionPane();
    private final FavList favList = new FavList(this);

    public AddonFavoritesPage() {
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE && body.getCurrentNode() != favList) {
                navigateBack();
                e.consume();
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        instanceReference.set(instance);

        HMCLGameRepository repository = instance.repository();
        instances.setAll(repository.getDisplayInstances()
                .map(DefaultGameInstance::getId)
                .toList());
        @Nullable HMCLGameInstance repositorySelection = repository.getSelectedInstance();
        selectedInstance.set(repositorySelection != null ? repositorySelection.getId() : null);

        refresh();
    }

    @Override
    public void refresh() {
        setLoading(true);
        navigateBack();
        items.clear();
        Task.runAsync(Schedulers.io(), manager::load)
                .thenRunAsync(Schedulers.javafx(), () -> {
                    items.setAll(manager.getFavorites());
                    setLoading(false);
                }).start();
    }

    @Override
    public void onPageShown() {
        refresh();
    }

    @Override
    public void onPageHidden() {
        navigateBack();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public boolean isLoading() {
        return this.loading.get();
    }

    @Override
    public Skin<?> createDefaultSkin() {
        return new AddonFavoritesPageSkin(this);
    }

    private void createNewFav() {
        Controllers.prompt(
                i18n("addon.favorites.create"),
                (result, handler) -> {
                    manager.getOrCreate(result);
                    refresh();
                    handler.resolve();
                },
                "",
                new Validator(i18n("addon.favorites.create.name_blank"), StringUtils::isNotBlank),
                new Validator(i18n("addon.favorites.create.already_exists"), s ->
                        manager.getFavorites().stream().map(Favorite::getName).noneMatch(name -> name.equals(s)))
        );
    }

    private void navigateTo(Favorite favorite) {
        var page = new FavItemsList(this, favorite);
        body.setContent(page, ContainerAnimations.SWIPE_LEFT);
        page.requestFocus();
        page.refresh();
    }

    private void navigateBack() {
        if (body.getCurrentNode() != favList) {
            body.setContent(favList, ContainerAnimations.SWIPE_RIGHT);
            favList.requestFocus();
            refresh();
        }
    }

    private static class AddonFavoritesPageSkin extends SkinBase<AddonFavoritesPage> {

        protected AddonFavoritesPageSkin(AddonFavoritesPage control) {
            super(control);

            BorderPane pane = new BorderPane();

            GridPane searchPane = new GridPane();
            pane.setTop(searchPane);
            searchPane.getStyleClass().addAll("card");
            BorderPane.setMargin(searchPane, new Insets(10, 10, 0, 10));

            ColumnConstraints nameColumn = new ColumnConstraints();
            nameColumn.setMinWidth(USE_PREF_SIZE);
            ColumnConstraints column1 = new ColumnConstraints();
            column1.setHgrow(Priority.ALWAYS);
            searchPane.getColumnConstraints().setAll(nameColumn, column1);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                int rowIndex = 0;
                searchPane.addRow(rowIndex);
                int columns = 0;
                Node lastNode;

                JFXComboBox<GameInstanceID> instancesComboBox = new JFXComboBox<>();
                instancesComboBox.setMaxWidth(Double.MAX_VALUE);
                Bindings.bindContent(instancesComboBox.getItems(), control.instances);
                ExtendedProperties.selectedItemPropertyFor(instancesComboBox).bindBidirectional(control.selectedInstance);

                searchPane.add(new Label(i18n("instance")), columns++, rowIndex);
                searchPane.add(lastNode = instancesComboBox, columns, rowIndex);

                GridPane.setColumnSpan(lastNode, 3);
            }

            control.body.setContent(control.favList, ContainerAnimations.NONE);
            pane.setCenter(control.body);

            getChildren().setAll(pane);
        }
    }

    private static class FavList extends ListPageBase<Favorite> {

        private final AddonFavoritesPage parentPage;

        public FavList(AddonFavoritesPage parentPage) {
            this.parentPage = parentPage;

            this.itemsProperty().bind(parentPage.items);
            this.loadingProperty().bind(parentPage.loading);
        }

        public Skin<?> createDefaultSkin() {
            return new FavListSkin(this);
        }
    }

    private static class FavListSkin extends ToolbarListPageSkin<Favorite, FavList> {

        public FavListSkin(FavList skinnable) {
            super(skinnable);

            listView.setCellFactory(x -> new FavCell(skinnable.parentPage, listView));
        }

        @Override
        protected List<Node> initializeToolbar(FavList skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable.parentPage::refresh),
                    ToolbarListPageSkin.createToolbarButton2(i18n("addon.favorites.create"), SVG.ADD, skinnable.parentPage::createNewFav)
            );
        }
    }

    private static class FavCell extends MDListCell<Favorite> {

        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE);

        public FavCell(AddonFavoritesPage parentPage, JFXListView<Favorite> listView) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            deleteButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty()) {
                    manager.remove(getItem());
                    parentPage.refresh();
                }
            });

            container.getChildren().setAll(content, deleteButton);

            StackPane.setMargin(container, new Insets(8, 8, 8, 16));
            getContainer().getChildren().setAll(container);

            onClicked(() -> {
                if (getItem() != null && !isEmpty()) parentPage.navigateTo(getItem());
            });
        }

        @Override
        protected void updateControl(Favorite item, boolean empty) {
            if (item == null || empty) return;

            deleteButton.setVisible(!item.getName().isEmpty()); // Forbid deleting default fav

            content.setTitle(getFavoriteDisplayName(item));

            final int count = item.getItems().size();
            final int availableCount;
            if (CurseForgeRemoteAddonRepository.isAvailable()) {
                availableCount = count;
            } else {
                availableCount = (int) item.getItems().stream().filter(i -> i.source() == RemoteAddon.Source.MODRINTH).count();
            }
            final String subtitle;
            if (availableCount == count) {
                subtitle = count + " items"; // TODO i18n
            } else {
                subtitle = "%d items, %d available".formatted(count, availableCount); // TODO i18n
            }
            content.setSubtitle(subtitle);
        }
    }

    public static class FavItemsList extends ListPageBase<FavItemObject> {

        private final AddonFavoritesPage parentPage;
        private final DownloadProvider downloadProvider;
        private final Favorite favorite;

        public FavItemsList(AddonFavoritesPage parentPage, Favorite favorite) {
            this.parentPage = parentPage;
            this.downloadProvider = parentPage.downloadProvider;
            this.favorite = favorite;
        }

        @Override
        protected Skin<?> createDefaultSkin() {
            return new FavItemsListSkin(this);
        }

        public void refresh() {
            setLoading(true);
            getItems().clear();
            Task.supplyAsync(Schedulers.io(), () -> {
                favorite.resolve(downloadProvider);
                return favorite.getResolvedAddons();
            }).whenComplete(Schedulers.javafx(), (map, exception) -> {
                getItems().setAll(map.entrySet().stream().map(FavItemObject::new).toList());
                setLoading(false);
            }).start();
        }

        private void remove(Collection<Item> items) {
            if (favorite.remove(items))
                refresh();
        }
    }

    public static final class FavItemObject {

        private final Item item;
        private final @Nullable RemoteAddon addon;

        private FavItemObject(Map.Entry<Item, RemoteAddon> entry) {
            this.item = entry.getKey();
            this.addon = entry.getValue();
        }
    }

    private static final class FavItemsListSkin extends ToolbarListPageSkin<FavItemObject, FavItemsList> {

        public FavItemsListSkin(FavItemsList skinnable) {
            super(skinnable);

            var iconLoader = new RemoteImageLoader(skinnable.downloadProvider) {
                @Override
                protected @NotNull Task<Image> createLoadTask(@NotNull DownloadCandidates candidates) {
                    return FXUtils.getRemoteImageTask(candidates, 64, 64, true, true);
                }
            };

            listView.setCellFactory(x -> new FavItemCell(skinnable, iconLoader, listView));
        }

        @Override
        protected List<Node> initializeToolbar(FavItemsList skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2("", SVG.ARROW_BACK, skinnable.parentPage::navigateBack),
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }
    }

    private static final class FavItemCell extends MDListCell<FavItemObject> {

        private final RemoteImageLoader iconLoader;

        private final ImageContainer imageContainer = new ImageContainer(32);
        private final TwoLineListItem content = new TwoLineListItem();

        public FavItemCell(FavItemsList page, RemoteImageLoader iconLoader, JFXListView<FavItemObject> listView) {
            super(listView);

            this.iconLoader = iconLoader;

            JFXButton removeButton = FXUtils.newToggleButton4(SVG.DELETE);
            removeButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty()) {
                    page.remove(List.of(getItem().item));
                }
            });

            JFXButton infoButton = FXUtils.newToggleButton4(SVG.INFO);
            infoButton.setOnAction(e -> {
                if (getItem() != null && !isEmpty() && getItem().addon != null) {
                    var downloadListPage = HMCLLocalizedDownloadListPage.ofAddonWithSource(false, getItem().addon);
                    if (downloadListPage == null) return;
                    Controllers.navigate(new DownloadPage(
                            downloadListPage,
                            getItem().addon,
                            HMCLGameInstance.Optional.of(page.parentPage.instanceReference.get().repository(), page.parentPage.selectedInstance.get()),
                            switch (getItem().addon.type()) { // TODO remove this
                                case MOD -> org.jackhuang.hmcl.ui.download.DownloadPage.FOR_MOD;
                                case RESOURCE_PACK -> org.jackhuang.hmcl.ui.download.DownloadPage.FOR_RESOURCE_PACK;
                                case SHADER_PACK -> org.jackhuang.hmcl.ui.download.DownloadPage.FOR_SHADER;
                                default -> null;
                            }
                    ));
                }
            });

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            container.getChildren().setAll(imageContainer, content, removeButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(FavItemObject item, boolean empty) {
            if (item == null || empty) return;

            content.getTags().clear();

            @Nullable RemoteAddon addon = item.addon;
            RemoteAddon.Source source = item.item.source();
            content.addTag(i18n(switch (source) {
                case MODRINTH -> "addon.modrinth";
                case CURSEFORGE -> "addon.curseforge";
            }));
            if (addon != null) {
                if (addon.type() != null && I18n.isUseChinese()) {
                    ModTranslations.Mod mod = ModTranslations.getTranslationsByAddonType(addon.type()).getModByCurseForgeId(addon.slug());
                    if (mod != null) content.setTitle(mod.getDisplayName());
                    else content.setTitle(addon.title());
                } else {
                    content.setTitle(addon.title());
                }
                {
                    String description = addon.description();
                    if (description != null) description = description.replaceAll("\\R", " ");
                    content.setSubtitle(description);
                }

                if (addon.type() != null) {
                    String type = switch (addon.type()) {
                        case MOD -> i18n("mods");
                        case RESOURCE_PACK -> i18n("resourcepack");
                        case SHADER_PACK -> i18n("download.shader"); //TODO update
                        default -> null;
                    };
                    if (type != null) content.addTag(type);
                }

                for (String category : addon.categories()) {
                    if (!"minecraft".equalsIgnoreCase(category)) {
                        content.addTag(i18n(switch (source) {
                            case MODRINTH -> "modrinth.category." + category;
                            case CURSEFORGE -> "curse.category." + category;
                        }));
                    }
                }

                iconLoader.load(imageContainer.imageProperty(), addon.iconUrl());
            }
        }
    }
}
