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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.image.Image;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImagePickerItem;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
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
    @FXML private ImagePickerItem iconPickerItem;

    @FXML
    private void initialize() {
        lblPhysicalMemory.setText(Main.i18n("settings.physical_memory") + ": " + OperatingSystem.TOTAL_MEMORY + "MB");

        FXUtils.smoothScrolling(scroll);

        Task.of(variables -> variables.set("list", JavaVersion.getJREs().values().stream().map(javaVersion ->
                javaItem.createChildren(javaVersion.getVersion(), javaVersion.getBinary().getAbsolutePath(), javaVersion)
        ).collect(Collectors.toList()))).subscribe(Schedulers.javafx(), variables ->
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

        boolean flag = false;
        Toggle defaultToggle = null;
        for (Toggle toggle : javaItem.getGroup().getToggles()) {
            if (toggle.getUserData() == Lang.invoke(versionSetting::getJavaVersion)) {
                toggle.setSelected(true);
                flag = true;
            } else if (toggle.getUserData() == JavaVersion.fromCurrentEnvironment()) {
                defaultToggle = toggle;
            }
        }

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaItem.isCustomToggle(newValue)) {
                versionSetting.setJava("Custom");
            } else {
                versionSetting.setJava(((JavaVersion) newValue.getUserData()).getVersion());
            }
        });

        if (!flag) {
            Optional.ofNullable(defaultToggle).ifPresent(t -> t.setSelected(true));
        }

        versionSetting.javaDirProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        versionSetting.javaProperty().setChangedListener(it -> initJavaSubtitle(versionSetting));
        initJavaSubtitle(versionSetting);

        if (versionSetting.isUsesGlobal())
            globalItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Boolean.TRUE).findFirst().ifPresent(it -> it.setSelected(true));
        else
            globalItem.getGroup().getToggles().stream().filter(it -> it.getUserData() == Boolean.FALSE).findFirst().ifPresent(it -> it.setSelected(true));
        globalItem.setToggleSelectedListener(newValue -> {
            if ((Boolean) newValue.getUserData())
                profile.globalizeVersionSetting(versionId);
            else
                profile.specializeVersionSetting(versionId);

            Platform.runLater(() -> loadVersionSetting(profile, versionId));
        });

        versionSetting.usesGlobalProperty().setChangedListenerAndOperate(it ->
                globalItem.setSubtitle(Main.i18n(versionSetting.isUsesGlobal() ? "settings.type.global" : "settings.type.special")));

        gameDirItem.getGroup().getToggles().stream()
                .filter(it -> it.getUserData() == versionSetting.getGameDirType())
                .findFirst().ifPresent(toggle -> toggle.setSelected(true));

        gameDirItem.setCustomUserData(EnumGameDirectory.CUSTOM);
        gameDirItem.setToggleSelectedListener(newValue -> versionSetting.setGameDirType((EnumGameDirectory) newValue.getUserData()));

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
            iconPickerItem.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        else
            iconPickerItem.setImage(FXUtils.DEFAULT_ICON);
        FXUtils.limitSize(iconPickerItem.getImageView(), 32, 32);
    }
}
