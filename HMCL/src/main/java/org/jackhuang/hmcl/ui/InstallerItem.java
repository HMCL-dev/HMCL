// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCard;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.ui.construct.ImageContainer;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends Control {
    private final GameComponentType type;
    private final GameInstanceIconType iconType;
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

    public InstallerItem(GameComponentType type, Style style) {
        this.type = type;
        this.style = style;
        this.iconType = GameInstanceIconType.getIconType(type);
    }

    public GameComponentType getComponentType() {
        return type;
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

    /// Returns horizontal bias for cards so wrapping is measured at the allocated width.
    /// List rows retain the default content bias.
    @Override
    public @Nullable Orientation getContentBias() {
        return style == Style.CARD ? Orientation.HORIZONTAL : super.getContentBias();
    }

    public final static class InstallerItemGroup {
        private final InstallerItem game;

        private final InstallerItem[] components;

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

        public InstallerItemGroup(GameVersionNumber gameVersion, Style style) {
            game = new InstallerItem(GameComponentType.GAME, style);
            InstallerItem fabric = new InstallerItem(GameComponentType.FABRIC, style);
            InstallerItem fabricApi = new InstallerItem(GameComponentType.FABRIC_API, style);
            InstallerItem forge = new InstallerItem(GameComponentType.FORGE, style);
            InstallerItem cleanroom = new InstallerItem(GameComponentType.CLEANROOM, style);
            InstallerItem legacyfabric = new InstallerItem(GameComponentType.LEGACY_FABRIC, style);
            InstallerItem legacyfabricApi = new InstallerItem(GameComponentType.LEGACY_FABRIC_API, style);
            InstallerItem neoForge = new InstallerItem(GameComponentType.NEO_FORGE, style);
            InstallerItem liteLoader = new InstallerItem(GameComponentType.LITELOADER, style);
            InstallerItem optiFine = new InstallerItem(GameComponentType.OPTIFINE, style);
            InstallerItem quilt = new InstallerItem(GameComponentType.QUILT, style);
            InstallerItem quiltApi = new InstallerItem(GameComponentType.QUILT_API, style);

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
                            return new IncompatibleState(other.type.getPatchId(), otherVersion.version);
                        }
                    }

                    return InstallableState.INSTANCE;
                }, bindings));
            }

            if (gameVersion != null) {
                game.versionProperty.set(new InstalledState(gameVersion.toString(), false, false));
            }

            InstallerItem[] all = {game, forge, neoForge, liteLoader, optiFine, fabric, fabricApi, quilt, quiltApi, legacyfabric, legacyfabricApi, cleanroom};

            for (InstallerItem item : all) {
                if (!item.resolvedStateProperty.isBound()) {
                    item.resolvedStateProperty.bind(Bindings.createObjectBinding(() -> {
                        InstalledState itemVersion = item.versionProperty.get();
                        return Objects.requireNonNullElse(itemVersion, InstallableState.INSTANCE);
                    }, item.versionProperty));
                }
            }

            if (gameVersion == null) {
                this.components = all;
            } else if (gameVersion.compareTo("1.12.2") == 0) {
                this.components = new InstallerItem[]{game, forge, cleanroom, liteLoader, legacyfabric, legacyfabricApi, optiFine};
            } else if (gameVersion.compareTo("1.13.2") <= 0) {
                this.components = new InstallerItem[]{game, forge, liteLoader, optiFine, legacyfabric, legacyfabricApi};
            } else {
                this.components = new InstallerItem[]{game, forge, neoForge, optiFine, fabric, fabricApi, quilt, quiltApi};
            }
        }

        public InstallerItem getGame() {
            return game;
        }

        public InstallerItem[] getComponents() {
            return components;
        }
    }

    /// Presents an installer as an interactive card or a compact list row.
    @NotNullByDefault
    private static final class InstallerItemSkin extends SkinBase<InstallerItem> {
        /// Selects the compact row's layout rules.
        private static final PseudoClass LIST_ITEM = PseudoClass.getPseudoClass("list-item");

        /// Dims the description of an incompatible component.
        private static final PseudoClass INCOMPATIBLE = PseudoClass.getPseudoClass("incompatible");

        /// The card or compact row whose size determines the control's preferred dimensions.
        private final RipplerContainer container;

        /// Creates state-bound actions and the layout selected by the control's style.
        InstallerItemSkin(InstallerItem control) {
            super(control);

            JFXButton removeButton = FXUtils.newToggleButton4(SVG.CLOSE);
            if (control.type == GameComponentType.GAME) {
                removeButton.setVisible(false);
            } else {
                removeButton.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
                    @Nullable State state = control.resolvedStateProperty.get();
                    return state instanceof InstalledState installedState && !installedState.external;
                }, control.resolvedStateProperty));
            }
            removeButton.managedProperty().bind(removeButton.visibleProperty());
            removeButton.setOnAction(e -> {
                @Nullable Runnable onRemove = control.getOnRemove();
                if (onRemove != null)
                    onRemove.run();
            });

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

                @Nullable State state = control.resolvedStateProperty.get();
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
                @Nullable Runnable onInstall = control.getOnInstall();
                if (onInstall != null)
                    onInstall.run();
            });

            @Nullable ImageContainer image = null;
            if (control.iconType != null) {
                image = new ImageContainer(32);
                image.setImage(control.iconType.getIcon());
                image.setMouseTransparent(true);
                image.getStyleClass().add("installer-item-image");
            }
            String name = I18n.hasKey("install.installer." + control.type.getPatchId())
                    ? i18n("install.installer." + control.type.getPatchId()) : control.type.getPatchId();
            StringBinding status = createStatusText(control);
            container = control.style == Style.CARD
                    ? createCard(control, image, name, status, removeButton, installButton)
                    : createListItem(control, image, name, status, removeButton, installButton);
            getChildren().setAll(container);
        }

        /// Measures card wrapping at the available width, using its preferred width when unspecified.
        @Override
        protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            if (container instanceof JFXCard) {
                double contentWidth = width < 0 ? container.prefWidth(-1) : Math.max(0, width - leftInset - rightInset);
                return topInset + container.prefHeight(contentWidth) + bottomInset;
            }
            return super.computePrefHeight(width, topInset, rightInset, bottomInset, leftInset);
        }

        /// Keeps the card's minimum height consistent with its width-dependent preferred height.
        @Override
        protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            return container instanceof JFXCard
                    ? computePrefHeight(width, topInset, rightInset, bottomInset, leftInset)
                    : super.computeMinHeight(width, topInset, rightInset, bottomInset, leftInset);
        }

        /// Formats the resolved installation state for the selected presentation.
        private static StringBinding createStatusText(InstallerItem control) {
            return Bindings.createStringBinding(() -> {
                State state = Objects.requireNonNull(control.resolvedStateProperty.get());
                if (state instanceof InstalledState installedState) {
                    if (installedState.incompatibleWithGame)
                        return i18n("install.installer.change_version", installedState.version);
                    if (installedState.external)
                        return i18n("install.installer.external_version", installedState.version);
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
            }, control.resolvedStateProperty);
        }

        /// Creates an outlined card whose background action follows installation availability.
        private static JFXCard createCard(InstallerItem control, @Nullable ImageContainer image, String name,
                                          StringBinding status, JFXButton removeButton, JFXButton installButton) {
            JFXCard card = new JFXCard();
            card.getStyleClass().add("installer-card");
            card.setCardType(JFXCard.CardType.OUTLINED);
            card.setMinHeight(Region.USE_PREF_SIZE);
            card.setHeaderGraphic(image);
            card.setHeadline(name);
            card.supportingTextProperty().bind(status);
            card.getActions().addAll(removeButton, installButton);
            card.interactiveProperty().bind(installButton.visibleProperty());
            card.accessibleTextProperty().bind(Bindings.concat(name, ", ", status));
            card.setOnAction(event -> installButton.fire());
            FXUtils.onChangeAndOperate(control.resolvedStateProperty,
                    state -> card.pseudoClassStateChanged(INCOMPATIBLE, state instanceof IncompatibleState));
            return card;
        }

        /// Creates the compact row with its existing horizontal layout and mouse activation.
        private static RipplerContainer createListItem(InstallerItem control, @Nullable ImageContainer image, String name,
                                                       StringBinding status, JFXButton removeButton, JFXButton installButton) {
            HBox pane = new HBox();
            pane.getStyleClass().add("installer-item");
            pane.pseudoClassStateChanged(LIST_ITEM, true);
            RipplerContainer container = new RipplerContainer(pane);
            container.getStyleClass().add("installer-item-wrapper");
            if (image != null)
                pane.getChildren().add(image);

            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("installer-item-name");
            nameLabel.setMouseTransparent(true);
            HBox.setMargin(nameLabel, new Insets(0, 4, 0, 4));

            Label statusLabel = new Label();
            statusLabel.getStyleClass().add("installer-item-status");
            statusLabel.setMouseTransparent(true);
            statusLabel.textProperty().bind(status);
            HBox.setHgrow(statusLabel, Priority.ALWAYS);
            FXUtils.onChangeAndOperate(control.resolvedStateProperty,
                    state -> statusLabel.pseudoClassStateChanged(INCOMPATIBLE, state instanceof IncompatibleState));

            HBox buttonsContainer = new HBox(8, removeButton, installButton);
            buttonsContainer.setPickOnBounds(false);
            buttonsContainer.setAlignment(Pos.CENTER);
            pane.getChildren().addAll(nameLabel, statusLabel, buttonsContainer);

            FXUtils.onChangeAndOperate(installButton.visibleProperty(), clickable -> {
                if (clickable) {
                    container.setOnMouseClicked(event -> {
                        @Nullable Runnable onInstall = control.getOnInstall();
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
            return container;
        }
    }
}
