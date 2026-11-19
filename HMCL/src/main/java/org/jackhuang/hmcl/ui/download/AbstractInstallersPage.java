// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputLayout;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.MessageDialogBuilder;
import org.jackhuang.hmcl.ui.construct.MessageType;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import static org.jackhuang.hmcl.setting.SettingsManager.state;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class AbstractInstallersPage extends Control implements WizardPage {
    public static final SettingsMap.Key<GameInstanceID> INSTANCE_ID = new SettingsMap.Key<>("instanceId");

    public static final String FABRIC_QUILT_API_TIP = "fabricQuiltApi";
    protected final WizardController controller;

    protected InstallerItem.InstallerItemGroup group;
    protected final M3TextField txtName = FXUtils.newTextField();
    /// Displays the name validation rules supplied by the installer page.
    protected final M3TextInputLayout nameLayout = FXUtils.newValidatedTextField(txtName);

    protected BooleanProperty installable = new SimpleBooleanProperty();

    public AbstractInstallersPage(WizardController controller, GameVersionNumber gameVersion, DownloadProvider downloadProvider) {
        this.controller = controller;
        this.group = new InstallerItem.InstallerItemGroup(gameVersion, getInstallerItemStyle());

        for (InstallerItem component : group.getComponents()) {
            GameComponentType type = component.getComponentType();
            if (type == GameComponentType.GAME) continue;
            component.setOnInstall(() -> {
                if (!Boolean.TRUE.equals(state().getShownTips().get(FABRIC_QUILT_API_TIP))
                        && (type == GameComponentType.FABRIC_API
                        || type == GameComponentType.QUILT_API
                        || type == GameComponentType.LEGACY_FABRIC_API)) {
                    Controllers.dialog(new MessageDialogBuilder(
                            i18n("install.installer.fabric-quilt-api.warning", i18n("install.installer." + type.getPatchId())),
                            i18n("message.warning"),
                            MessageType.WARNING
                    ).ok(null).addCancel(i18n("button.do_not_show_again"), () -> state().getShownTips().put(FABRIC_QUILT_API_TIP, true)).build());
                }

                if (!(component.resolvedStateProperty().get() instanceof InstallerItem.IncompatibleState))
                    controller.onNext(
                            new VersionsPage(
                                    controller,
                                    i18n("install.installer.choose", i18n("install.installer." + type.getPatchId())),
                                    gameVersion,
                                    downloadProvider,
                                    type,
                                    () -> controller.onPrev(false, Navigation.NavigationDirection.PREVIOUS)
                            ), Navigation.NavigationDirection.NEXT
                    );
            });
            component.setOnRemove(() -> {
                controller.getSettings().remove(type.getPatchId());
                reload();
            });
        }
    }

    protected InstallerItem.Style getInstallerItemStyle() {
        return InstallerItem.Style.CARD;
    }

    @Override
    public abstract String getTitle();

    protected abstract void reload();

    @Override
    public void onNavigate(SettingsMap settings) {
        reload();
    }

    @Override
    public abstract void cleanup(SettingsMap settings);

    protected abstract void onInstall();

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallersPageSkin(this);
    }

    /// Returns whether the name field includes clear and reset controls.
    ///
    /// @return `true` to display the controls; `false` otherwise
    protected abstract boolean showExtendPane();

    /// Restores the default value of the name field.
    protected abstract void resetDefaultName();

    protected static class InstallersPageSkin extends SkinBase<AbstractInstallersPage> {
        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected InstallersPageSkin(AbstractInstallersPage control) {
            super(control);

            BorderPane root = new BorderPane();
            root.setPadding(new Insets(16));

            {
                HBox versionNamePane = new HBox(8);
                versionNamePane.getStyleClass().add("card-non-transparent");
                versionNamePane.setStyle("-fx-padding: 20 16 20 16");
                versionNamePane.setAlignment(Pos.CENTER_LEFT);

                HBox.setHgrow(control.nameLayout, Priority.ALWAYS);

                versionNamePane.getChildren().addAll(new Label(i18n("instance.name")), control.nameLayout);

                if (control.showExtendPane()) {
                    M3IconButton clearButton = new M3IconButton(SVG.CLOSE.createIcon());
                    FXUtils.installFastTooltip(clearButton, i18n("button.clear"));
                    clearButton.disableProperty().bind(control.txtName.textProperty().isEmpty().or(control.txtName.disableProperty()));
                    clearButton.setOnAction(e -> control.txtName.clear());

                    M3IconButton resetButton = new M3IconButton(SVG.RESTORE.createIcon());
                    FXUtils.installFastTooltip(resetButton, i18n("button.reset"));
                    resetButton.disableProperty().bind(control.txtName.disableProperty());
                    resetButton.setOnAction(e -> control.resetDefaultName());

                    versionNamePane.getChildren().addAll(clearButton, resetButton);
                }

                root.setTop(versionNamePane);
            }

            {
                InstallerItem[] components = control.group.getComponents();

                FlowPane libraryPane = new FlowPane(16, 16, components);
                ScrollPane scrollPane = new ScrollPane(libraryPane);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                BorderPane.setMargin(scrollPane, new Insets(16, 0, 16, 0));
                root.setCenter(scrollPane);

                if (components.length <= 8)
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }

            {
                M3Button installButton = new M3Button(I18n.i18n("button.install"), M3ButtonVariant.FILLED);
                installButton.disableProperty().bind(control.installable.not());
                installButton.setPrefWidth(100);
                installButton.setPrefHeight(40);
                installButton.setOnAction(e -> control.onInstall());
                BorderPane.setAlignment(installButton, Pos.CENTER_RIGHT);
                root.setBottom(installButton);
            }

            getChildren().setAll(root);
        }
    }
}
