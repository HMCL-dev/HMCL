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

import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ModDownloadListPage extends DownloadListPage {
    public ModDownloadListPage(int section, DownloadPage.DownloadCallback callback, boolean versionSelection) {
        super(section, callback, versionSelection);

        supportChinese.set(true);
    }

    @Override
    protected List<CurseAddon> searchImpl(String gameVersion, int category, int section, int pageOffset, String searchFilter, int sort) throws Exception {
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
            return super.searchImpl(gameVersion, category, section, pageOffset, String.join(" ", searchFilters), sort);
        } else {
            return super.searchImpl(gameVersion, category, section, pageOffset, searchFilter, sort);
        }
    }
}
