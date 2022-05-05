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

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModpackDownloadListPage extends DownloadListPage {
    public ModpackDownloadListPage(DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(null, callback, versionSelection);

        repository = new Repository();


        supportChinese.set(true);
    }

    private class Repository implements RemoteModRepository {

        @Override
        public Type getType() {
            return Type.MODPACK;
        }

        @Override
        public Stream<RemoteMod> search(String gameVersion, Category category, int pageOffset, int pageSize, String searchFilter, SortType sort) throws IOException {
            String newSearchFilter;
            if (StringUtils.CHINESE_PATTERN.matcher(searchFilter).find()) {
                List<ModTranslations.Mod> mods = ModTranslations.MODPACK.searchMod(searchFilter);
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

            return CurseForgeRemoteModRepository.MODPACKS.search(gameVersion, category, pageOffset, pageSize, newSearchFilter, sort);
        }

        @Override
        public Stream<Category> getCategories() throws IOException {
            return CurseForgeRemoteModRepository.MODPACKS.getCategories();
        }

        @Override
        public Optional<RemoteMod.Version> getRemoteVersionByLocalFile(LocalModFile localModFile, Path file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RemoteMod getModById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Stream<RemoteMod.Version> getRemoteVersionsById(String id) throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    protected String getLocalizedCategory(String category) {
        return i18n("curse.category." + category);
    }

    @Override
    protected String getLocalizedOfficialPage() {
        return i18n("mods.curseforge");
    }
}
