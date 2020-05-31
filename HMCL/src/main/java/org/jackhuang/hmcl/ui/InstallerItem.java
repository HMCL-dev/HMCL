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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.effects.JFXDepthManager;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.util.i18n.I18n;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends Control {
    private final String id;
    private final String imageUrl;
    public final StringProperty libraryVersion = new SimpleStringProperty();
    public final StringProperty incompatibleLibraryName = new SimpleStringProperty();
    public final BooleanProperty incompatibleWithGame = new SimpleBooleanProperty();
    public final BooleanProperty removable = new SimpleBooleanProperty();
    public final BooleanProperty upgradable = new SimpleBooleanProperty(false);
    public final BooleanProperty installable = new SimpleBooleanProperty(true);
    public final ObjectProperty<EventHandler<? super MouseEvent>> removeAction = new SimpleObjectProperty<>();
    public final ObjectProperty<EventHandler<? super MouseEvent>> action = new SimpleObjectProperty<>();

    public InstallerItem(LibraryAnalyzer.LibraryType id) {
        this(id.getPatchId());
    }

    public InstallerItem(String id) {
        this.id = id;

        switch (id) {
            case "game":
                imageUrl = "/assets/img/grass.png";
                break;
            case "fabric":
                imageUrl = "/assets/img/fabric.png";
                break;
            case "forge":
                imageUrl = "/assets/img/forge.png";
                break;
            case "liteloader":
                imageUrl = "/assets/img/chicken.png";
                break;
            case "optifine":
                imageUrl = "/assets/img/command.png";
                break;
            default:
                imageUrl = null;
                break;
        }
    }

    public void setState(String libraryVersion, boolean incompatibleWithGame, boolean removable) {
        this.libraryVersion.set(libraryVersion);
        this.incompatibleWithGame.set(incompatibleWithGame);
        this.removable.set(removable);
    }

    public String getLibraryId() {
        return id;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallerItemSkin(this);
    }

    public static class InstallerItemGroup {
        public InstallerItem game = new InstallerItem(MINECRAFT);
        public InstallerItem fabric = new InstallerItem(FABRIC);
        public InstallerItem forge = new InstallerItem(FORGE);
        public InstallerItem liteLoader = new InstallerItem(LITELOADER);
        public InstallerItem optiFine = new InstallerItem(OPTIFINE);

        public InstallerItemGroup() {
            forge.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                return null;
            }, fabric.libraryVersion));

            liteLoader.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                return null;
            }, fabric.libraryVersion));

            optiFine.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                return null;
            }, fabric.libraryVersion));

            fabric.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (liteLoader.libraryVersion.get() != null) return LITELOADER.getPatchId();
                if (optiFine.libraryVersion.get() != null) return OPTIFINE.getPatchId();
                if (forge.libraryVersion.get() != null) return FORGE.getPatchId();
                return null;
            }, optiFine.libraryVersion, forge.libraryVersion));
        }

        public InstallerItem[] getLibraries() {
             return new InstallerItem[]{game, fabric, forge, liteLoader, optiFine};
        }
    }

    public static class InstallerItemSkin extends SkinBase<InstallerItem> {

        InstallerItemSkin(InstallerItem control) {
            super(control);

            HBox hbox = new HBox();
            getChildren().setAll(hbox);
            JFXDepthManager.setDepth(hbox, 1);

            hbox.getStyleClass().add("card");

            hbox.setAlignment(Pos.CENTER_LEFT);

            if (control.imageUrl != null) {
                hbox.getChildren().add(FXUtils.limitingSize(new ImageView(new Image(control.imageUrl, 32, 32, true, true)), 32, 32));
            }

            Label nameLabel = new Label();
            hbox.getChildren().add(nameLabel);
            nameLabel.setPrefWidth(80);
            nameLabel.textProperty().set(I18n.hasKey("install.installer." + control.id) ? i18n("install.installer." + control.id) : control.id);
            HBox.setMargin(nameLabel, new Insets(0, 4, 0, 4));

            Label label = new Label();
            hbox.getChildren().add(label);
            label.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(label, Priority.ALWAYS);
            label.textProperty().bind(Bindings.createStringBinding(() -> {
                String incompatibleWith = control.incompatibleLibraryName.get();
                String version = control.libraryVersion.get();
                if (control.incompatibleWithGame.get()) {
                    return i18n("install.installer.change_version", version);
                } else if (incompatibleWith != null) {
                    return i18n("install.installer.incompatible", i18n("install.installer." + incompatibleWith));
                } else if (version == null) {
                    return i18n("install.installer.not_installed");
                } else {
                    return i18n("install.installer.version", version);
                }
            }, control.incompatibleLibraryName, control.incompatibleWithGame, control.libraryVersion));
            BorderPane.setMargin(label, new Insets(0, 0, 0, 8));
            BorderPane.setAlignment(label, Pos.CENTER_LEFT);

            JFXButton closeButton = new JFXButton();
            closeButton.setGraphic(SVG.close(Theme.blackFillBinding(), -1, -1));
            closeButton.getStyleClass().add("toggle-icon4");
            closeButton.visibleProperty().bind(control.removable);
            closeButton.managedProperty().bind(closeButton.visibleProperty());
            closeButton.onMouseClickedProperty().bind(control.removeAction);
            hbox.getChildren().add(closeButton);

            JFXButton arrowButton = new JFXButton();
            arrowButton.graphicProperty().bind(Bindings.createObjectBinding(() -> control.upgradable.get()
                            ? SVG.update(Theme.blackFillBinding(), -1, -1)
                            : SVG.arrowRight(Theme.blackFillBinding(), -1, -1),
                    control.upgradable));
            arrowButton.getStyleClass().add("toggle-icon4");
            arrowButton.visibleProperty().bind(Bindings.createBooleanBinding(
                    () -> control.installable.get() && control.incompatibleLibraryName.get() == null,
                    control.installable, control.incompatibleLibraryName));
            arrowButton.managedProperty().bind(arrowButton.visibleProperty());
            arrowButton.onMouseClickedProperty().bind(control.action);
            hbox.getChildren().add(arrowButton);

            FXUtils.onChangeAndOperate(arrowButton.visibleProperty(), clickable -> {
                if (clickable) {
                    hbox.onMouseClickedProperty().bind(control.action);
                    hbox.setCursor(Cursor.HAND);
                } else {
                    hbox.onMouseClickedProperty().unbind();
                    hbox.onMouseClickedProperty().set(null);
                    hbox.setCursor(Cursor.DEFAULT);
                }
            });
        }
    }
}
