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

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.FavoritesManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jetbrains.annotations.Nullable;

public class AddonFavoritesPage extends Control implements DecoratorPage, GameInstancePage.GameInstanceLoadable {

    private static final FavoritesManager manager = FavoritesManager.getInstance();

    protected final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final ObjectProperty<HMCLGameRepository.InstanceReference> instanceReference = new SimpleObjectProperty<>();
    private final ObservableList<GameInstanceID> instances = FXCollections.observableArrayList();
    private final ObjectProperty<GameInstanceID> selectedInstance = new SimpleObjectProperty<>();

    private final ListProperty<FavoritesManager.Favorites> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void loadInstance(HMCLGameRepository repository, @Nullable GameInstanceID instanceId) {
        instanceReference.set(new HMCLGameRepository.InstanceReference(repository, instanceId));
        instances.setAll(repository.getDisplayInstanceManifests()
                .map(GameInstanceManifest::id)
                .toList());
        selectedInstance.set(repository.getSelectedInstance());
        refresh();
    }

    public void refresh() {
        setLoading(true);
        Task.runAsync(Schedulers.io(), manager::load)
                .thenRunAsync(Schedulers.javafx(), () -> {
                    items.setAll(manager.getFavorites());
                }).start();
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }
}
