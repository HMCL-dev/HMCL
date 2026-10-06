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
package org.jackhuang.hmcl.ui.patchpack;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.patchpack.PatchpackHelper;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.construct.URLValidator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// The first page of the patch pack installation wizard, which lets the user choose the patch pack
/// archive to install.
@NotNullByDefault
public final class PatchPackSelectionPage extends VBox implements WizardPage {
    private final WizardController controller;

    /// Creates the patch pack source selection page.
    ///
    /// @param controller the wizard controller
    public PatchPackSelectionPage(WizardController controller) {
        this.controller = controller;

        Label title = new Label(i18n("patchpack.choose"));
        title.setPadding(new Insets(8));

        this.getStyleClass().add("jfx-list-view");
        this.setMaxSize(400, 150);
        this.setSpacing(8);
        this.getChildren().setAll(
                title,
                createButton("local", this::onChooseLocalFile),
                createButton("remote", this::onChooseRemoteFile)
        );

        FXUtils.applyDragListener(this, PatchpackHelper::isPatchPackByExtension, files -> {
            controller.getSettings().put(PatchpackInstallWizardProvider.PATCH_PACK_FILE, files.get(0));
            controller.onNext();
        });
    }

    /// Creates one of the buttons of this page.
    ///
    /// @param type   the localization key suffix describing the source
    /// @param action the action performed when the button is clicked
    /// @return the created button
    private JFXButton createButton(String type, Runnable action) {
        JFXButton button = new JFXButton();

        button.getStyleClass().add("card");
        button.setStyle("-fx-cursor: HAND;");
        button.prefWidthProperty().bind(this.widthProperty());
        button.setOnAction(e -> action.run());

        BorderPane graphic = new BorderPane();
        graphic.setMouseTransparent(true);
        graphic.setLeft(new TwoLineListItem(i18n("patchpack.choose." + type), i18n("patchpack.choose." + type + ".detail")));

        SVGPath arrow = SVG.ARROW_FORWARD.createIcon();
        BorderPane.setAlignment(arrow, Pos.CENTER);
        graphic.setRight(arrow);

        button.setGraphic(graphic);

        JFXDepthManager.setDepth(button, 1);

        return button;
    }

    /// Asks the user for a patch pack archive stored on this computer.
    private void onChooseLocalFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("patchpack.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("patchpack"), "*.zip"));
        Path selectedFile = Controllers.showOpenDialog(chooser);
        if (selectedFile == null)
            return;

        controller.getSettings().put(PatchpackInstallWizardProvider.PATCH_PACK_FILE, selectedFile);
        controller.onNext();
    }

    /// Downloads a patch pack archive from a URL provided by the user.
    ///
    /// The download happens in a task dialog so that the user can cancel it, and the wizard moves
    /// on to the next page only after the archive has been downloaded successfully.
    private void onChooseRemoteFile() {
        Controllers.prompt(i18n("patchpack.choose.remote.tooltip"), (url, handler) -> {
            // The URL may not end with ".zip", so the file is downloaded before its content is read.
            Path patchPack;
            try {
                patchPack = Files.createTempFile("patchpack", ".zip");
            } catch (Exception e) {
                handler.reject(i18n("message.failed"));
                return;
            }

            try {
                Controllers.taskDialog(
                        new FileDownloadTask(url, patchPack)
                                .whenComplete(Schedulers.javafx(), e -> {
                                    if (e == null) {
                                        handler.resolve();
                                        controller.getSettings().put(PatchpackInstallWizardProvider.PATCH_PACK_FILE, patchPack);
                                        controller.onNext();
                                    } else {
                                        // The download task also reports cancellation as a failure.
                                        handler.reject(StringUtils.isBlank(e.getMessage())
                                                ? i18n("install.failed.downloading.detail", url)
                                                : e.getMessage());
                                    }
                                }),
                        i18n("message.downloading"),
                        TaskCancellationAction.NORMAL);
            } catch (Exception e) {
                handler.reject(i18n("message.failed"));
            }
        }, "", new URLValidator());
    }

    @Override
    public void cleanup(SettingsMap settings) {
    }

    @Override
    public String getTitle() {
        return i18n("patchpack.task.install");
    }
}
