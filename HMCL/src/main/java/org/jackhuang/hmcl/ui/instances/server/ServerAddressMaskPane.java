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
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BoxBlur;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import org.jetbrains.annotations.Nullable;

public class ServerAddressMaskPane extends HBox {
    private final Label atLabel = new Label();
    private final Label realServerLabel = new Label();
    private final Label queryLabel = new Label();

    private final Tooltip atLabelTooltip = new Tooltip();
    private final Tooltip queryLabelTooltip = new Tooltip();

    public ServerAddressMaskPane(@Nullable String serverIP) {
        getChildren().add(atLabel);
        getChildren().add(realServerLabel);
        getChildren().add(queryLabel);

        atLabel.setPickOnBounds(true);
        queryLabel.setPickOnBounds(true);

//        FXUtils.installFastTooltip(atLabel, atLabelTooltip);
//        FXUtils.installFastTooltip(queryLabel, queryLabelTooltip);
        set(serverIP);

        BoxBlur blur = new BoxBlur();
        blur.setIterations(3);

        atLabel.setEffect(blur);
        queryLabel.setEffect(blur);

        atLabel.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            atLabel.setEffect(null);
        });
        atLabel.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            atLabel.setEffect(blur);
        });

        queryLabel.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            queryLabel.setEffect(null);
        });
        queryLabel.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            queryLabel.setEffect(blur);
        });
    }

    public void set(@Nullable String serverIP) {
        if (serverIP == null) {
            atLabel.setText("");
            realServerLabel.setText("");
            queryLabel.setText("");

            atLabelTooltip.setText("");
            queryLabelTooltip.setText("");
            return;
        }
        int queryStart = serverIP.lastIndexOf('?');
        if (queryStart != -1) {
            String queryStr = serverIP.substring(queryStart);
            queryLabel.setText(queryStr);
            atLabelTooltip.setText(queryStr);
            serverIP = serverIP.substring(0, queryStart);
        } else {
            queryLabel.setText("");
            atLabelTooltip.setText("");
        }

        int atEnd = serverIP.indexOf('@');
        if (atEnd != -1) {
            String atStr = serverIP.substring(0, atEnd + 1);
            atLabel.setText(atStr);
            atLabelTooltip.setText(atStr);
            serverIP = serverIP.substring(atEnd + 1);
        } else {
            atLabel.setText("");
            atLabelTooltip.setText("");
        }

        realServerLabel.setText(serverIP);
    }

    public void labelAddStyleClass(String styleClass) {
        atLabel.getStyleClass().add(styleClass);
        realServerLabel.getStyleClass().add(styleClass);
        queryLabel.getStyleClass().add(styleClass);
    }
}
