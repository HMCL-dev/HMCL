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

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class HMCLLocalizedDownloadListPage extends DownloadListPage {
    public static DownloadListPage ofMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.MOD, CurseForgeRemoteModRepository.MODS, ModrinthRemoteModRepository.MODS);
    }

    public static DownloadListPage ofCurseForgeMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.MOD, CurseForgeRemoteModRepository.MODS, null);
    }

    public static DownloadListPage ofModrinthMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.MOD, null, ModrinthRemoteModRepository.MODS);
    }

    public static DownloadListPage ofModPack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.MODPACK, CurseForgeRemoteModRepository.MODPACKS, ModrinthRemoteModRepository.MODPACKS);
    }

    public static DownloadListPage ofResourcePack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.RESOURCE_PACK, CurseForgeRemoteModRepository.RESOURCE_PACKS, ModrinthRemoteModRepository.RESOURCE_PACKS);
    }

    public static DownloadListPage ofShaderPack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteModRepository.Type.SHADER_PACK, null, ModrinthRemoteModRepository.SHADER_PACKS);
    }

    private HMCLLocalizedDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection, RemoteModRepository.Type type, CurseForgeRemoteModRepository curseForge, ModrinthRemoteModRepository modrinth) {
        super(null, callback, versionSelection);

        repository = new Repository(type, curseForge, modrinth);

        supportChinese.set(true);
        downloadSources.get().setAll("mods.modrinth", "mods.curseforge");
        if (modrinth != null) {
            downloadSource.set("mods.modrinth");
        } else if (curseForge != null) {
            downloadSource.set("mods.curseforge");
        } else {
            throw new AssertionError("Should not be here.");
        }
    }

    private class Repository extends LocalizedRemoteModRepository {
        private final RemoteModRepository.Type type;
        private final CurseForgeRemoteModRepository curseForge;
        private final ModrinthRemoteModRepository modrinth;

        public Repository(Type type, CurseForgeRemoteModRepository curseForge, ModrinthRemoteModRepository modrinth) {
            this.type = type;
            this.curseForge = curseForge;
            this.modrinth = modrinth;
        }

        @Override
        protected RemoteModRepository getBackedRemoteModRepository() {
            if ("mods.modrinth".equals(downloadSource.get())) {
                return modrinth;
            } else {
                return curseForge;
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
            return type;
        }
    }

    @Override
    protected String getLocalizedCategory(String category) {
        if (category.isEmpty()) {
            return "";
        }

        String key = ("mods.modrinth".equals(downloadSource.get()) ? "modrinth" : "curse") + ".category." + category;
        try {
            return I18n.getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.warning("Cannot find key " + key + " in resource bundle");
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
