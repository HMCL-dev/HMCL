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
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;

import java.net.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

public final class SettingsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("settings.launcher"));

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
    private MultiColorItem themeItem;
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

        txtProxyHost.setText(Settings.INSTANCE.getProxyHost());
        txtProxyHost.textProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setProxyHost(newValue));

        txtProxyPort.setText(Settings.INSTANCE.getProxyPort());
        txtProxyPort.textProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setProxyPort(newValue));

        txtProxyUsername.setText(Settings.INSTANCE.getProxyUser());
        txtProxyUsername.textProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setProxyUser(newValue));

        txtProxyPassword.setText(Settings.INSTANCE.getProxyPass());
        txtProxyPassword.textProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setProxyPass(newValue));

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
        if (!Settings.INSTANCE.hasProxy())
            chkNoProxy.setSelected(true);
        else
            chkManualProxy.setSelected(true);
        proxyPane.disableProperty().bind(chkNoProxy.selectedProperty());

        hasProxyGroup.selectedToggleProperty().addListener((a, b, newValue) ->
                Settings.INSTANCE.setHasProxy(newValue != chkNoProxy));

        proxyConfigurationGroup.selectedToggleProperty().addListener((a, b, newValue) ->
                Settings.INSTANCE.setProxyType((Proxy.Type) newValue.getUserData()));

        chkProxyAuthentication.setSelected(Settings.INSTANCE.hasProxyAuth());
        chkProxyAuthentication.selectedProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setHasProxyAuth(newValue));
        authPane.disableProperty().bind(chkProxyAuthentication.selectedProperty().not());

        fileCommonLocation.setProperty(Settings.INSTANCE.commonPathProperty());

        FXUtils.installTooltip(btnUpdate, Main.i18n("update.tooltip"));
        checkUpdate();

        // background
        backgroundItem.loadChildren(Collections.singletonList(
                backgroundItem.createChildren(Main.i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
        ));

        FXUtils.bindString(backgroundItem.getTxtCustom(), Settings.INSTANCE.backgroundImageProperty());

        backgroundItem.setCustomUserData(EnumBackgroundImage.CUSTOM);
        backgroundItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Settings.INSTANCE.getBackgroundImageType()).findFirst().ifPresent(it -> it.setSelected(true));

        Settings.INSTANCE.backgroundImageProperty().setChangedListener(it -> initBackgroundItemSubtitle());
        Settings.INSTANCE.backgroundImageTypeProperty().setChangedListener(it -> initBackgroundItemSubtitle());
        initBackgroundItemSubtitle();

        backgroundItem.setToggleSelectedListener(newValue ->
                Settings.INSTANCE.setBackgroundImageType((EnumBackgroundImage) newValue.getUserData()));

        // theme
        themeItem.loadChildren(Arrays.asList(
                themeItem.createChildren(Main.i18n("color.blue"), Theme.BLUE),
                themeItem.createChildren(Main.i18n("color.dark_blue"), Theme.DARK_BLUE),
                themeItem.createChildren(Main.i18n("color.green"), Theme.GREEN),
                themeItem.createChildren(Main.i18n("color.orange"), Theme.ORANGE),
                themeItem.createChildren(Main.i18n("color.purple"), Theme.PURPLE),
                themeItem.createChildren(Main.i18n("color.red"), Theme.RED)
        ));

        if (Settings.INSTANCE.getTheme().isCustom())
            themeItem.setColor(Color.web(Settings.INSTANCE.getTheme().getColor()));

        themeItem.setToggleSelectedListener(newValue -> {
            if (newValue.getUserData() != null) {
                Settings.INSTANCE.setTheme((Theme) newValue.getUserData());
                themeItem.setOnColorPickerChanged(null);
            } else {
                themeItem.setOnColorPickerChanged(color ->
                        Settings.INSTANCE.setTheme(Theme.custom(Theme.getColorDisplayName(color))));
            }
        });

        themeItem.getGroup().getToggles().stream().filter(it -> Settings.INSTANCE.getTheme() == it.getUserData() || Settings.INSTANCE.getTheme().isCustom() && themeItem.isCustomToggle(it)).findFirst().ifPresent(it -> it.setSelected(true));

        Settings.INSTANCE.themeProperty().setChangedListenerAndOperate(it -> {
            if (it.isCustom())
                themeItem.setSubtitle(it.getName());
            else
                themeItem.setSubtitle(Main.i18n("color." + it.getName().toLowerCase()));

            Controllers.getScene().getStylesheets().setAll(it.getStylesheets());
        });
    }

    private void initBackgroundItemSubtitle() {
        switch (Settings.INSTANCE.getBackgroundImageType()) {
            case DEFAULT:
                backgroundItem.setSubtitle(Main.i18n("launcher.background.default"));
                break;
            case CUSTOM:
                backgroundItem.setSubtitle(Settings.INSTANCE.getBackgroundImage());
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
        btnUpdate.setVisible(Main.UPDATE_CHECKER.isOutOfDate());

        if (Main.UPDATE_CHECKER.isOutOfDate()) {
            lblUpdateSub.setText(Main.i18n("update.newest_version", Main.UPDATE_CHECKER.getNewVersion().toString()));
            lblUpdateSub.getStyleClass().setAll("update-label");

            lblUpdate.setText(Main.i18n("update.found"));
            lblUpdate.getStyleClass().setAll("update-label");
        } else {
            lblUpdateSub.setText(Main.i18n("update.latest"));
            lblUpdateSub.getStyleClass().setAll("subtitle-label");

            lblUpdate.setText(Main.i18n("update"));
            lblUpdate.getStyleClass().setAll();
        }
    }

    @FXML
    private void onUpdate() {
        Main.UPDATE_CHECKER.checkOutdate();
    }
}
