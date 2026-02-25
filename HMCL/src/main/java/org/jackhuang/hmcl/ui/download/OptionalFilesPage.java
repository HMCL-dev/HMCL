/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.mod.ModpackFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXHyperlink;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.Optional;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * This page is used to ask player which optional file they want to install
 * Support CurseForge modpack yet
 */
public class OptionalFilesPage extends SpinnerPane implements WizardPage {
    private final ObservableSet<ModpackFile> excludedFiles;
    private final VBox head = new VBox();
    private final JFXListView<ModpackFile> body = new JFXListView<>();
    private final VBox tail = new VBox();


    // FIXME: restore retry functionality
    public OptionalFilesPage(Runnable install, ObservableBooleanValue loading,
            ObservableBooleanValue successful, ObservableList<ModpackFile> optionalFiles,
            ObservableSet<ModpackFile> excludedFiles) {
        this.excludedFiles = excludedFiles;

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(borderPane, 500);
        ComponentList componentList = new ComponentList();
        {
            head.getChildren().add(new Label(i18n("modpack.optional_files")));

            var descPane = new BorderPane();
            var btnInstall = FXUtils.newRaisedButton(i18n("button.install"));
            descPane.setRight(btnInstall);
            btnInstall.setOnAction(e -> install.run());
            tail.getChildren().add(descPane);

            componentList.getContent().setAll(head, body, tail);
        }

        borderPane.getChildren().setAll(componentList);
        setContent(borderPane);
        body.setCellFactory(it -> new OptionalFileEntry(body));
        body.setItems(optionalFiles);

        loadingProperty().bind(loading);
    }

    private class OptionalFileEntry extends MDListCell<ModpackFile> {
        private JFXCheckBox checkBox = new JFXCheckBox();
        private TwoLineListItem content = new TwoLineListItem();
        private JFXButton infoButton = new JFXButton();
        private HBox container = new HBox(8);
        private Label text1 = new Label();
        private ModpackFile currentFile = null;
        private ChangeListener<Boolean> selectedListener = (observable, oldValue, newValue) -> {
            if (currentFile != null) {
                if (newValue) {
                    excludedFiles.remove(currentFile);
                } else {
                    excludedFiles.add(currentFile);
                }
            }
        };

        public OptionalFileEntry(JFXListView<ModpackFile> listView) {
            super(listView);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();
            container.getChildren().setAll(checkBox, content);

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(SVG.INFO.createIcon());
            container.getChildren().add(infoButton);
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModpackFile item, boolean empty) {
            if (empty)
                return;
            checkBox.selectedProperty().removeListener(selectedListener);
            currentFile = item;
            String name = item.getFileName();
            text1.setText(name);
            if (name != null) {
                content.setTitle(name);
            } else {
                content.setTitle(i18n("modpack.unknown_optional_file"));
            }
            Optional<RemoteMod> mod = item.getMod();
            RemoteMod mod1 = mod == null ? null : mod.orElse(null);
            if (mod1 != null) {
                content.setSubtitle(mod1.getTitle());
                infoButton.setOnMouseClicked(e -> Controllers.dialog(new ModInfo(mod1)));
                infoButton.setManaged(true);
                infoButton.setVisible(true);
            } else {
                content.setSubtitle("");
                infoButton.setOnMouseClicked(null);
                infoButton.setManaged(false);
                infoButton.setVisible(false);
            }
            checkBox.setSelected(!excludedFiles.contains(item));
            checkBox.selectedProperty().addListener(selectedListener);
        }
    }

    private static class ModInfo extends JFXDialogLayout {
        public ModInfo(RemoteMod mod) {
            HBox container = new HBox(8);
            ImageView imageView = new ImageView(mod.getIconUrl());
            imageView.setFitHeight(32);
            imageView.setFitWidth(32);
            container.getChildren().add(imageView);

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(mod.getTitle());
            title.setSubtitle(mod.getAuthor());
            container.getChildren().add(title);
            setHeading(container);

            Label description = new Label(mod.getDescription());
            setBody(description);

            JFXHyperlink pageButton = new JFXHyperlink(i18n("mods.url"));
            pageButton.setOnAction(e -> FXUtils.openLink(mod.getPageUrl()));
            getActions().add(pageButton);

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    @Override
    public String getTitle() {
        return i18n("modpack.optional_files"); // FIXME: is this title appropriate?
    }
}
