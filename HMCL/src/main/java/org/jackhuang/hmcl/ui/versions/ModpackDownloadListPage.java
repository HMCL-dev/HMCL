/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModpackDownloadListPage extends DownloadListPage {
    public ModpackDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(null, callback, versionSelection);

        repository = new Repository();

        supportChinese.set(true);
        downloadSources.get().setAll("mods.curseforge", "mods.modrinth");
        downloadSource.set("mods.curseforge");
    }

    private class Repository extends LocalizedRemoteModRepository {

        @Override
        protected RemoteModRepository getBackedRemoteModRepository() {
            if ("mods.modrinth".equals(downloadSource.get())) {
                return ModrinthRemoteModRepository.MODPACKS;
            } else {
                return CurseForgeRemoteModRepository.MODPACKS;
            }
        }

        @Override
        public Type getType() {
            return Type.MODPACK;
        }
    }

    @Override
    protected String getLocalizedCategory(String category) {
        if ("mods.modrinth".equals(downloadSource.get())) {
            return i18n("modrinth.category." + category);
        } else {
            return i18n("curse.category." + category);
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
