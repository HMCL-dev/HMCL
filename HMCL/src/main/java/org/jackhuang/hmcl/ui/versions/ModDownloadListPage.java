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

import org.jackhuang.hmcl.mod.DownloadManager;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.mod.modrinth.Modrinth;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadListPage extends DownloadListPage {
    public ModDownloadListPage(int section, DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(section, callback, versionSelection);

        supportChinese.set(true);
        downloadSources.get().setAll("mods.curseforge", "mods.modrinth");
        downloadSource.set("mods.curseforge");
    }

    @Override
    protected Stream<DownloadManager.Mod> searchImpl(String gameVersion, int category, int section, int pageOffset, String searchFilter, int sort) throws Exception {
        if (StringUtils.CHINESE_PATTERN.matcher(searchFilter).find()) {
            List<ModTranslations.Mod> mods = ModTranslations.searchMod(searchFilter);
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
            return search(gameVersion, category, section, pageOffset, String.join(" ", searchFilters), sort);
        } else {
            return search(gameVersion, category, section, pageOffset, searchFilter, sort);
        }
    }

    private Stream<DownloadManager.Mod> search(String gameVersion, int category, int section, int pageOffset, String searchFilter, int sort) throws Exception {
        if ("mods.modrinth".equals(downloadSource.get())) {
            return Modrinth.searchPaginated(gameVersion, pageOffset, searchFilter).stream().map(Modrinth.ModResult::toMod);
        } else {
            return CurseModManager.searchPaginated(gameVersion, category, section, pageOffset, searchFilter, sort).stream().map(CurseAddon::toMod);
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
