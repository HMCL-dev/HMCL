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

import com.jfoenix.controls.JFXListView;
import org.jackhuang.hmcl.mod.LocalMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.ui.construct.DialogPane;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModUpdatesDialog extends DialogPane {

    public ModUpdatesDialog(List<LocalMod.ModUpdate> updates) {
        setTitle(i18n("mods.check_updates"));

        JFXListView<LocalMod.ModUpdate> listView = new JFXListView<>();
        listView.getItems().setAll(updates);
        listView.setCellFactory(l -> new ModUpdateCell(listView));
        setBody(listView);
    }

    public static class ModUpdateCell extends MDListCell<LocalMod.ModUpdate> {
        TwoLineListItem content = new TwoLineListItem();

        public ModUpdateCell(JFXListView<LocalMod.ModUpdate> listView) {
            super(listView);

            getContainer().getChildren().setAll(content);
        }

        @Override
        protected void updateControl(LocalMod.ModUpdate item, boolean empty) {
            if (empty) return;
            ModTranslations.Mod mod = ModTranslations.getModById(item.getLocalMod().getId());
            content.setTitle(mod != null ? mod.getDisplayName() : item.getCurrentVersion().getName());
            content.setSubtitle(item.getLocalMod().getFileName());
            content.getTags().setAll();

            if (item.getCurrentVersion().getSelf() instanceof CurseAddon.LatestFile) {
                content.getTags().add("Curseforge");
            } else if (item.getCurrentVersion().getSelf() instanceof ModrinthRemoteModRepository.ModVersion) {
                content.getTags().add("Modrinth");
            }
        }
    }
}
