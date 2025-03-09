/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package com.jfoenix.adapters.skins;

import com.jfoenix.adapters.VirtualFlowAdapter;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.skin.ListViewSkin;
import javafx.scene.control.skin.VirtualFlow;

public class ListViewSkinAdapter<T> extends ListViewSkin<T> {
    public ListViewSkinAdapter(ListView<T> control) {
        super(control);
    }

    @SuppressWarnings("unchecked")
    protected final VirtualFlowAdapter<ListCell<T>> __getFlow() {
        VirtualFlow<ListCell<T>> flow;
        try {
            // Since JavaFX 10
            flow = getVirtualFlow();
        } catch (NoSuchMethodError e) {
            flow = (VirtualFlow<ListCell<T>>) getChildren().get(0);
        }

        return VirtualFlowAdapter.wrap(flow);
    }
}
