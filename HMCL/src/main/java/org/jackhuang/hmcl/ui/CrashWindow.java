/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.util.i18n.I18n;

/**
 * @author huangyuhui
 */
public class CrashWindow extends Stage {

    public CrashWindow(String text) {
        Label lblCrash = new Label();
        if (Launcher.UPDATE_CHECKER.isOutOfDate())
            lblCrash.setText(I18n.i18n("launcher.crash_out_dated"));
        else
            lblCrash.setText(I18n.i18n("launcher.crash"));
        lblCrash.setWrapText(true);

        TextArea textArea = new TextArea();
        textArea.setText(text);
        textArea.setEditable(false);

        Button btnContact = new Button();
        btnContact.setText(I18n.i18n("launcher.contact"));
        btnContact.setOnMouseClicked(event -> FXUtils.openLink(Launcher.CONTACT));
        HBox box = new HBox();
        box.setStyle("-fx-padding: 8px;");
        box.getChildren().add(btnContact);
        box.setAlignment(Pos.CENTER_RIGHT);

        BorderPane pane = new BorderPane();
        StackPane stackPane = new StackPane();
        stackPane.setStyle("-fx-padding: 8px;");
        stackPane.getChildren().add(lblCrash);
        pane.setTop(stackPane);
        pane.setCenter(textArea);
        pane.setBottom(box);

        Scene scene = new Scene(pane, 800, 480);
        setScene(scene);
        getIcons().add(new Image("/assets/img/icon.png"));
        setTitle(I18n.i18n("message.error"));

        setOnCloseRequest(e -> System.exit(1));
    }

}
