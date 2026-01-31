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
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
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
        refresh();
    }

    public void refresh() {
        if (modManager == null) return;
        try {
            if (modManager.getModsDirectory() != null) {
                LOG.info("Refreshing built-in mod list for folder: " + modManager.getModsDirectory().toAbsolutePath());
            }
        } catch (Exception e) {
            LOG.warning("Failed to log mods directory", e);
        }

        LOG.info("Refreshing built-in mod list page...");

        getItems().clear();
        setLoading(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                modManager.refreshMods();
                return modManager.getMods().stream()
                        .filter(mod -> {
                            try {
                                return mod != null && mod.hasBundledMods();
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .map(ModListPageSkin.ModInfoObject::new)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                LOG.warning("Failed to refresh built-in mods", e);
                return null;
            }
        }, Schedulers.io()).whenCompleteAsync((list, ex) -> {
            if (ex != null) {
                LOG.warning("Async task failed in BuiltInModListPage", ex);
            } else if (list != null) {
                getItems().setAll(list);
                LOG.info("Built-in mod list refreshed, found " + list.size() + " mods with JIJ.");
            }
            setLoading(false);
        }, Schedulers.javafx());
    }
}