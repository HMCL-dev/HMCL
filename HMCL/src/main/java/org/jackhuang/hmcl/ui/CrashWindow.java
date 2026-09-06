/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.countly.CrashReport;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.LauncherLogExporter;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class CrashWindow extends Stage {
    private static final Lazy<CrashWindow> instance = new Lazy<>(CrashWindow::new);

    public static CrashWindow getInstance() {
        return instance.get();
    }

    private final TextArea textArea = new TextArea();

    private CrashWindow() {
        Label lblCrash = new Label();
        lblCrash.setWrapText(true);

        if (UpdateChecker.isOutdated()) {
            lblCrash.setText(i18n("launcher.crash.hmcl_out_dated"));
        } else {
            lblCrash.setText(i18n("launcher.crash"));
        }

        StackPane exportPane = new StackPane();
        ProgressIndicator progressIndicator = new ProgressIndicator();
        FXUtils.setLimitHeight(progressIndicator, 20);

        Button btnExport = new Button();
        exportPane.getChildren().setAll(btnExport);
        btnExport.setText(i18n("settings.launcher.launcher_log.export"));
        btnExport.setOnAction(event -> {
            exportPane.getChildren().setAll(progressIndicator);
            try {
                Path path = LauncherLogExporter.exportLogsAsZip();
                FXUtils.showFileInExplorer(path);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, i18n("settings.launcher.launcher_log.export.success", path));
                alert.setTitle(i18n("settings.launcher.launcher_log.export"));
                alert.showAndWait();
            } catch (IOException e) {
                LOG.warning("Failed to export launcher logs", e);
                Alert alert = new Alert(Alert.AlertType.WARNING, i18n("settings.launcher.launcher_log.export.failed") + "\n" + StringUtils.getStackTrace(e));
                alert.setTitle(i18n("message.error"));
                alert.setContentText(StringUtils.getStackTrace(e));
                alert.showAndWait();
            }
            exportPane.getChildren().setAll(btnExport);
        });

        Button btnContact = new Button();
        btnContact.setText(i18n("launcher.contact"));
        btnContact.setOnAction(event -> FXUtils.openLink(Metadata.CONTACT_URL));
        HBox box = new HBox(8);
        box.setStyle("-fx-padding: 8px;");
        box.getChildren().setAll(exportPane, btnContact);
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

        setOnCloseRequest(e -> Platform.exit());
    }

    public void addCrashReport(CrashReport report) {
        textArea.setText(textArea.getText() + "\n\n" + report.getDisplayText());
        textArea.setEditable(false);
        this.requestFocus();
    }
}
