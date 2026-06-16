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

import org.jackhuang.hmcl.game.LocalizedRemoteAddonRepository;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.MissingResourceException;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class HMCLLocalizedDownloadListPage extends DownloadListPage {
    public static DownloadListPage ofMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.MOD, CurseForgeRemoteAddonRepository.MODS, ModrinthRemoteAddonRepository.MODS);
    }

    public static DownloadListPage ofCurseForgeMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.MOD, CurseForgeRemoteAddonRepository.MODS, null);
    }

    public static DownloadListPage ofModrinthMod(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.MOD, null, ModrinthRemoteAddonRepository.MODS);
    }

    public static DownloadListPage ofModPack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.MODPACK, CurseForgeRemoteAddonRepository.MODPACKS, ModrinthRemoteAddonRepository.MODPACKS);
    }

    public static DownloadListPage ofResourcePack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.RESOURCE_PACK, CurseForgeRemoteAddonRepository.RESOURCE_PACKS, ModrinthRemoteAddonRepository.RESOURCE_PACKS);
    }

    public static DownloadListPage ofCurseForgeResourcePack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.RESOURCE_PACK, CurseForgeRemoteAddonRepository.RESOURCE_PACKS, null);
    }

    public static DownloadListPage ofModrinthResourcePack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        return new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.RESOURCE_PACK, null, ModrinthRemoteAddonRepository.RESOURCE_PACKS);
    }

    public static DownloadListPage ofShaderPack(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        var page = new HMCLLocalizedDownloadListPage(callback, versionSelection, RemoteAddonRepository.Type.SHADER_PACK, CurseForgeRemoteAddonRepository.SHADERS, ModrinthRemoteAddonRepository.SHADER_PACKS);
        page.supportChinese.set(false);
        return page;
    }

    private HMCLLocalizedDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection, RemoteAddonRepository.Type type, CurseForgeRemoteAddonRepository curseForge, ModrinthRemoteAddonRepository modrinth) {
        super(null, callback, versionSelection);

        repository = new Repository(type, curseForge, modrinth);

        supportChinese.set(true);

        boolean supportedCurseForge = CurseForgeRemoteAddonRepository.isAvailable() && curseForge != null;

        downloadSources.setAll("addon.modrinth");
        if (supportedCurseForge) {
            downloadSources.add("addon.curseforge");
        }

        if ("curseforge".equalsIgnoreCase(config().getDefaultAddonSource())) {
            if (supportedCurseForge) {
                downloadSource.set("addon.curseforge");
            } else if (modrinth != null) {
                downloadSource.set("addon.modrinth");
            } else {
                throw new AssertionError("Should not be here.");
            }
        } else {
            if (modrinth != null) {
                downloadSource.set("addon.modrinth");
            } else if (supportedCurseForge) {
                downloadSource.set("addon.curseforge");
            } else {
                throw new AssertionError("Should not be here.");
            }
        }
    }

    private class Repository extends LocalizedRemoteAddonRepository {
        private final RemoteAddonRepository.Type type;
        private final CurseForgeRemoteAddonRepository curseForge;
        private final ModrinthRemoteAddonRepository modrinth;

        public Repository(Type type, CurseForgeRemoteAddonRepository curseForge, ModrinthRemoteAddonRepository modrinth) {
            this.type = type;
            this.curseForge = curseForge;
            this.modrinth = modrinth;
        }

        @Override
        protected RemoteAddonRepository getBackedRemoteModRepository() {
            if ("addon.modrinth".equals(downloadSource.get())) {
                return modrinth;
            } else {
                return curseForge;
            }
        }

        @Override
        protected SortType getBackedRemoteModRepositorySortOrder() {
            if ("addon.modrinth".equals(downloadSource.get())) {
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
    protected String getLocalizedCategory(String category, Object self) {
        if (category.isEmpty()) {
            return "";
        }

        String key = ("addon.modrinth".equals(downloadSource.get()) ? "modrinth" : "curse") + ".category." + category;
        try {
            return I18n.getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.warning("Cannot find key " + key + " in resource bundle");
            if (self instanceof CurseForgeRemoteAddonRepository.Category curseCategory) {
                return curseCategory.getName();
            }
            return category;
        }
    }

    @Override
    protected String getLocalizedOfficialPage() {
        if ("addon.modrinth".equals(downloadSource.get())) {
            return i18n("addon.modrinth");
        } else {
            return i18n("addon.curseforge");
        }
    }
}
