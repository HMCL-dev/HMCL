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

import org.jackhuang.hmcl.game.LocalizedRemoteModRepository;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.MissingResourceException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ResourcePackDownloadListPage extends DownloadListPage {
    public ResourcePackDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(null, callback, versionSelection);

        repository = new Repository();

        supportChinese.set(true);
        downloadSources.get().setAll("mods.curseforge", "mods.modrinth");
        if (CurseForgeRemoteModRepository.isAvailable())
            downloadSource.set("mods.curseforge");
        else
            downloadSource.set("mods.modrinth");
    }

    private class Repository extends LocalizedRemoteModRepository {

        @Override
        protected RemoteModRepository getBackedRemoteModRepository() {
            if ("mods.modrinth".equals(downloadSource.get())) {
                return ModrinthRemoteModRepository.RESOURCE_PACKS;
            } else {
                return CurseForgeRemoteModRepository.RESOURCE_PACKS;
            }
        }

        @Override
        protected SortType getBackedRemoteModRepositorySortOrder() {
            if ("mods.modrinth".equals(downloadSource.get())) {
                return SortType.NAME;
            } else {
                return SortType.POPULARITY;
            }
        }

        @Override
        public Type getType() {
            return Type.MOD;
        }
    }

    @Override
    protected String getLocalizedCategory(String category) {
        String key;
        if ("mods.modrinth".equals(downloadSource.get())) {
            key = "modrinth.category." + category;
        } else {
            key = "curse.category." + category;
        }

        try {
            return I18n.getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.warning("Cannot find key " + key + " in resource bundle", e);
            return category;
        }
    }

    @Override
    protected String getLocalizedOfficialPage() {
        if ("mods.modrinth".equals(downloadSource.get())) {
            return i18n("mods.modrinth");
        } else {
            return i18n("mods.curseforge");
        }
    }
}
