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
package org.jackhuang.hmcl.ui.instances.server;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.jetbrains.annotations.Nullable;

public class ServerAddressMaskHBox extends HBox {
    public final Label realServerLabel = new Label();
    public final Label queryLabel = new Label();

    public ServerAddressMaskHBox(@Nullable String serverIP) {
        queryLabel.setOpacity(0.3);

        getChildren().add(realServerLabel);
        getChildren().add(queryLabel);
        set(serverIP);
    }

    public void set(@Nullable String serverIP) {
        if (serverIP == null) {
            realServerLabel.setText("");
            queryLabel.setText("");
            return;
        }
        int queryStart = serverIP.lastIndexOf('?');
        if (queryStart != -1) {
            String queryPropertyStr = serverIP.substring(queryStart);
            queryLabel.setText(queryPropertyStr);
            serverIP = serverIP.substring(0, queryStart);
        } else {
            queryLabel.setText("");
        }

        realServerLabel.setText(serverIP);
    }
}
