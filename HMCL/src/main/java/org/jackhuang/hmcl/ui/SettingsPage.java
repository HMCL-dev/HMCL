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

import com.jfoenix.controls.*;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.construct.FileItem;
import org.jackhuang.hmcl.ui.construct.FontComboBox;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.i18n.Locales;

import static org.jackhuang.hmcl.ui.FXUtils.onInvalidating;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.net.Proxy;
import java.util.Collections;

public final class SettingsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", i18n("settings.launcher"));

    @FXML
    private JFXTextField txtProxyHost;
    @FXML
    private JFXTextField txtProxyPort;
    @FXML
    private JFXTextField txtProxyUsername;
    @FXML
    private JFXPasswordField txtProxyPassword;
    @FXML
    private JFXTextField txtFontSize;
    @FXML
    private JFXComboBox<Label> cboLanguage;
    @FXML
    private JFXComboBox<?> cboDownloadSource;
    @FXML
    private FontComboBox cboFont;
    @FXML
    private FileItem fileCommonLocation;
    @FXML
    private Label lblDisplay;
    @FXML
    private Label lblUpdate;
    @FXML
    private Label lblUpdateSub;
    @FXML
    private JFXButton btnUpdate;
    @FXML
    private ScrollPane scroll;
    @FXML
    private MultiFileItem backgroundItem;
    @FXML
    private StackPane themeColorPickerContainer;
    @FXML
    private JFXRadioButton chkNoProxy;
    @FXML
    private JFXRadioButton chkManualProxy;
    @FXML
    private JFXRadioButton chkProxyHttp;
    @FXML
    private JFXRadioButton chkProxySocks;
    @FXML
    private JFXCheckBox chkProxyAuthentication;
    @FXML
    private GridPane authPane;
    @FXML
    private Pane proxyPane;

    {
        FXUtils.loadFXML(this, "/assets/fxml/setting.fxml");

        FXUtils.smoothScrolling(scroll);

        txtProxyHost.setText(ConfigHolder.CONFIG.proxyHost.get());
        txtProxyHost.textProperty().addListener((a, b, newValue) -> ConfigHolder.CONFIG.proxyHost.set(newValue));

        txtProxyPort.setText(ConfigHolder.CONFIG.proxyPort.get());
        txtProxyPort.textProperty().addListener((a, b, newValue) -> ConfigHolder.CONFIG.proxyPort.set(newValue));

        txtProxyUsername.setText(ConfigHolder.CONFIG.proxyUser.get());
        txtProxyUsername.textProperty().addListener((a, b, newValue) -> ConfigHolder.CONFIG.proxyUser.set(newValue));

        txtProxyPassword.setText(ConfigHolder.CONFIG.proxyPass.get());
        txtProxyPassword.textProperty().addListener((a, b, newValue) -> ConfigHolder.CONFIG.proxyPass.set(newValue));

        cboDownloadSource.getSelectionModel().select(DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(Settings.INSTANCE.getDownloadProvider()));
        cboDownloadSource.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setDownloadProvider(DownloadProviders.getDownloadProvider(newValue.intValue())));

        cboFont.getSelectionModel().select(Settings.INSTANCE.getFont().getFamily());
        cboFont.valueProperty().addListener((a, b, newValue) -> {
            Font font = Font.font(newValue, Settings.INSTANCE.getFont().getSize());
            Settings.INSTANCE.setFont(font);
            lblDisplay.setStyle("-fx-font: " + font.getSize() + " \"" + font.getFamily() + "\";");
        });

        txtFontSize.setText(Double.toString(Settings.INSTANCE.getFont().getSize()));
        txtFontSize.getValidators().add(new Validator(it -> Lang.toDoubleOrNull(it) != null));
        txtFontSize.textProperty().addListener((a, b, newValue) -> {
            if (txtFontSize.validate()) {
                Font font = Font.font(Settings.INSTANCE.getFont().getFamily(), Double.parseDouble(newValue));
                Settings.INSTANCE.setFont(font);
                lblDisplay.setStyle("-fx-font: " + font.getSize() + " \"" + font.getFamily() + "\";");
            }
        });

        lblDisplay.setStyle("-fx-font: " + Settings.INSTANCE.getFont().getSize() + " \"" + Settings.INSTANCE.getFont().getFamily() + "\";");

        ObservableList<Label> list = FXCollections.observableArrayList();
        for (Locales.SupportedLocale locale : Locales.LOCALES)
            list.add(new Label(locale.getName(Settings.INSTANCE.getLocale().getResourceBundle())));

        cboLanguage.setItems(list);
        cboLanguage.getSelectionModel().select(Locales.LOCALES.indexOf(Settings.INSTANCE.getLocale()));
        cboLanguage.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setLocale(Locales.getLocale(newValue.intValue())));


        ToggleGroup proxyConfigurationGroup = new ToggleGroup();
        chkProxyHttp.setUserData(Proxy.Type.HTTP);
        chkProxyHttp.setToggleGroup(proxyConfigurationGroup);
        chkProxySocks.setUserData(Proxy.Type.SOCKS);
        chkProxySocks.setToggleGroup(proxyConfigurationGroup);

        for (Toggle toggle : proxyConfigurationGroup.getToggles())
            if (toggle.getUserData() == Settings.INSTANCE.getProxyType())
                toggle.setSelected(true);

        ToggleGroup hasProxyGroup = new ToggleGroup();
        chkNoProxy.setToggleGroup(hasProxyGroup);
        chkManualProxy.setToggleGroup(hasProxyGroup);
        if (!ConfigHolder.CONFIG.hasProxy.get())
            chkNoProxy.setSelected(true);
        else
            chkManualProxy.setSelected(true);
        proxyPane.disableProperty().bind(chkNoProxy.selectedProperty());

        hasProxyGroup.selectedToggleProperty().addListener((a, b, newValue) ->
                ConfigHolder.CONFIG.hasProxy.set(newValue != chkNoProxy));

        proxyConfigurationGroup.selectedToggleProperty().addListener((a, b, newValue) ->
                Settings.INSTANCE.setProxyType((Proxy.Type) newValue.getUserData()));

        chkProxyAuthentication.setSelected(ConfigHolder.CONFIG.hasProxyAuth.get());
        chkProxyAuthentication.selectedProperty().addListener((a, b, newValue) -> ConfigHolder.CONFIG.hasProxyAuth.set(newValue));
        authPane.disableProperty().bind(chkProxyAuthentication.selectedProperty().not());

        fileCommonLocation.pathProperty().bindBidirectional(ConfigHolder.CONFIG.commonDirectory);

        FXUtils.installTooltip(btnUpdate, i18n("update.tooltip"));
        checkUpdate();

        // background
        backgroundItem.loadChildren(Collections.singletonList(
                backgroundItem.createChildren(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
        ));

        FXUtils.bindString(backgroundItem.getTxtCustom(), ConfigHolder.CONFIG.backgroundImage);

        backgroundItem.setCustomUserData(EnumBackgroundImage.CUSTOM);
        backgroundItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == ConfigHolder.CONFIG.backgroundImageType.get()).findFirst().ifPresent(it -> it.setSelected(true));

        ConfigHolder.CONFIG.backgroundImage.addListener(onInvalidating(this::initBackgroundItemSubtitle));
        ConfigHolder.CONFIG.backgroundImageType.addListener(onInvalidating(this::initBackgroundItemSubtitle));
        initBackgroundItemSubtitle();

        backgroundItem.setToggleSelectedListener(newValue ->
                ConfigHolder.CONFIG.backgroundImageType.set((EnumBackgroundImage) newValue.getUserData()));

        // theme
        JFXColorPicker picker = new JFXColorPicker(Color.web(Settings.INSTANCE.getTheme().getColor()), null);
        picker.setCustomColorText(i18n("color.custom"));
        picker.setRecentColorsText(i18n("color.recent"));
        picker.getCustomColors().setAll(Theme.SUGGESTED_COLORS);
        picker.setOnAction(e -> {
            Theme theme = Theme.custom(Theme.getColorDisplayName(picker.getValue()));
            Settings.INSTANCE.setTheme(theme);
            Controllers.getScene().getStylesheets().setAll(theme.getStylesheets());
        });
        themeColorPickerContainer.getChildren().setAll(picker);
        Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));
    }

    private void initBackgroundItemSubtitle() {
        switch (ConfigHolder.CONFIG.backgroundImageType.get()) {
            case DEFAULT:
                backgroundItem.setSubtitle(i18n("launcher.background.default"));
                break;
            case CUSTOM:
                backgroundItem.setSubtitle(ConfigHolder.CONFIG.backgroundImage.get());
                break;
        }
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public void checkUpdate() {
        btnUpdate.setVisible(Launcher.UPDATE_CHECKER.isOutOfDate());

        if (Launcher.UPDATE_CHECKER.isOutOfDate()) {
            lblUpdateSub.setText(i18n("update.newest_version", Launcher.UPDATE_CHECKER.getNewVersion().toString()));
            lblUpdateSub.getStyleClass().setAll("update-label");

            lblUpdate.setText(i18n("update.found"));
            lblUpdate.getStyleClass().setAll("update-label");
        } else {
            lblUpdateSub.setText(i18n("update.latest"));
            lblUpdateSub.getStyleClass().setAll("subtitle-label");

            lblUpdate.setText(i18n("update"));
            lblUpdate.getStyleClass().setAll();
        }
    }

    @FXML
    private void onUpdate() {
        Launcher.UPDATE_CHECKER.checkOutdate();
    }
}
