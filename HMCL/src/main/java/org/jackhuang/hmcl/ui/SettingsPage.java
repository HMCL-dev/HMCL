/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.When;
import javafx.beans.property.*;
import javafx.scene.control.ToggleGroup;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.upgrade.RemoteVersion;
import org.jackhuang.hmcl.upgrade.UpdateChannel;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;

import java.net.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.reservedSelectedPropertyFor;
import static org.jackhuang.hmcl.util.javafx.ExtendedProperties.selectedItemPropertyFor;

public final class SettingsPage extends SettingsView implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", i18n("settings.launcher"));

    private InvalidationListener updateListener;

    public SettingsPage() {
        FXUtils.smoothScrolling(scroll);

        // ==== Download sources ====
        cboDownloadSource.getItems().setAll(DownloadProviders.providersById.keySet());
        selectedItemPropertyFor(cboDownloadSource).bindBidirectional(config().downloadTypeProperty());
        // ====

        // ==== Font ====
        cboFont.valueProperty().bindBidirectional(config().fontFamilyProperty());

        txtFontSize.textProperty().bindBidirectional(config().fontSizeProperty(),
                SafeStringConverter.fromFiniteDouble()
                        .restrict(it -> it > 0)
                        .fallbackTo(12.0)
                        .asPredicate(Validator.addTo(txtFontSize)));

        lblDisplay.fontProperty().bind(Bindings.createObjectBinding(
                () -> Font.font(config().getFontFamily(), config().getFontSize()),
                config().fontFamilyProperty(), config().fontSizeProperty()));
        // ====

        // ==== Languages ====
        cboLanguage.getItems().setAll(Locales.LOCALES);
        selectedItemPropertyFor(cboLanguage).bindBidirectional(config().localizationProperty());
        // ====

        // ==== Proxy ====
        txtProxyHost.textProperty().bindBidirectional(config().proxyHostProperty());
        txtProxyPort.textProperty().bindBidirectional(config().proxyPortProperty());
        txtProxyUsername.textProperty().bindBidirectional(config().proxyUserProperty());
        txtProxyPassword.textProperty().bindBidirectional(config().proxyPassProperty());

        proxyPane.disableProperty().bind(chkDisableProxy.selectedProperty());
        authPane.disableProperty().bind(chkProxyAuthentication.selectedProperty().not());

        reservedSelectedPropertyFor(chkDisableProxy).bindBidirectional(config().hasProxyProperty());
        chkProxyAuthentication.selectedProperty().bindBidirectional(config().hasProxyAuthProperty());

        ToggleGroup proxyConfigurationGroup = new ToggleGroup();
        chkProxyHttp.setUserData(Proxy.Type.HTTP);
        chkProxyHttp.setToggleGroup(proxyConfigurationGroup);
        chkProxySocks.setUserData(Proxy.Type.SOCKS);
        chkProxySocks.setToggleGroup(proxyConfigurationGroup);
        selectedItemPropertyFor(proxyConfigurationGroup, Proxy.Type.class).bindBidirectional(config().proxyTypeProperty());
        // ====

        fileCommonLocation.loadChildren(Arrays.asList(
                fileCommonLocation.createChildren(i18n("launcher.common_directory.default"), EnumCommonDirectory.DEFAULT)
        ), EnumCommonDirectory.CUSTOM);
        fileCommonLocation.selectedDataProperty().bindBidirectional(config().commonDirTypeProperty());
        fileCommonLocation.customTextProperty().bindBidirectional(config().commonDirectoryProperty());
        fileCommonLocation.subtitleProperty().bind(
                Bindings.createObjectBinding(() -> Optional.ofNullable(Settings.instance().getCommonDirectory())
                                .orElse(i18n("launcher.common_directory.disabled")),
                        config().commonDirectoryProperty(), config().commonDirTypeProperty()));

        // ==== Update ====
        FXUtils.installTooltip(btnUpdate, i18n("update.tooltip"));
        updateListener = any -> {
            btnUpdate.setVisible(UpdateChecker.isOutdated());

            if (UpdateChecker.isOutdated()) {
                lblUpdateSub.setText(i18n("update.newest_version", UpdateChecker.getLatestVersion().getVersion()));
                lblUpdateSub.getStyleClass().setAll("update-label");

                lblUpdate.setText(i18n("update.found"));
                lblUpdate.getStyleClass().setAll("update-label");
            } else if (UpdateChecker.isCheckingUpdate()) {
                lblUpdateSub.setText(i18n("update.checking"));
                lblUpdateSub.getStyleClass().setAll("subtitle-label");

                lblUpdate.setText(i18n("update"));
                lblUpdate.getStyleClass().setAll();
            } else {
                lblUpdateSub.setText(i18n("update.latest"));
                lblUpdateSub.getStyleClass().setAll("subtitle-label");

                lblUpdate.setText(i18n("update"));
                lblUpdate.getStyleClass().setAll();
            }
        };
        UpdateChecker.latestVersionProperty().addListener(new WeakInvalidationListener(updateListener));
        UpdateChecker.outdatedProperty().addListener(new WeakInvalidationListener(updateListener));
        UpdateChecker.checkingUpdateProperty().addListener(new WeakInvalidationListener(updateListener));
        updateListener.invalidated(null);

        lblUpdateNote.setWrappingWidth(470);

        ObjectProperty<UpdateChannel> updateChannel = new SimpleObjectProperty<UpdateChannel>() {
            @Override
            protected void invalidated() {
                UpdateChannel updateChannel = Objects.requireNonNull(get());
                chkUpdateDev.setSelected(updateChannel == UpdateChannel.DEVELOPMENT);
                chkUpdateStable.setSelected(updateChannel == UpdateChannel.STABLE);
            }
        };

        ToggleGroup updateChannelGroup = new ToggleGroup();
        chkUpdateDev.setToggleGroup(updateChannelGroup);
        chkUpdateDev.setUserData(UpdateChannel.DEVELOPMENT);
        chkUpdateStable.setToggleGroup(updateChannelGroup);
        chkUpdateStable.setUserData(UpdateChannel.STABLE);
        updateChannelGroup.getToggles().forEach(
                toggle -> toggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        updateChannel.set((UpdateChannel) toggle.getUserData());
                    }
                }));
        updateChannel.bindBidirectional(ConfigHolder.config().updateChannelProperty());
        // ====

        // ==== Background ====
        backgroundItem.loadChildren(Collections.singletonList(
                backgroundItem.createChildren(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
        ), EnumBackgroundImage.CUSTOM);
        backgroundItem.customTextProperty().bindBidirectional(config().backgroundImageProperty());
        backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
        backgroundItem.subtitleProperty().bind(
                new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                        .then(i18n("launcher.background.default"))
                        .otherwise(config().backgroundImageProperty()));
        // ====

        // ==== Theme ====
        JFXColorPicker picker = new JFXColorPicker(Color.web(config().getTheme().getColor()), null);
        picker.setCustomColorText(i18n("color.custom"));
        picker.setRecentColorsText(i18n("color.recent"));
        picker.getCustomColors().setAll(Theme.SUGGESTED_COLORS);
        picker.setOnAction(e -> {
            Theme theme = Theme.custom(Theme.getColorDisplayName(picker.getValue()));
            config().setTheme(theme);
            Controllers.getScene().getStylesheets().setAll(theme.getStylesheets());
        });
        themeColorPickerContainer.getChildren().setAll(picker);
        Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));
        // ====
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    @Override
    protected void onUpdate() {
        RemoteVersion target = UpdateChecker.getLatestVersion();
        if (target == null) {
            return;
        }
        UpdateHandler.updateFrom(target);
    }

    @Override
    protected void onHelp() {
        FXUtils.openLink(Metadata.HELP_URL);
    }
}
