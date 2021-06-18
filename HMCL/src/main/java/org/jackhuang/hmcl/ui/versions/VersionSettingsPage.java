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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.ImagePickerItem;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionSettingsPage extends StackPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State("", null, false, false, false));

    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private String versionId;
    private boolean javaItemsLoaded;

    @FXML private VBox rootPane;
    @FXML private ScrollPane scroll;
    @FXML private ComponentList universalComponentList;
    @FXML private ComponentList clientComponentList;
    @FXML private ComponentList serverComponentList;
    @FXML private HBox universalHBox;
    @FXML private HBox clientHBox;
    @FXML private HBox serverHBox;
    @FXML private JFXTextField txtWidth;
    @FXML private JFXTextField txtHeight;
    @FXML private JFXTextField txtMaxMemory;
    @FXML private JFXTextField txtNativesPath;
    @FXML private JFXTextField txtJVMArgs;
    @FXML private JFXTextField txtGameArgs;
    @FXML private JFXTextField txtMetaspace;
    @FXML private JFXTextField txtWrapper;
    @FXML private JFXTextField txtPrecallingCommand;
    @FXML private JFXTextField txtServerIP;
    @FXML private ComponentList iconPickerItemWrapper;
    @FXML private JFXComboBox<LauncherVisibility> cboLauncherVisibility;
    @FXML private JFXCheckBox chkFullscreen;
    @FXML private Label lblPhysicalMemory;
    @FXML private Label lblCustomizedNativesPath;
    @FXML private JFXToggleButton chkNoJVMArgs;
    @FXML private JFXToggleButton chkNoGameCheck;
    @FXML private JFXToggleButton chkNoJVMCheck;
    @FXML private MultiFileItem<JavaVersion> javaItem;
    @FXML private MultiFileItem<GameDirectoryType> gameDirItem;
    @FXML private MultiFileItem<NativesDirectoryType> nativesDirItem;
    @FXML private JFXToggleButton chkShowLogs;
    @FXML private ImagePickerItem iconPickerItem;
    @FXML private JFXCheckBox chkEnableSpecificSettings;
    @FXML private JFXToggleButton chkServerUseGui;
    @FXML private BorderPane settingsTypePane;

    private InvalidationListener specificSettingsListener = any -> {
        chkEnableSpecificSettings.setSelected(!lastVersionSetting.isUsesGlobal());
    };

    private InvalidationListener javaListener = any -> initJavaSubtitle();

    public VersionSettingsPage() {
        FXUtils.loadFXML(this, "/assets/fxml/version/version-settings.fxml");
        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onDecoratorPageNavigating);

        cboLauncherVisibility.getItems().setAll(LauncherVisibility.values());
        cboLauncherVisibility.setConverter(stringConverter(e -> i18n("settings.universal.launcher_visibility." + e.name().toLowerCase())));
    }

    @FXML
    private void initialize() {
        lblPhysicalMemory.setText(i18n("settings.physical_memory") + ": " + OperatingSystem.TOTAL_MEMORY + "MB");

        FXUtils.smoothScrolling(scroll);

        Task.supplyAsync(JavaVersion::getJavas).thenAcceptAsync(Schedulers.javafx(), list -> {
            javaItem.loadChildren(list.stream()
                    .map(javaVersion -> javaItem.createChildren(javaVersion.getVersion() + i18n("settings.universal.java_directory.bit",
                            javaVersion.getPlatform().getBit()), javaVersion.getBinary().toString(), javaVersion))
                    .collect(Collectors.toList()));
            javaItemsLoaded = true;
            initializeSelectedJava();
        }).start();

        javaItem.setSelectedData(null);
        javaItem.setFallbackData(JavaVersion.fromCurrentEnvironment());
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            javaItem.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "java.exe", "javaw.exe"));

        gameDirItem.setCustomUserData(GameDirectoryType.CUSTOM);
        gameDirItem.loadChildren(Arrays.asList(
                gameDirItem.createChildren(i18n("settings.client.game_dir.default"), GameDirectoryType.ROOT_FOLDER),
                gameDirItem.createChildren(i18n("settings.client.game_dir.independent"), GameDirectoryType.VERSION_FOLDER)
        ));

        nativesDirItem.setCustomUserData(NativesDirectoryType.CUSTOM);
        nativesDirItem.loadChildren(Arrays.asList(
                nativesDirItem.createChildren(i18n("settings.client.natives_directory.default"), NativesDirectoryType.VERSION_FOLDER)
        ));

        chkEnableSpecificSettings.selectedProperty().addListener((a, b, newValue) -> {
            if (versionId == null) return;

            // do not call versionSettings.setUsesGlobal(true/false)
            // because versionSettings can be the global one.
            // global versionSettings.usesGlobal is always true.
            if (newValue)
                profile.getRepository().specializeVersionSetting(versionId);
            else
                profile.getRepository().globalizeVersionSetting(versionId);

            Platform.runLater(() -> loadVersion(profile, versionId));
        });

        universalComponentList.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        clientComponentList.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        serverComponentList.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
    }

    private void bindVersionSettingProperties(VersionSetting vs) {
        // bind new data fields
        FXUtils.bindInt(txtMaxMemory, vs.maxMemoryProperty());
        FXUtils.bindString(javaItem.getTxtCustom(), vs.javaDirProperty());
        FXUtils.bindString(txtJVMArgs, vs.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, vs.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, vs.permSizeProperty());
        FXUtils.bindString(txtWrapper, vs.wrapperProperty());
        FXUtils.bindString(txtPrecallingCommand, vs.preLaunchCommandProperty());
        FXUtils.bindBoolean(chkNoGameCheck, vs.notCheckGameProperty());
        FXUtils.bindBoolean(chkNoJVMCheck, vs.notCheckJVMProperty());
        FXUtils.bindBoolean(chkNoJVMArgs, vs.noJVMArgsProperty());
        FXUtils.bindEnum(cboLauncherVisibility, vs.launcherVisibilityProperty());

        vs.usesGlobalProperty().addListener(specificSettingsListener);
        if (!vs.isGlobal())
            chkEnableSpecificSettings.setSelected(!vs.isUsesGlobal());

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaItem.isCustomToggle(newValue)) {
                vs.setUsesCustomJavaDir();
            } else {
                vs.setJavaVersion((JavaVersion) newValue.getUserData());
            }
        });

        vs.javaDirProperty().addListener(javaListener);
        vs.javaProperty().addListener(javaListener);

        if (vs.containsClientType())
            bindClientVersionSettingProperties(vs);
        
        if (vs.containsServerType())
            bindServerVersionSettingProperties(vs);
    }

    private void bindClientVersionSettingProperties(VersionSetting vs) {
        FXUtils.bindInt(txtWidth, vs.widthProperty());
        FXUtils.bindInt(txtHeight, vs.heightProperty());
        FXUtils.bindString(gameDirItem.getTxtCustom(), vs.gameDirProperty());
        FXUtils.bindString(nativesDirItem.getTxtCustom(), vs.nativesDirProperty());
        FXUtils.bindString(txtServerIP, vs.serverIpProperty());
        FXUtils.bindBoolean(chkFullscreen, vs.fullscreenProperty());
        FXUtils.bindBoolean(chkShowLogs, vs.showLogsProperty());

        gameDirItem.selectedDataProperty().bindBidirectional(vs.gameDirTypeProperty());
        gameDirItem.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath()).normalize().toString(),
                vs.gameDirProperty(), vs.gameDirTypeProperty()));
        
        nativesDirItem.selectedDataProperty().bindBidirectional(vs.nativesDirTypeProperty());
        nativesDirItem.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath() + "/natives").normalize().toString(),
                vs.nativesDirProperty(), vs.nativesDirTypeProperty()));
    }

    private void bindServerVersionSettingProperties(VersionSetting vs) {
        FXUtils.bindBoolean(chkServerUseGui, vs.useGraphicsUserInterfaceProperty());
    }

    private void unbindVersionSettingProperties(VersionSetting vs) {
        // unbind data fields
        FXUtils.unbindInt(txtMaxMemory, vs.maxMemoryProperty());
        FXUtils.unbindString(javaItem.getTxtCustom(), vs.javaDirProperty());
        FXUtils.unbindString(txtJVMArgs, vs.javaArgsProperty());
        FXUtils.unbindString(txtGameArgs, vs.minecraftArgsProperty());
        FXUtils.unbindString(txtMetaspace, vs.permSizeProperty());
        FXUtils.unbindString(txtWrapper, vs.wrapperProperty());
        FXUtils.unbindString(txtPrecallingCommand, vs.preLaunchCommandProperty());
        FXUtils.unbindBoolean(chkNoGameCheck, vs.notCheckGameProperty());
        FXUtils.unbindBoolean(chkNoJVMCheck, vs.notCheckJVMProperty());
        FXUtils.unbindBoolean(chkNoJVMArgs, vs.noJVMArgsProperty());
        FXUtils.unbindEnum(cboLauncherVisibility);

        vs.usesGlobalProperty().removeListener(specificSettingsListener);
        vs.javaDirProperty().removeListener(javaListener);
        vs.javaProperty().removeListener(javaListener);

        javaItem.setToggleSelectedListener(null);

        if (vs.containsClientType())
            unbindClientVersionSettingProperties(vs);
        
        if (vs.containsServerType())
            unbindServerVersionSettingProperties(vs);
    }

    private void unbindClientVersionSettingProperties(VersionSetting vs) {
        FXUtils.unbindInt(txtWidth, vs.widthProperty());
        FXUtils.unbindInt(txtHeight, vs.heightProperty());
        FXUtils.unbindString(gameDirItem.getTxtCustom(), vs.gameDirProperty());
        FXUtils.unbindString(nativesDirItem.getTxtCustom(), vs.nativesDirProperty());
        FXUtils.unbindString(txtServerIP, vs.serverIpProperty());
        FXUtils.unbindBoolean(chkFullscreen, vs.fullscreenProperty());
        FXUtils.unbindBoolean(chkShowLogs, vs.showLogsProperty());

        gameDirItem.selectedDataProperty().unbindBidirectional(vs.gameDirTypeProperty());
        gameDirItem.subtitleProperty().unbind();
        nativesDirItem.selectedDataProperty().unbindBidirectional(vs.nativesDirTypeProperty());
        nativesDirItem.subtitleProperty().unbind();
    }

    private void unbindServerVersionSettingProperties(VersionSetting vs) {
        FXUtils.unbindBoolean(chkServerUseGui, vs.useGraphicsUserInterfaceProperty());
    }

    public void loadVersion(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;

        if (versionId == null) {
            rootPane.getChildren().remove(iconPickerItemWrapper);
            rootPane.getChildren().remove(settingsTypePane);
            chkEnableSpecificSettings.setSelected(true);
            state.set(State.fromTitle(Profiles.getProfileDisplayName(profile) + " - " + i18n("settings.type.global.manage")));
        }

        VersionSetting versionSetting = profile.getVersionSetting(versionId);

        gameDirItem.setDisable(versionId != null && profile.getRepository().isModpack(versionId));
        settingsTypePane.setDisable(versionId != null && profile.getRepository().isModpack(versionId));

        if (lastVersionSetting != null)
            unbindVersionSettingProperties(lastVersionSetting);

        bindVersionSettingProperties(versionSetting);

        lastVersionSetting = versionSetting;

        Platform.runLater(() -> {

            rootPane.getChildren().removeAll(clientHBox, clientComponentList,
                                             serverHBox, serverComponentList);

            if (versionSetting.containsClientType()) {
                rootPane.getChildren().addAll(clientHBox, clientComponentList);
            }
            if (versionSetting.containsServerType()) {
                rootPane.getChildren().addAll(serverHBox, serverComponentList);
            }
            
        });

        initializeSelectedJava();
        initJavaSubtitle();

        loadIcon();
    }

    private void initializeSelectedJava() {
        if (lastVersionSetting == null
                || !javaItemsLoaded /* JREs are still being loaded */) {
            return;
        }

        if (lastVersionSetting.isUsesCustomJavaDir()) {
            javaItem.getGroup().getToggles().stream()
                    .filter(javaItem::isCustomToggle)
                    .findFirst().get()
                    .setSelected(true);
        } else {
            try {
                javaItem.setSelectedData(lastVersionSetting.getJavaVersion());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initJavaSubtitle() {
        VersionSetting versionSetting = lastVersionSetting;
        if (versionSetting == null)
            return;
        Task.supplyAsync(versionSetting::getJavaVersion)
                .thenAcceptAsync(Schedulers.javafx(), javaVersion -> javaItem.setSubtitle(Optional.ofNullable(javaVersion)
                        .map(JavaVersion::getBinary).map(Path::toString).orElse("Invalid Java Path")))
                .start();
    }

    @FXML
    private void editGlobalSettings() {
        Versions.modifyGlobalSettings(profile);
    }

    @FXML
    private void onExploreIcon() {
        if (versionId == null)
            return;

        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.png"), "*.png"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile != null) {
            File iconFile = profile.getRepository().getVersionIconFile(versionId);
            try {
                FileUtils.copyFile(selectedFile, iconFile);
                loadIcon();
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Failed to copy icon file from " + selectedFile + " to " + iconFile, e);
            }
        }
    }

    @FXML
    private void onDeleteIcon() {
        if (versionId == null)
            return;

        File iconFile = profile.getRepository().getVersionIconFile(versionId);
        if (iconFile.exists())
            iconFile.delete();
        loadIcon();
    }

    private void loadIcon() {
        if (versionId == null) {
            iconPickerItem.setImage(newImage("/assets/img/grass.png"));
            return;
        }

        File iconFile = profile.getRepository().getVersionIconFile(versionId);
        if (iconFile.exists())
            iconPickerItem.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        else
            iconPickerItem.setImage(newImage("/assets/img/grass.png"));
        FXUtils.limitSize(iconPickerItem.getImageView(), 32, 32);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
