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

import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.NumberValidator;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class VersionSettingsController {
    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private String versionId;

    @FXML private VBox rootPane;
    @FXML private ScrollPane scroll;
    @FXML private JFXTextField txtWidth;
    @FXML private JFXTextField txtHeight;
    @FXML private JFXTextField txtMaxMemory;
    @FXML private JFXTextField txtJVMArgs;
    @FXML private JFXTextField txtGameArgs;
    @FXML private JFXTextField txtMetaspace;
    @FXML private JFXTextField txtWrapper;
    @FXML private JFXTextField txtPrecallingCommand;
    @FXML private JFXTextField txtServerIP;
    @FXML private ComponentList advancedSettingsPane;
    @FXML private JFXComboBox<?> cboLauncherVisibility;
    @FXML private JFXCheckBox chkFullscreen;
    @FXML private Label lblPhysicalMemory;
    @FXML private JFXToggleButton chkNoJVMArgs;
    @FXML private JFXToggleButton chkNoCommon;
    @FXML private JFXToggleButton chkNoGameCheck;
    @FXML private MultiFileItem globalItem;
    @FXML private MultiFileItem javaItem;
    @FXML private MultiFileItem gameDirItem;
    @FXML private JFXToggleButton chkShowLogs;
    @FXML private JFXButton btnIconSelection;
    @FXML private ImageView iconView;

    @FXML
    private void initialize() {
        lblPhysicalMemory.setText(Main.i18n("settings.physical_memory") + ": " + OperatingSystem.TOTAL_MEMORY + "MB");

        FXUtils.smoothScrolling(scroll);

        double limitWidth = 300;
        FXUtils.limitWidth(txtMaxMemory, limitWidth);
        FXUtils.limitWidth(cboLauncherVisibility, limitWidth);

        double limitHeight = 10;
        FXUtils.limitHeight(chkNoJVMArgs, limitHeight);
        FXUtils.limitHeight(chkNoCommon, limitHeight);
        FXUtils.limitHeight(chkNoGameCheck, limitHeight);
        FXUtils.limitHeight(chkShowLogs, limitHeight);

        NumberValidator nonnull = new NumberValidator("Must be a number.", false);
        NumberValidator nullable = new NumberValidator("Must be a number.", true);

        txtWidth.setValidators(nonnull);
        FXUtils.setValidateWhileTextChanged(txtWidth);
        txtHeight.setValidators(nonnull);
        FXUtils.setValidateWhileTextChanged(txtHeight);
        txtMaxMemory.setValidators(nonnull);
        FXUtils.setValidateWhileTextChanged(txtMaxMemory);
        txtMetaspace.setValidators(nullable);
        FXUtils.setValidateWhileTextChanged(txtMetaspace);

        Task.of(variables -> {
            variables.set("list", JavaVersion.getJREs().values().stream().map(javaVersion ->
                    javaItem.createChildren(javaVersion.getVersion(), javaVersion.getBinary().getAbsolutePath(), javaVersion)
            ).collect(Collectors.toList()));
        }).subscribe(Schedulers.javafx(), variables ->
                javaItem.loadChildren(variables.<Collection<Node>>get("list"))
        );

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            javaItem.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "java.exe", "javaw.exe"));

        gameDirItem.loadChildren(Arrays.asList(
                gameDirItem.createChildren(Main.i18n("settings.advanced.game_dir.default"), EnumGameDirectory.ROOT_FOLDER),
                gameDirItem.createChildren(Main.i18n("settings.advanced.game_dir.independent"), EnumGameDirectory.VERSION_FOLDER)
        ));

        globalItem.loadChildren(Arrays.asList(
                globalItem.createChildren(Main.i18n("settings.type.global"), true),
                globalItem.createChildren(Main.i18n("settings.type.special"), false)
        ));

        FXUtils.installTooltip(btnIconSelection, 0, 5000, 0, new Tooltip(Main.i18n("button.edit")));
    }

    public void loadVersionSetting(Profile profile, String versionId) {
        rootPane.getChildren().remove(advancedSettingsPane);

        this.profile = profile;
        this.versionId = versionId;

        VersionSetting versionSetting = profile.getVersionSetting(versionId);

        gameDirItem.setDisable(profile.getRepository().isModpack(versionId));
        globalItem.setDisable(profile.getRepository().isModpack(versionId));

        if (lastVersionSetting != null) {
            FXUtils.unbindInt(txtWidth, lastVersionSetting.widthProperty());
            FXUtils.unbindInt(txtHeight, lastVersionSetting.heightProperty());
            FXUtils.unbindInt(txtMaxMemory, lastVersionSetting.maxMemoryProperty());
            FXUtils.unbindString(javaItem.getTxtCustom(), lastVersionSetting.javaDirProperty());
            FXUtils.unbindString(gameDirItem.getTxtCustom(), lastVersionSetting.gameDirProperty());
            FXUtils.unbindString(txtJVMArgs, lastVersionSetting.javaArgsProperty());
            FXUtils.unbindString(txtGameArgs, lastVersionSetting.minecraftArgsProperty());
            FXUtils.unbindString(txtMetaspace, lastVersionSetting.permSizeProperty());
            FXUtils.unbindString(txtWrapper, lastVersionSetting.wrapperProperty());
            FXUtils.unbindString(txtPrecallingCommand, lastVersionSetting.preLaunchCommandProperty());
            FXUtils.unbindString(txtServerIP, lastVersionSetting.serverIpProperty());
            FXUtils.unbindBoolean(chkFullscreen, lastVersionSetting.fullscreenProperty());
            FXUtils.unbindBoolean(chkNoGameCheck, lastVersionSetting.notCheckGameProperty());
            FXUtils.unbindBoolean(chkNoCommon, lastVersionSetting.noCommonProperty());
            FXUtils.unbindBoolean(chkShowLogs, lastVersionSetting.showLogsProperty());
            FXUtils.unbindEnum(cboLauncherVisibility);
        }

        FXUtils.bindInt(txtWidth, versionSetting.widthProperty());
        FXUtils.bindInt(txtHeight, versionSetting.heightProperty());
        FXUtils.bindInt(txtMaxMemory, versionSetting.maxMemoryProperty());
        FXUtils.bindString(javaItem.getTxtCustom(), versionSetting.javaDirProperty());
        FXUtils.bindString(gameDirItem.getTxtCustom(), versionSetting.gameDirProperty());
        FXUtils.bindString(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.bindString(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.bindString(txtPrecallingCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.bindString(txtServerIP, versionSetting.serverIpProperty());
        FXUtils.bindBoolean(chkFullscreen, versionSetting.fullscreenProperty());
        FXUtils.bindBoolean(chkNoGameCheck, versionSetting.notCheckGameProperty());
        FXUtils.bindBoolean(chkNoCommon, versionSetting.noCommonProperty());
        FXUtils.bindBoolean(chkShowLogs, versionSetting.showLogsProperty());
        FXUtils.bindEnum(cboLauncherVisibility, versionSetting.launcherVisibilityProperty());

        String javaGroupKey = "java_group.listener";

        Lang.get(javaItem.getGroup().getProperties(), javaGroupKey, ChangeListener.class)
                .ifPresent(javaItem.getGroup().selectedToggleProperty()::removeListener);

        boolean flag = false;
        JFXRadioButton defaultToggle = null;
        for (Toggle toggle : javaItem.getGroup().getToggles()) {
            if (toggle instanceof JFXRadioButton) {
                if (toggle.getUserData() == Lang.invoke(versionSetting::getJavaVersion)) {
                    toggle.setSelected(true);
                    flag = true;
                } else if (toggle.getUserData() == JavaVersion.fromCurrentEnvironment()) {
                    defaultToggle = (JFXRadioButton) toggle;
                }
            }
        }

        ChangeListener<Toggle> listener = (a, b, newValue) -> {
            if (newValue == javaItem.getRadioCustom()) {
                versionSetting.setJava("Custom");
            } else {
                versionSetting.setJava(((JavaVersion) newValue.getUserData()).getVersion());
            }
        };

        javaItem.getGroup().getProperties().put(javaGroupKey, listener);
        javaItem.getGroup().selectedToggleProperty().addListener(listener);

        if (!flag) {
            Optional.ofNullable(defaultToggle).ifPresent(t -> t.setSelected(true));
        }

        versionSetting.javaDirProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        versionSetting.javaProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        initJavaSubtitle(versionSetting);


        String globalGroupKey = "global_group.listener";

        Lang.get(globalItem.getGroup().getProperties(), globalGroupKey, ChangeListener.class)
                .ifPresent(globalItem.getGroup().selectedToggleProperty()::removeListener);
        ChangeListener<Toggle> globalListener = (a, b, newValue) -> {
            if ((Boolean) newValue.getUserData())
                profile.globalizeVersionSetting(versionId);
            else
                profile.specializeVersionSetting(versionId);

            Platform.runLater(() -> loadVersionSetting(profile, versionId));
        };
        if (versionSetting.isUsesGlobal())
            globalItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Boolean.TRUE).findFirst().ifPresent(it -> it.setSelected(true));
        else
            globalItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Boolean.FALSE).findFirst().ifPresent(it -> it.setSelected(true));
        globalItem.getGroup().getProperties().put(globalGroupKey, globalListener);
        globalItem.getGroup().selectedToggleProperty().addListener(globalListener);
        versionSetting.usesGlobalProperty().setChangedListener(it -> initUsesGlobalSubtitle(versionSetting));
        initUsesGlobalSubtitle(versionSetting);

        String gameDirKey = "game_dir.listener";
        Lang.get(gameDirItem.getGroup().getProperties(), gameDirKey, ChangeListener.class)
                .ifPresent(gameDirItem.getGroup().selectedToggleProperty()::removeListener);

        for (Toggle toggle : gameDirItem.getGroup().getToggles()) {
            if (toggle instanceof JFXRadioButton) {
                if (toggle.getUserData() == versionSetting.getGameDirType()) {
                    toggle.setSelected(true);
                    flag = true;
                }
            }
        }

        gameDirItem.setCustomUserData(EnumGameDirectory.CUSTOM);

        ChangeListener<Toggle> gameDirListener = (a, b, newValue) -> {
            versionSetting.setGameDirType((EnumGameDirectory) newValue.getUserData());
        };

        gameDirItem.getGroup().getProperties().put(gameDirKey, gameDirListener);
        gameDirItem.getGroup().selectedToggleProperty().addListener(gameDirListener);

        versionSetting.gameDirProperty().setChangedListener(it -> initGameDirSubtitle(versionSetting));
        versionSetting.gameDirTypeProperty().setChangedListener(it -> initGameDirSubtitle(versionSetting));
        initGameDirSubtitle(versionSetting);

        lastVersionSetting = versionSetting;

        loadIcon();
    }

    private void initJavaSubtitle(VersionSetting versionSetting) {
        Task.of(variables -> variables.set("java", versionSetting.getJavaVersion()))
                .subscribe(Task.of(Schedulers.javafx(),
                        variables -> javaItem.setSubtitle(variables.<JavaVersion>getOptional("java")
                                .map(JavaVersion::getBinary).map(File::getAbsolutePath).orElse("Invalid Java Directory"))));
    }

    private void initGameDirSubtitle(VersionSetting versionSetting) {
        gameDirItem.setSubtitle(profile.getRepository().getRunDirectory(versionId).getAbsolutePath());
    }

    private void initUsesGlobalSubtitle(VersionSetting versionSetting) {
        globalItem.setSubtitle(Main.i18n(versionSetting.isUsesGlobal() ? "settings.type.global" : "settings.type.special"));
    }

    @FXML
    private void onShowAdvanced() {
        if (!rootPane.getChildren().contains(advancedSettingsPane))
            rootPane.getChildren().add(advancedSettingsPane);
        else
            rootPane.getChildren().remove(advancedSettingsPane);
    }

    @FXML
    private void onExploreIcon() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Main.i18n("extension.png"), "*.png"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile != null) {
            File iconFile = profile.getRepository().getVersionIcon(versionId);
            try {
                FileUtils.copyFile(selectedFile, iconFile);
                loadIcon();
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to copy icon file from " + selectedFile + " to " + iconFile, e);
            }
        }
    }

    private void loadIcon() {
        File iconFile = profile.getRepository().getVersionIcon(versionId);
        if (iconFile.exists())
            iconView.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        else
            iconView.setImage(FXUtils.DEFAULT_ICON);
        FXUtils.limitSize(iconView, 32, 32);
    }
}
