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
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
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
    public final StringProperty dependencyName = new SimpleStringProperty();
    public final BooleanProperty incompatibleWithGame = new SimpleBooleanProperty();
    public final BooleanProperty removable = new SimpleBooleanProperty();
    public final BooleanProperty upgradable = new SimpleBooleanProperty(false);
    public final BooleanProperty installable = new SimpleBooleanProperty(true);
    public final ObjectProperty<EventHandler<? super MouseEvent>> removeAction = new SimpleObjectProperty<>();
    public final ObjectProperty<EventHandler<? super MouseEvent>> action = new SimpleObjectProperty<>();

    private Style style = Style.LIST_ITEM;

    public enum Style {
        LIST_ITEM,
        CARD,
    }

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
            case "fabric-api":
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
            case "quilt":
            case "quilt-api":
                imageUrl = "/assets/img/quilt.png";
                break;
            default:
                imageUrl = null;
                break;
        }
    }

    public void setStyleMode(Style style) {
        this.style = style;
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
        public final InstallerItem game = new InstallerItem(MINECRAFT);
        public final InstallerItem fabric = new InstallerItem(FABRIC);
        public final InstallerItem fabricApi = new InstallerItem(FABRIC_API);
        public final InstallerItem forge = new InstallerItem(FORGE);
        public final InstallerItem liteLoader = new InstallerItem(LITELOADER);
        public final InstallerItem optiFine = new InstallerItem(OPTIFINE);
        public final InstallerItem quilt = new InstallerItem(QUILT);
        public final InstallerItem quiltApi = new InstallerItem(QUILT_API);

        public InstallerItemGroup() {
            forge.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                if (quilt.libraryVersion.get() != null) return QUILT.getPatchId();
                return null;
            }, fabric.libraryVersion, quilt.libraryVersion));

            liteLoader.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                if (quilt.libraryVersion.get() != null) return QUILT.getPatchId();
                return null;
            }, fabric.libraryVersion, quilt.libraryVersion));

            optiFine.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                if (quilt.libraryVersion.get() != null) return QUILT.getPatchId();
                return null;
            }, fabric.libraryVersion, quilt.libraryVersion));

            for (InstallerItem fabric : new InstallerItem[]{fabric, fabricApi}) {
                fabric.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                    if (forge.libraryVersion.get() != null) return FORGE.getPatchId();
                    if (liteLoader.libraryVersion.get() != null) return LITELOADER.getPatchId();
                    if (optiFine.libraryVersion.get() != null) return OPTIFINE.getPatchId();
                    if (quilt.libraryVersion.get() != null) return QUILT.getPatchId();
                    if (quiltApi.libraryVersion.get() != null) return QUILT_API.getPatchId();
                    return null;
                }, forge.libraryVersion, liteLoader.libraryVersion, optiFine.libraryVersion, quilt.libraryVersion, quiltApi.libraryVersion));
            }

            fabricApi.dependencyName.bind(Bindings.createStringBinding(() -> {
                if (fabric.libraryVersion.get() == null) return FABRIC.getPatchId();
                else return null;
            }, fabric.libraryVersion));

            for (InstallerItem quilt : new InstallerItem[]{quilt, quiltApi}) {
                quilt.incompatibleLibraryName.bind(Bindings.createStringBinding(() -> {
                    if (fabric.libraryVersion.get() != null) return FABRIC.getPatchId();
                    if (fabricApi.libraryVersion.get() != null) return FABRIC_API.getPatchId();
                    if (forge.libraryVersion.get() != null) return FORGE.getPatchId();
                    if (liteLoader.libraryVersion.get() != null) return LITELOADER.getPatchId();
                    if (optiFine.libraryVersion.get() != null) return OPTIFINE.getPatchId();
                    return null;
                }, fabric.libraryVersion, fabricApi.libraryVersion, forge.libraryVersion, liteLoader.libraryVersion, optiFine.libraryVersion));
            }

            quiltApi.dependencyName.bind(Bindings.createStringBinding(() -> {
                if (quilt.libraryVersion.get() == null) return QUILT.getPatchId();
                else return null;
            }, quilt.libraryVersion));
        }

        public InstallerItem[] getLibraries() {
            return new InstallerItem[]{game, forge, liteLoader, optiFine, fabric, fabricApi, quilt, quiltApi};
        }
    }

    public static class InstallerItemSkin extends SkinBase<InstallerItem> {

        private static final PseudoClass LIST_ITEM = PseudoClass.getPseudoClass("list-item");
        private static final PseudoClass CARD = PseudoClass.getPseudoClass("card");

        InstallerItemSkin(InstallerItem control) {
            super(control);

            Pane pane;
            if (control.style == Style.CARD) {
                pane = new VBox();
            } else {
                pane = new HBox();
            }
            pane.getStyleClass().add("installer-item");
            RipplerContainer container = new RipplerContainer(pane);
            getChildren().setAll(container);

            pane.pseudoClassStateChanged(LIST_ITEM, control.style == Style.LIST_ITEM);
            pane.pseudoClassStateChanged(CARD, control.style == Style.CARD);

            if (control.imageUrl != null) {
                ImageView view = new ImageView(new Image(control.imageUrl));
                Node node = FXUtils.limitingSize(view, 32, 32);
                node.setMouseTransparent(true);
                node.getStyleClass().add("installer-item-image");
                pane.getChildren().add(node);

                if (control.style == Style.CARD) {
                    VBox.setMargin(node, new Insets(8, 0, 16, 0));
                }
            }

            Label nameLabel = new Label();
            nameLabel.getStyleClass().add("installer-item-name");
            nameLabel.setMouseTransparent(true);
            pane.getChildren().add(nameLabel);
            nameLabel.textProperty().set(I18n.hasKey("install.installer." + control.id) ? i18n("install.installer." + control.id) : control.id);
            HBox.setMargin(nameLabel, new Insets(0, 4, 0, 4));

            Label statusLabel = new Label();
            statusLabel.getStyleClass().add("installer-item-status");
            statusLabel.setMouseTransparent(true);
            pane.getChildren().add(statusLabel);
            HBox.setHgrow(statusLabel, Priority.ALWAYS);
            statusLabel.textProperty().bind(Bindings.createStringBinding(() -> {
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
            BorderPane.setMargin(statusLabel, new Insets(0, 0, 0, 8));
            BorderPane.setAlignment(statusLabel, Pos.CENTER_LEFT);

            HBox buttonsContainer = new HBox();
            buttonsContainer.setSpacing(8);
            buttonsContainer.setAlignment(Pos.CENTER);
            pane.getChildren().add(buttonsContainer);

            JFXButton closeButton = new JFXButton();
            closeButton.setGraphic(SVG.close(Theme.blackFillBinding(), -1, -1));
            closeButton.getStyleClass().add("toggle-icon4");
            closeButton.visibleProperty().bind(control.removable);
            closeButton.managedProperty().bind(closeButton.visibleProperty());
            closeButton.onMouseClickedProperty().bind(control.removeAction);
            buttonsContainer.getChildren().add(closeButton);

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
            buttonsContainer.getChildren().add(arrowButton);

            FXUtils.onChangeAndOperate(arrowButton.visibleProperty(), clickable -> {
                if (clickable) {
                    container.onMouseClickedProperty().bind(control.action);
                    pane.setCursor(Cursor.HAND);
                } else {
                    container.onMouseClickedProperty().unbind();
                    container.onMouseClickedProperty().set(null);
                    pane.setCursor(Cursor.DEFAULT);
                }
            });
        }
    }
}
