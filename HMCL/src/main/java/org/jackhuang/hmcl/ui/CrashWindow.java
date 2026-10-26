// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.countly.CrashReport;
import org.jackhuang.hmcl.upgrade.UpdateChecker;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class CrashWindow extends Stage {

    public CrashWindow(CrashReport report) {
        Label lblCrash = new Label();
        if (report.getThrowable() instanceof InternalError)
            lblCrash.setText(i18n("launcher.crash.java_internal_error"));
        else if (UpdateChecker.isOutdated())
            lblCrash.setText(i18n("launcher.crash.hmcl_out_dated"));
        else
            lblCrash.setText(i18n("launcher.crash"));
        lblCrash.setWrapText(true);

        TextArea textArea = new TextArea();
        textArea.setText(report.getDisplayText());
        textArea.setEditable(false);

        Button btnContact = new Button();
        btnContact.setText(i18n("launcher.contact"));
        btnContact.setOnAction(event -> FXUtils.openLink(Metadata.CONTACT_URL));
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
        FXUtils.setIcon(this);
        setTitle(i18n("message.error"));

        FXUtils.addMacOSCloseWindowHandler(this, null);

        setOnCloseRequest(e -> javafx.application.Platform.exit());
    }

}
