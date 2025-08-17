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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.mod.ModpackFile;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.modrinth.ModrinthManifest;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.Holder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * This page is used to ask player which optional file they want to install
 * Support CurseForge modpack yet
 */
public class OptionalFilesSelectionPane extends BorderPane {
    private Set<ModpackFile> selected = new HashSet<>();
    private Runnable retry;

    private final VBox title = new VBox();
    private final Label retryOptionalFiles = new Label(i18n("modpack.retry_optional_files"));
    private final Label pendingOptionalFiles = new Label(i18n("modpack.pending_optional_files"));
    private final Label noOptionalFiles = new Label(i18n("modpack.no_optional_files"));

    private final JFXListView<ModpackFile> list = new JFXListView<>();

    public OptionalFilesSelectionPane() {
        retryOptionalFiles.setOnMouseClicked(e -> {
            title.getChildren().remove(retryOptionalFiles);
            retry.run();
        });

        Label label = new Label(i18n("modpack.optional_files"));
        label.getStyleClass().add("subtitle");
        title.getChildren().add(label);
        this.setTop(title);
        setPending();
    }

    public void setOptionalFileList(List<? extends ModpackFile> files) {
        Holder<Object> holder = new Holder<>();
        list.setCellFactory(it -> new OptionalFileEntry(list, holder));
        List<ModpackFile> optionalFiles = files.stream().filter(ModpackFile::isOptional).collect(Collectors.toList());
        list.getItems().setAll(optionalFiles);
        selected = new HashSet<>(files);
        if (!optionalFiles.isEmpty()) {
            this.setCenter(list);
        } else {
            this.setCenter(noOptionalFiles);
        }
    }

    public void setPending() {
        this.setCenter(pendingOptionalFiles);
        retry = null;
    }

    public void setRetry(Runnable retry) {
        title.getChildren().add(retryOptionalFiles);
        this.retry = retry;
    }

    public Set<ModpackFile> getSelected() {
        return selected;
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
                    selected.add(currentFile);
                } else {
                    selected.remove(currentFile);
                }
            }
        };

        public OptionalFileEntry(JFXListView<ModpackFile> listView, Holder<Object> lastCell) {
            super(listView, lastCell);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();
            container.getChildren().setAll(checkBox, content);

            infoButton.getStyleClass().add("toggle-icon4");
            infoButton.setGraphic(FXUtils.limitingSize(SVG.INFO.createIcon(Theme.blackFill(), 24), 24, 24));
            container.getChildren().add(infoButton);
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModpackFile item, boolean empty) {
            if (empty) return;
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
            checkBox.setSelected(selected.contains(item));
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
}
