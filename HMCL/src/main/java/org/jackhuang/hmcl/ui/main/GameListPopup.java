/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXListCell;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;

import java.util.List;

public class GameListPopup extends StackPane {
    public GameListPopup(Profile profile, ObservableList<Version> instances) {
        var list = new JFXListView<Version>();
        Bindings.bindContent(list.getItems(), instances);

        list.setCellFactory(listView -> new JFXListCell<>() {
        });

        this.getChildren().add(list);
    }

    private static final class Cell extends JFXListCell<Version> {

    }
}
