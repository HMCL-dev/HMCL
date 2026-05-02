/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class AbstractInstallersPage extends Control implements WizardPage {
    public static final String FABRIC_QUILT_API_TIP = "fabricQuiltApi";
    protected final WizardController controller;

    protected InstallerItem.InstallerItemGroup group;
    protected JFXTextField txtName = new JFXTextField();

    protected BooleanProperty installable = new SimpleBooleanProperty();

    public AbstractInstallersPage(WizardController controller, String gameVersion, DownloadProvider downloadProvider) {
        this.controller = controller;
        this.group = new InstallerItem.InstallerItemGroup(gameVersion, getInstallerItemStyle());

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            if (libraryId.equals(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())) continue;
            library.setOnInstall(() -> {
                if (!Boolean.TRUE.equals(config().getShownTips().get(FABRIC_QUILT_API_TIP))
                        && (LibraryAnalyzer.LibraryType.FABRIC_API.getPatchId().equals(libraryId)
                        || LibraryAnalyzer.LibraryType.QUILT_API.getPatchId().equals(libraryId)
                        || LibraryAnalyzer.LibraryType.LEGACY_FABRIC_API.getPatchId().equals(libraryId))) {
                    Controllers.dialog(new MessageDialogPane.Builder(
                            i18n("install.installer.fabric-quilt-api.warning", i18n("install.installer." + libraryId)),
                            i18n("message.warning"),
                            MessageDialogPane.MessageType.WARNING
                    ).ok(null).addCancel(i18n("button.do_not_show_again"), () -> config().getShownTips().put(FABRIC_QUILT_API_TIP, true)).build());
                }

                if (!(library.resolvedStateProperty().get() instanceof InstallerItem.IncompatibleState))
                    controller.onNext(
                            new VersionsPage(
                                    controller,
                                    i18n("install.installer.choose", i18n("install.installer." + libraryId)),
                                    gameVersion,
                                    downloadProvider,
                                    libraryId,
                                    () -> controller.onPrev(false, Navigation.NavigationDirection.PREVIOUS)
                            ), Navigation.NavigationDirection.NEXT
                    );
            });
            library.setOnRemove(() -> {
                controller.getSettings().remove(libraryId);
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
                versionNamePane.setStyle("-fx-padding: 20 8 20 16");
                versionNamePane.setAlignment(Pos.CENTER_LEFT);

                control.txtName.setMaxWidth(300);
                versionNamePane.getChildren().setAll(new Label(i18n("version.name")), control.txtName);
                root.setTop(versionNamePane);
            }

            {
                InstallerItem[] libraries = control.group.getLibraries();

                FlowPane libraryPane = new FlowPane(16, 16, libraries);
                ScrollPane scrollPane = new ScrollPane(libraryPane);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(true);
                BorderPane.setMargin(scrollPane, new Insets(16, 0, 16, 0));
                root.setCenter(scrollPane);

                if (libraries.length <= 8)
                    scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            }

            {
                JFXButton installButton = FXUtils.newRaisedButton(i18n("button.install"));
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
