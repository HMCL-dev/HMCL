/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.construct.FileItem;
import org.jackhuang.hmcl.ui.construct.FontComboBox;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;

import java.util.Arrays;
import java.util.Collections;

public final class SettingsPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("settings.launcher"));

    @FXML
    private JFXTextField txtProxyHost;
    @FXML
    private JFXTextField txtProxyPort;
    @FXML
    private JFXTextField txtProxyUsername;
    @FXML
    private JFXTextField txtProxyPassword;
    @FXML
    private JFXTextField txtFontSize;
    @FXML
    private JFXComboBox<?> cboProxyType;
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

    {
        FXUtils.loadFXML(this, "/assets/fxml/setting.fxml");

        FXUtils.limitWidth(cboLanguage, 400);
        FXUtils.limitWidth(cboDownloadSource, 400);

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

        cboProxyType.getSelectionModel().select(Proxies.PROXIES.indexOf(Settings.INSTANCE.getProxyType()));
        cboProxyType.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) -> Settings.INSTANCE.setProxyType(Proxies.getProxyType(newValue.intValue())));

        fileCommonLocation.setProperty(Settings.INSTANCE.commonPathProperty());

        FXUtils.installTooltip(btnUpdate, 0, 5000, 0, new Tooltip(Main.i18n("update.tooltip")));
        checkUpdate();

        backgroundItem.loadChildren(Collections.singletonList(
                backgroundItem.createChildren(Main.i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
        ));

        FXUtils.bindString(backgroundItem.getTxtCustom(), Settings.INSTANCE.backgroundImageProperty());

        backgroundItem.setCustomUserData(EnumBackgroundImage.CUSTOM);
        backgroundItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Settings.INSTANCE.getBackgroundImageType()).findFirst().ifPresent(it -> it.setSelected(true));

        Settings.INSTANCE.backgroundImageProperty().setChangedListener(it -> initBackgroundItemSubtitle());
        Settings.INSTANCE.backgroundImageTypeProperty().setChangedListener(it -> initBackgroundItemSubtitle());
        initBackgroundItemSubtitle();

        backgroundItem.setToggleSelectedListener(newValue -> {
            Settings.INSTANCE.setBackgroundImageType((EnumBackgroundImage) newValue.getUserData());
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
