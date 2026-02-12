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
import com.jfoenix.controls.JFXRippler;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.setting.VersionIconType;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends Control {
    private final String id;
    private final VersionIconType iconType;
    private final Style style;
    private final ObjectProperty<InstalledState> versionProperty = new SimpleObjectProperty<>(this, "version", null);
    private final ObjectProperty<State> resolvedStateProperty = new SimpleObjectProperty<>(this, "resolvedState", InstallableState.INSTANCE);

    private final ObjectProperty<Runnable> onInstall = new SimpleObjectProperty<>(this, "onInstall");
    private final ObjectProperty<Runnable> onRemove = new SimpleObjectProperty<>(this, "onRemove");

    public sealed interface State {
    }

    public static final class InstallableState implements State {
        public static final InstallableState INSTANCE = new InstallableState();

        private InstallableState() {
        }
    }

    public record IncompatibleState(String incompatibleItemName, String incompatibleItemVersion) implements State {
    }

    public record InstalledState(String version, boolean external, boolean incompatibleWithGame) implements State {
    }

    public enum Style {
        LIST_ITEM,
        CARD,
    }

    public InstallerItem(LibraryAnalyzer.LibraryType id, Style style) {
        this(id.getPatchId(), style);
    }

    public InstallerItem(String id, Style style) {
        this.id = id;
        this.style = style;

        iconType = switch (id) {
            case "game" -> VersionIconType.GRASS;
            case "fabric", "fabric-api" -> VersionIconType.FABRIC;
            case "legacyfabric", "legacyfabric-api" -> VersionIconType.LEGACY_FABRIC;
            case "forge" -> VersionIconType.FORGE;
            case "cleanroom" -> VersionIconType.CLEANROOM;
            case "liteloader" -> VersionIconType.CHICKEN;
            case "optifine" -> VersionIconType.OPTIFINE;
            case "quilt", "quilt-api" -> VersionIconType.QUILT;
            case "neoforge" -> VersionIconType.NEO_FORGE;
            default -> null;
        };
    }

    public String getLibraryId() {
        return id;
    }

    public ObjectProperty<InstalledState> versionProperty() {
        return versionProperty;
    }

    public ObjectProperty<State> resolvedStateProperty() {
        return resolvedStateProperty;
    }

    public ObjectProperty<Runnable> onInstallProperty() {
        return onInstall;
    }

    public Runnable getOnInstall() {
        return onInstall.get();
    }

    public void setOnInstall(Runnable onInstall) {
        this.onInstall.set(onInstall);
    }

    public ObjectProperty<Runnable> onRemoveProperty() {
        return onRemove;
    }

    public Runnable getOnRemove() {
        return onRemove.get();
    }

    public void setOnRemove(Runnable onRemove) {
        this.onRemove.set(onRemove);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallerItemSkin(this);
    }

    public final static class InstallerItemGroup {
        private final InstallerItem game;

        private final InstallerItem[] libraries;

        private Set<InstallerItem> getIncompatibles(Map<InstallerItem, Set<InstallerItem>> incompatibleMap, InstallerItem item) {
            return incompatibleMap.computeIfAbsent(item, it -> new HashSet<>());
        }

        private void addIncompatibles(Map<InstallerItem, Set<InstallerItem>> incompatibleMap, InstallerItem item, InstallerItem... others) {
            Set<InstallerItem> set = getIncompatibles(incompatibleMap, item);
            for (InstallerItem other : others) {
                set.add(other);
                getIncompatibles(incompatibleMap, other).add(item);
            }
        }

        private void mutualIncompatible(Map<InstallerItem, Set<InstallerItem>> incompatibleMap, InstallerItem... items) {
            for (InstallerItem item : items) {
                Set<InstallerItem> set = getIncompatibles(incompatibleMap, item);

                for (InstallerItem item2 : items) {
                    if (item2 != item) {
                        set.add(item2);
                    }
                }
            }
        }

        public InstallerItemGroup(String gameVersion, Style style) {
            game = new InstallerItem(MINECRAFT, style);
            InstallerItem fabric = new InstallerItem(FABRIC, style);
            InstallerItem fabricApi = new InstallerItem(FABRIC_API, style);
            InstallerItem forge = new InstallerItem(FORGE, style);
            InstallerItem cleanroom = new InstallerItem(CLEANROOM, style);
            InstallerItem legacyfabric = new InstallerItem(LEGACY_FABRIC, style);
            InstallerItem legacyfabricApi = new InstallerItem(LEGACY_FABRIC_API, style);
            InstallerItem neoForge = new InstallerItem(NEO_FORGE, style);
            InstallerItem liteLoader = new InstallerItem(LITELOADER, style);
            InstallerItem optiFine = new InstallerItem(OPTIFINE, style);
            InstallerItem quilt = new InstallerItem(QUILT, style);
            InstallerItem quiltApi = new InstallerItem(QUILT_API, style);

            Map<InstallerItem, Set<InstallerItem>> incompatibleMap = new HashMap<>();
            mutualIncompatible(incompatibleMap, forge, fabric, quilt, neoForge, cleanroom, legacyfabric);
            addIncompatibles(incompatibleMap, liteLoader, fabric, quilt, neoForge, cleanroom, legacyfabric);
            addIncompatibles(incompatibleMap, optiFine, fabric, quilt, neoForge, cleanroom, liteLoader, legacyfabric);
            addIncompatibles(incompatibleMap, fabricApi, forge, quiltApi, neoForge, liteLoader, optiFine, cleanroom, legacyfabric, legacyfabricApi);
            addIncompatibles(incompatibleMap, quiltApi, forge, fabric, fabricApi, neoForge, liteLoader, optiFine, cleanroom, legacyfabric, legacyfabricApi);
            addIncompatibles(incompatibleMap, legacyfabricApi, forge, fabric, fabricApi, neoForge, liteLoader, optiFine, cleanroom, quilt, quiltApi);

            for (Map.Entry<InstallerItem, Set<InstallerItem>> entry : incompatibleMap.entrySet()) {
                InstallerItem item = entry.getKey();
                Set<InstallerItem> incompatibleItems = entry.getValue();

                Observable[] bindings = new Observable[incompatibleItems.size() + 1];
                bindings[0] = item.versionProperty;
                int i = 1;
                for (InstallerItem other : incompatibleItems) {
                    bindings[i++] = other.versionProperty;
                }

                item.resolvedStateProperty.bind(Bindings.createObjectBinding(() -> {
                    InstalledState itemVersion = item.versionProperty.get();
                    if (itemVersion != null) {
                        return itemVersion;
                    }

                    for (InstallerItem other : incompatibleItems) {
                        InstalledState otherVersion = other.versionProperty.get();
                        if (otherVersion != null) {
                            return new IncompatibleState(other.id, otherVersion.version);
                        }
                    }

                    return InstallableState.INSTANCE;
                }, bindings));
            }

            if (gameVersion != null) {
                game.versionProperty.set(new InstalledState(gameVersion, false, false));
            }

            InstallerItem[] all = {game, forge, neoForge, liteLoader, optiFine, fabric, fabricApi, quilt, quiltApi, legacyfabric, legacyfabricApi, cleanroom};

            for (InstallerItem item : all) {
                if (!item.resolvedStateProperty.isBound()) {
                    item.resolvedStateProperty.bind(Bindings.createObjectBinding(() -> {
                        InstalledState itemVersion = item.versionProperty.get();
                        if (itemVersion != null) {
                            return itemVersion;
                        }
                        return InstallableState.INSTANCE;
                    }, item.versionProperty));
                }
            }

            if (gameVersion == null) {
                this.libraries = all;
            } else if (gameVersion.equals("1.12.2")) {
                this.libraries = new InstallerItem[]{game, forge, cleanroom, liteLoader, legacyfabric, legacyfabricApi, optiFine};
            } else if (GameVersionNumber.compare(gameVersion, "1.13.2") <= 0) {
                this.libraries = new InstallerItem[]{game, forge, liteLoader, optiFine, legacyfabric, legacyfabricApi};
            } else {
                this.libraries = new InstallerItem[]{game, forge, neoForge, optiFine, fabric, fabricApi, quilt, quiltApi};
            }
        }

        public InstallerItem getGame() {
            return game;
        }

        public InstallerItem[] getLibraries() {
            return libraries;
        }
    }

    private static final class InstallerItemSkin extends SkinBase<InstallerItem> {
        private static final PseudoClass LIST_ITEM = PseudoClass.getPseudoClass("list-item");
        private static final PseudoClass CARD = PseudoClass.getPseudoClass("card");

        @SuppressWarnings({"FieldCanBeLocal", "unused"})
        private final ChangeListener<Number> holder;

        InstallerItemSkin(InstallerItem control) {
            super(control);

            Pane pane;
            if (control.style == Style.CARD) {
                pane = new VBox();
                holder = FXUtils.onWeakChangeAndOperate(pane.widthProperty(), v -> FXUtils.setLimitHeight(pane, v.doubleValue() * 0.7));
            } else {
                pane = new HBox();
                holder = null;
            }
            pane.getStyleClass().add("installer-item");
            RipplerContainer container = new RipplerContainer(pane);
            container.setPosition(JFXRippler.RipplerPos.BACK);
            StackPane paneWrapper = new StackPane();
            paneWrapper.getStyleClass().add("installer-item-wrapper");
            paneWrapper.getChildren().setAll(container);
            getChildren().setAll(paneWrapper);

            pane.pseudoClassStateChanged(LIST_ITEM, control.style == Style.LIST_ITEM);
            pane.pseudoClassStateChanged(CARD, control.style == Style.CARD);
            paneWrapper.pseudoClassStateChanged(CARD, control.style == Style.CARD);

            if (control.iconType != null) {
                ImageView view = new ImageView(control.iconType.getIcon());
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
                State state = control.resolvedStateProperty.get();

                if (state instanceof InstalledState installedState) {
                    if (installedState.incompatibleWithGame) {
                        return i18n("install.installer.change_version", installedState.version);
                    }
                    if (installedState.external) {
                        return i18n("install.installer.external_version", installedState.version);
                    }
                    return i18n("install.installer.version", installedState.version);
                } else if (state instanceof InstallableState) {
                    return control.style == Style.CARD
                            ? i18n("install.installer.do_not_install")
                            : i18n("install.installer.not_installed");
                } else if (state instanceof IncompatibleState incompatibleState) {
                    return i18n("install.installer.incompatible", i18n("install.installer." + incompatibleState.incompatibleItemName));
                } else {
                    throw new AssertionError("Unknown state type: " + state.getClass());
                }
            }, control.resolvedStateProperty));
            BorderPane.setMargin(statusLabel, new Insets(0, 0, 0, 8));
            BorderPane.setAlignment(statusLabel, Pos.CENTER_LEFT);

            HBox buttonsContainer = new HBox();
            buttonsContainer.setPickOnBounds(false);
            buttonsContainer.setSpacing(8);
            buttonsContainer.setAlignment(Pos.CENTER);
            pane.getChildren().add(buttonsContainer);

            JFXButton removeButton = new JFXButton();
            removeButton.setGraphic(SVG.CLOSE.createIcon());
            removeButton.getStyleClass().add("toggle-icon4");
            if (control.id.equals(MINECRAFT.getPatchId())) {
                removeButton.setVisible(false);
            } else {
                removeButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
                    State state = control.resolvedStateProperty.get();
                    return state instanceof InstalledState installedState && !installedState.external;
                }, control.resolvedStateProperty));
            }
            removeButton.managedProperty().bind(removeButton.visibleProperty());
            removeButton.setOnAction(e -> {
                Runnable onRemove = control.getOnRemove();
                if (onRemove != null)
                    onRemove.run();
            });
            buttonsContainer.getChildren().add(removeButton);

            JFXButton installButton = new JFXButton();
            installButton.graphicProperty().bind(Bindings.createObjectBinding(() ->
                            control.resolvedStateProperty.get() instanceof InstallableState ?
                                    SVG.ARROW_FORWARD.createIcon() :
                                    SVG.UPDATE.createIcon(),
                    control.resolvedStateProperty
            ));
            installButton.getStyleClass().add("toggle-icon4");
            installButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
                if (control.getOnInstall() == null) {
                    return false;
                }

                State state = control.resolvedStateProperty.get();
                if (state instanceof InstallableState) {
                    return true;
                }
                if (state instanceof InstalledState) {
                    return !((InstalledState) state).external;
                }

                return false;
            }, control.resolvedStateProperty, control.onInstall));
            installButton.managedProperty().bind(installButton.visibleProperty());
            installButton.setOnAction(e -> {
                Runnable onInstall = control.getOnInstall();
                if (onInstall != null)
                    onInstall.run();
            });
            buttonsContainer.getChildren().add(installButton);

            FXUtils.onChangeAndOperate(installButton.visibleProperty(), clickable -> {
                if (clickable) {
                    container.setOnMouseClicked(event -> {
                        Runnable onInstall = control.getOnInstall();
                        if (onInstall != null && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                            onInstall.run();
                            event.consume();
                        }
                    });
                    pane.setCursor(Cursor.HAND);
                } else {
                    container.setOnMouseClicked(null);
                    pane.setCursor(Cursor.DEFAULT);
                }
            });
        }
    }
}
