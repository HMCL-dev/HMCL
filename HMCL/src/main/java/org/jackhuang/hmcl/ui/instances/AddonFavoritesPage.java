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

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.setting.FavoritesManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ToolbarListPageSkin;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddonFavoritesPage extends ListPageBase<AddonFavoritesPage.FavoriteItemObject> implements DecoratorPage {

    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();

    private final DownloadProvider downloadProvider;
    private final FavoritesManager.Favorites favorites;

    public AddonFavoritesPage(DownloadProvider downloadProvider, FavoritesManager.Favorites favorites) {
        this.downloadProvider = downloadProvider;
        this.favorites = favorites;
        this.state.set(State.fromTitle(i18n("addon.favorites" + " - " + favorites.getName())));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new AddonFavoritesPageSkin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        setLoading(true);
        getItems().clear();
        Task.supplyAsync(Schedulers.io(), () -> {
            favorites.resolve(downloadProvider);
            return favorites.getResolvedAddons();
        }).whenComplete(Schedulers.javafx(), (map, exception) -> {
            getItems().setAll(map.entrySet().stream().map(FavoriteItemObject::new).toList());
            setLoading(false);
        });
    }

    public static final class FavoriteItemObject {

        private final FavoritesManager.Item item;
        private final @Nullable RemoteAddon addon;

        private FavoriteItemObject(Map.Entry<FavoritesManager.Item, RemoteAddon> entry) {
            this.item = entry.getKey();
            this.addon = entry.getValue();
        }
    }

    private static final class AddonFavoritesPageSkin extends ToolbarListPageSkin<FavoriteItemObject, AddonFavoritesPage> {

        public AddonFavoritesPageSkin(AddonFavoritesPage skinnable) {
            super(skinnable);
        }

        @Override
        protected List<Node> initializeToolbar(AddonFavoritesPage skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }
    }

    private static final
}
