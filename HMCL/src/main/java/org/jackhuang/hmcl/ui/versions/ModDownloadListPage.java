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

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadListPage extends DownloadListPage {
    public ModDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(null, callback, versionSelection);

        repository = new Repository();


        supportChinese.set(true);
        downloadSources.get().setAll("mods.curseforge", "mods.modrinth");
        downloadSource.set("mods.curseforge");
    }

    private class Repository implements RemoteModRepository {

        private RemoteModRepository getBackedRemoteModRepository() {
            if ("mods.modrinth".equals(downloadSource.get())) {
                return ModrinthRemoteModRepository.INSTANCE;
            } else {
                return CurseForgeRemoteModRepository.MODS;
            }
        }

        @Override
        public Type getType() {
            return Type.MOD;
        }

        @Override
        public Stream<RemoteMod> search(String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort, SortOrder sortOrder) throws IOException {
            String newSearchFilter;
            if (StringUtils.CHINESE_PATTERN.matcher(searchFilter).find()) {
                List<ModTranslations.Mod> mods = ModTranslations.MOD.searchMod(searchFilter);
                List<String> searchFilters = new ArrayList<>();
                int count = 0;
                for (ModTranslations.Mod mod : mods) {
                    String englishName = mod.getName();
                    if (StringUtils.isNotBlank(mod.getSubname())) {
                        englishName = mod.getSubname();
                    }

                    searchFilters.add(englishName);

                    count++;
                    if (count >= 3) break;
                }
                newSearchFilter = String.join(" ", searchFilters);
            } else {
                newSearchFilter = searchFilter;
            }

            return getBackedRemoteModRepository().search(gameVersion, category, pageOffset, pageSize, newSearchFilter, sort, sortOrder);
        }

        @Override
        public Stream<Category> getCategories() throws IOException {
            return getBackedRemoteModRepository().getCategories();
        }

        @Override
        public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) throws IOException {
            return getBackedRemoteModRepository().getRemoteVersionByLocalFile(localModFile, file);
        }

        @Override
        public RemoteMod getModById(String id) throws IOException {
            return getBackedRemoteModRepository().getModById(id);
        }

        @Override
        public RemoteMod.File getModFile(String modId, String fileId) throws IOException {
            return getBackedRemoteModRepository().getModFile(modId, fileId);
        }

        @Override
        public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
            return getBackedRemoteModRepository().getRemoteVersionsById(id);
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
