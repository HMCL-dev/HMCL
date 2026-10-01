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
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerAddressMaskPane extends HBox {
    public final Label atLabel = new Label();
    public final Label realServerLabel = new Label();
    public final Label queryLabel = new Label();

    public ServerAddressMaskPane(@Nullable String serverIP) {
        queryLabel.setOpacity(0.3);
        atLabel.setOpacity(0.3);

        queryLabel.setMinWidth(Region.USE_PREF_SIZE);
        atLabel.setMinWidth(Region.USE_PREF_SIZE);
        realServerLabel.setMinWidth(Region.USE_PREF_SIZE);

        getChildren().add(atLabel);
        getChildren().add(realServerLabel);
        getChildren().add(queryLabel);
        set(serverIP);
    }

    public static void installMask(@NotNull Label label, String text) {
        int length = text.length();
        StringBuilder maskBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            if (c == '@' || c == '?') {
                maskBuilder.append(c);
            } else {
                maskBuilder.append("*");
            }
        }
        String mask = maskBuilder.toString();

        label.setText(mask);

        label.hoverProperty().addListener((obs, wasHover, isHover) -> {
            label.setText(isHover ? text : mask);
        });
    }

    public void set(@Nullable String serverIP) {
        if (serverIP == null) {
            realServerLabel.setText("");
            installMask(atLabel, "");
            installMask(queryLabel, "");
            return;
        }
        int queryStart = serverIP.lastIndexOf('?');
        if (queryStart != -1) {
            installMask(queryLabel, serverIP.substring(queryStart));
            serverIP = serverIP.substring(0, queryStart);
        } else {
            installMask(queryLabel, "");
        }

        int atEnd = serverIP.indexOf('@');
        if (atEnd != -1) {
            installMask(atLabel, serverIP.substring(0, atEnd + 1));
            serverIP = serverIP.substring(atEnd + 1);
        } else {
            installMask(atLabel, "");
        }

        realServerLabel.setText(serverIP);
    }
}
