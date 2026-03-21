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
package org.jackhuang.hmcl.ui.versions;

import javafx.scene.control.Skin;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.PageAware;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class BuiltInModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements VersionPage.VersionLoadable, PageAware {

    private ModManager modManager;

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BuiltInModListPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        getItems().clear();
        this.modManager = profile.getRepository().getModManager(id);
        loadMods(false);
    }

    /**
     * Called by the refresh button in the skin — forces a full rescan of the mods directory.
     */
    public void refresh() {
        loadMods(true);
    }

    private void loadMods(boolean forceRefresh) {
        if (modManager == null) return;

        getItems().clear();
        setLoading(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                if (forceRefresh) {
                    modManager.refreshMods();
                }
                return modManager.getMods().stream()
                        .filter(mod -> mod != null && mod.hasBundledMods())
                        .map(ModListPageSkin.ModInfoObject::new)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOG.warning("Failed to load built-in mods", e);
                return List.<ModListPageSkin.ModInfoObject>of();
            }
        }, Schedulers.io()).whenCompleteAsync((list, ex) -> {
            if (ex != null) {
                LOG.warning("Async task failed in BuiltInModListPage", ex);
            } else {
                getItems().setAll(list);
            }
            setLoading(false);
        }, Schedulers.javafx());
    }
}
