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

import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionSettingsPage extends StackPane implements DecoratorPage, VersionPage.VersionLoadable {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State("", null, false, false, false));

    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private String versionId;
    private boolean javaItemsLoaded;

    private final VBox rootPane;
    private final JFXTextField txtWidth;
    private final JFXTextField txtHeight;
    private final JFXTextField txtJVMArgs;
    private final JFXTextField txtGameArgs;
    private final JFXTextField txtMetaspace;
    private final JFXTextField txtWrapper;
    private final JFXTextField txtPrecallingCommand;
    private final JFXTextField txtServerIP;
    private final ComponentList componentList;
    private final ComponentList iconPickerItemWrapper;
    private final JFXComboBox<LauncherVisibility> cboLauncherVisibility;
    private final JFXCheckBox chkAutoAllocate;
    private final JFXCheckBox chkFullscreen;
    private final JFXToggleButton chkNoJVMArgs;
    private final JFXToggleButton chkNoGameCheck;
    private final JFXToggleButton chkNoJVMCheck;
    private final JFXToggleButton chkUseNativeGLFW;
    private final JFXToggleButton chkUseNativeOpenAL;
    private final MultiFileItem<JavaVersion> javaItem;
    private final MultiFileItem<GameDirectoryType> gameDirItem;
    private final MultiFileItem<NativesDirectoryType> nativesDirItem;
    private final JFXComboBox<ProcessPriority> cboProcessPriority;
    private final JFXToggleButton chkShowLogs;
    private final ImagePickerItem iconPickerItem;
    private final JFXCheckBox chkEnableSpecificSettings;
    private final BorderPane settingsTypePane;

    private final InvalidationListener specificSettingsListener;

    private final InvalidationListener javaListener = any -> initJavaSubtitle();

    private boolean uiVisible = false;
    private final IntegerProperty maxMemoryProperty = new SimpleIntegerProperty();
    private final ObjectProperty<OperatingSystem.PhysicalMemoryStatus> memoryStatusProperty = new SimpleObjectProperty<>(OperatingSystem.PhysicalMemoryStatus.INVALID);

    public VersionSettingsPage() {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        getChildren().setAll(scrollPane);

        rootPane = new VBox();
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");

        {
            iconPickerItemWrapper = new ComponentList();
            iconPickerItem = new ImagePickerItem();
            iconPickerItem.setImage(new Image("/assets/img/icon.png"));
            iconPickerItem.setTitle(i18n("settings.icon"));
            iconPickerItem.setOnSelectButtonClicked(e -> onExploreIcon());
            iconPickerItem.setOnDeleteButtonClicked(e -> onDeleteIcon());
            iconPickerItemWrapper.getContent().setAll(iconPickerItem);
        }

        {
            settingsTypePane = new BorderPane();

            chkEnableSpecificSettings = new JFXCheckBox();
            settingsTypePane.setLeft(chkEnableSpecificSettings);
            chkEnableSpecificSettings.setText(i18n("settings.type.special.enable"));
            BorderPane.setAlignment(chkEnableSpecificSettings, Pos.CENTER_RIGHT);

            JFXButton editGlobalSettingsButton = new JFXButton();
            settingsTypePane.setRight(editGlobalSettingsButton);
            editGlobalSettingsButton.setText(i18n("settings.type.global.edit"));
            editGlobalSettingsButton.getStyleClass().add("jfx-button-raised");
            editGlobalSettingsButton.setButtonType(JFXButton.ButtonType.RAISED);
            editGlobalSettingsButton.disableProperty().bind(chkEnableSpecificSettings.selectedProperty());
            BorderPane.setAlignment(editGlobalSettingsButton, Pos.CENTER_RIGHT);
            editGlobalSettingsButton.setOnMouseClicked(e -> editGlobalSettings());
        }

        {
            componentList = new ComponentList();
            componentList.setDepth(1);

            javaItem = new MultiFileItem<>(true);
            javaItem.setTitle(i18n("settings.game.java_directory"));
            javaItem.setChooserTitle(i18n("settings.game.java_directory.choose"));
            javaItem.setHasSubtitle(true);
            javaItem.setCustomText(i18n("settings.custom"));
            javaItem.setDirectory(false);

            gameDirItem = new MultiFileItem<>(true);
            gameDirItem.setTitle(i18n("settings.game.working_directory"));
            gameDirItem.setChooserTitle(i18n("settings.game.working_directory.choose"));
            gameDirItem.setHasSubtitle(true);
            gameDirItem.setCustomText(i18n("settings.custom"));
            gameDirItem.setDirectory(true);

            VBox maxMemoryPane = new VBox(8);
            {
                Label title = new Label(i18n("settings.memory"));
                VBox.setMargin(title, new Insets(0, 0, 8, 0));

                chkAutoAllocate = new JFXCheckBox(i18n("settings.memory.auto_allocate"));
                VBox.setMargin(chkAutoAllocate, new Insets(0, 0, 8, 5));

                HBox lowerBoundPane = new HBox(8);
                lowerBoundPane.setAlignment(Pos.CENTER);
                VBox.setMargin(lowerBoundPane, new Insets(0, 0, 0, 16));
                {
                    Label label = new Label();
                    label.textProperty().bind(Bindings.createStringBinding(() -> {
                        if (chkAutoAllocate.isSelected()) {
                            return i18n("settings.memory.lower_bound");
                        } else {
                            return i18n("settings.memory");
                        }
                    }, chkAutoAllocate.selectedProperty()));

                    JFXSlider slider = new JFXSlider(0, 1, 0);
                    HBox.setMargin(slider, new Insets(0, 0, 0, 8));
                    HBox.setHgrow(slider, Priority.ALWAYS);
                    slider.setValueFactory(self -> Bindings.createStringBinding(() -> (int) (self.getValue() * 100) + "%", self.valueProperty()));
                    AtomicBoolean changedByTextField = new AtomicBoolean(false);
                    FXUtils.onChangeAndOperate(maxMemoryProperty, maxMemory -> {
                        changedByTextField.set(true);
                        slider.setValue(maxMemory.intValue() * 1.0 / OperatingSystem.TOTAL_MEMORY);
                        changedByTextField.set(false);
                    });
                    slider.valueProperty().addListener((value, oldVal, newVal) -> {
                        if (changedByTextField.get()) return;
                        maxMemoryProperty.set((int) (value.getValue().doubleValue() * OperatingSystem.TOTAL_MEMORY));
                    });

                    JFXTextField txtMaxMemory = new JFXTextField();
                    FXUtils.setLimitWidth(txtMaxMemory, 60);
                    FXUtils.setValidateWhileTextChanged(txtMaxMemory, true);
                    txtMaxMemory.textProperty().bindBidirectional(maxMemoryProperty, SafeStringConverter.fromInteger());
                    txtMaxMemory.setValidators(new NumberValidator(i18n("input.number"), false));

                    lowerBoundPane.getChildren().setAll(label, slider, txtMaxMemory, new Label("MB"));
                }

                StackPane progressBarPane = new StackPane();
                progressBarPane.setAlignment(Pos.CENTER_LEFT);
                VBox.setMargin(progressBarPane, new Insets(8, 0, 0, 16));
                {
                    progressBarPane.setMinHeight(4);
                    progressBarPane.getStyleClass().add("memory-total");

                    StackPane usedMemory = new StackPane();
                    usedMemory.getStyleClass().add("memory-used");
                    usedMemory.maxWidthProperty().bind(Bindings.createDoubleBinding(() ->
                                    progressBarPane.getWidth() *
                                            (memoryStatusProperty.get().getUsed() * 1.0 / memoryStatusProperty.get().getTotal()), progressBarPane.widthProperty(),
                            memoryStatusProperty));
                    StackPane allocateMemory = new StackPane();
                    allocateMemory.getStyleClass().add("memory-allocate");
                    allocateMemory.maxWidthProperty().bind(Bindings.createDoubleBinding(() ->
                                    progressBarPane.getWidth() *
                                            Math.min(1.0,
                                                    (double) (HMCLGameRepository.getAllocatedMemory(maxMemoryProperty.get() * 1024L * 1024L, memoryStatusProperty.get().getAvailable(), chkAutoAllocate.isSelected())
                                                            + memoryStatusProperty.get().getUsed()) / memoryStatusProperty.get().getTotal()), progressBarPane.widthProperty(),
                            maxMemoryProperty, memoryStatusProperty, chkAutoAllocate.selectedProperty()));

                    progressBarPane.getChildren().setAll(allocateMemory, usedMemory);
                }

                BorderPane digitalPane = new BorderPane();
                VBox.setMargin(digitalPane, new Insets(0, 0, 0, 16));
                {
                    Label lblPhysicalMemory = new Label();
                    lblPhysicalMemory.getStyleClass().add("memory-label");
                    digitalPane.setLeft(lblPhysicalMemory);
                    lblPhysicalMemory.textProperty().bind(Bindings.createStringBinding(() -> {
                        return i18n("settings.memory.used_per_total", memoryStatusProperty.get().getUsedGB(), memoryStatusProperty.get().getTotalGB());
                    }, memoryStatusProperty));

                    Label lblAllocateMemory = new Label();
                    lblAllocateMemory.textProperty().bind(Bindings.createStringBinding(() -> {
                        long maxMemory = Lang.parseInt(maxMemoryProperty.get(), 0) * 1024L * 1024L;
                        return i18n(memoryStatusProperty.get().hasAvailable() && maxMemory > memoryStatusProperty.get().getAvailable()
                                        ? (chkAutoAllocate.isSelected() ? "settings.memory.allocate.auto.exceeded" : "settings.memory.allocate.manual.exceeded")
                                        : (chkAutoAllocate.isSelected() ? "settings.memory.allocate.auto" : "settings.memory.allocate.manual"),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(maxMemory),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(HMCLGameRepository.getAllocatedMemory(maxMemory, memoryStatusProperty.get().getAvailable(), chkAutoAllocate.isSelected())),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(memoryStatusProperty.get().getAvailable()));
                    }, memoryStatusProperty, maxMemoryProperty, chkAutoAllocate.selectedProperty()));
                    lblAllocateMemory.getStyleClass().add("memory-label");
                    digitalPane.setRight(lblAllocateMemory);
                }

                maxMemoryPane.getChildren().setAll(title, chkAutoAllocate, lowerBoundPane, progressBarPane, digitalPane);
            }

            BorderPane launcherVisibilityPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.launcher_visible"));
                launcherVisibilityPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                cboLauncherVisibility = new JFXComboBox<>();
                launcherVisibilityPane.setRight(cboLauncherVisibility);
                BorderPane.setAlignment(cboLauncherVisibility, Pos.CENTER_RIGHT);
                FXUtils.setLimitWidth(cboLauncherVisibility, 300);
            }

            BorderPane dimensionPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.game.dimension"));
                dimensionPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                BorderPane right = new BorderPane();
                dimensionPane.setRight(right);
                {
                    HBox hbox = new HBox();
                    right.setLeft(hbox);
                    hbox.setPrefWidth(210);
                    hbox.setSpacing(3);
                    hbox.setAlignment(Pos.CENTER);
                    BorderPane.setAlignment(hbox, Pos.CENTER);
                    {
                        txtWidth = new JFXTextField();
                        txtWidth.setPromptText("800");
                        txtWidth.setPrefWidth(100);
                        FXUtils.setValidateWhileTextChanged(txtWidth, true);
                        txtWidth.getValidators().setAll(new NumberValidator(i18n("input.number"), false));

                        Label x = new Label("x");

                        txtHeight = new JFXTextField();
                        txtHeight.setPromptText("480");
                        txtHeight.setPrefWidth(100);
                        FXUtils.setValidateWhileTextChanged(txtHeight, true);
                        txtHeight.getValidators().setAll(new NumberValidator(i18n("input.number"), false));

                        hbox.getChildren().setAll(txtWidth, x, txtHeight);
                    }

                    chkFullscreen = new JFXCheckBox();
                    right.setRight(chkFullscreen);
                    chkFullscreen.setText(i18n("settings.game.fullscreen"));
                    chkFullscreen.setAlignment(Pos.CENTER);
                    BorderPane.setAlignment(chkFullscreen, Pos.CENTER);
                    BorderPane.setMargin(chkFullscreen, new Insets(0, 0, 0, 7));
                }
            }

            BorderPane showLogsPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.show_log"));
                showLogsPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkShowLogs = new JFXToggleButton();
                showLogsPane.setRight(chkShowLogs);
                chkShowLogs.setSize(8);
                FXUtils.setLimitHeight(chkShowLogs, 20);
            }

            BorderPane processPriorityPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.process_priority"));
                processPriorityPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                cboProcessPriority = new JFXComboBox<>();
                processPriorityPane.setRight(cboProcessPriority);
                BorderPane.setAlignment(cboProcessPriority, Pos.CENTER_RIGHT);
                FXUtils.setLimitWidth(cboProcessPriority, 300);
            }

            GridPane serverPane = new GridPane();
            {
                ColumnConstraints title = new ColumnConstraints();
                ColumnConstraints value = new ColumnConstraints();
                value.setFillWidth(true);
                value.setHgrow(Priority.ALWAYS);
                value.setHalignment(HPos.RIGHT);
                serverPane.setHgap(16);
                serverPane.setVgap(8);
                serverPane.getColumnConstraints().setAll(title, value);

                txtServerIP = new JFXTextField();
                txtServerIP.setPromptText(i18n("settings.advanced.server_ip.prompt"));
                txtServerIP.getStyleClass().add("fit-width");
                FXUtils.setLimitWidth(txtServerIP, 300);
                serverPane.addRow(0, new Label(i18n("settings.advanced.server_ip")), txtServerIP);
            }

            componentList.getContent().setAll(javaItem, gameDirItem, maxMemoryPane, launcherVisibilityPane, dimensionPane, showLogsPane, processPriorityPane, serverPane);
        }

        HBox advancedHintPane = new HBox();
        advancedHintPane.setAlignment(Pos.CENTER);
        advancedHintPane.setPadding(new Insets(16, 0, 8, 0));
        {
            Label advanced = new Label(i18n("settings.advanced"));
            advanced.setStyle("-fx-font-size: 12px;");
            advancedHintPane.getChildren().setAll(advanced);
        }

        ComponentList customCommandsPane = new ComponentList();
        customCommandsPane.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        {
            GridPane pane = new GridPane();
            ColumnConstraints title = new ColumnConstraints();
            ColumnConstraints value = new ColumnConstraints();
            value.setFillWidth(true);
            value.setHgrow(Priority.ALWAYS);
            pane.setHgap(16);
            pane.setVgap(8);
            pane.getColumnConstraints().setAll(title, value);

            txtGameArgs = new JFXTextField();
            txtGameArgs.setPromptText(i18n("settings.advanced.minecraft_arguments.prompt"));
            txtGameArgs.getStyleClass().add("fit-width");
            pane.addRow(0, new Label(i18n("settings.advanced.minecraft_arguments")), txtGameArgs);

            txtPrecallingCommand = new JFXTextField();
            txtPrecallingCommand.setPromptText(i18n("settings.advanced.precall_command.prompt"));
            txtPrecallingCommand.getStyleClass().add("fit-width");
            pane.addRow(1, new Label(i18n("settings.advanced.precall_command")), txtPrecallingCommand);

            txtWrapper = new JFXTextField();
            txtWrapper.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
            txtWrapper.getStyleClass().add("fit-width");
            pane.addRow(2, new Label(i18n("settings.advanced.wrapper_launcher")), txtWrapper);

            customCommandsPane.getContent().setAll(pane);
        }

        ComponentList jvmPane = new ComponentList();
        jvmPane.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        {
            GridPane pane = new GridPane();
            ColumnConstraints title = new ColumnConstraints();
            ColumnConstraints value = new ColumnConstraints();
            value.setFillWidth(true);
            value.setHgrow(Priority.ALWAYS);
            pane.setHgap(16);
            pane.setVgap(8);
            pane.getColumnConstraints().setAll(title, value);

            txtJVMArgs = new JFXTextField();
            txtJVMArgs.setPromptText(i18n("settings.advanced.jvm_args.prompt"));
            txtJVMArgs.getStyleClass().add("fit-width");
            pane.addRow(0, new Label(i18n("settings.advanced.jvm_args")), txtJVMArgs);

            txtMetaspace = new JFXTextField();
            txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
            txtMetaspace.getStyleClass().add("fit-width");
            FXUtils.setValidateWhileTextChanged(txtMetaspace, true);
            txtMetaspace.setValidators(new NumberValidator(i18n("input.number"), true));
            pane.addRow(1, new Label(i18n("settings.advanced.java_permanent_generation_space")), txtMetaspace);

            jvmPane.getContent().setAll(pane);
        }

        ComponentList workaroundPane = new ComponentList();
        workaroundPane.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
        {
            nativesDirItem = new MultiFileItem<>(true);
            nativesDirItem.setTitle(i18n("settings.advanced.natives_directory"));
            nativesDirItem.setChooserTitle(i18n("settings.advanced.natives_directory.choose"));
            nativesDirItem.setHasSubtitle(true);
            nativesDirItem.setCustomText(i18n("settings.custom"));
            nativesDirItem.setDirectory(true);

            BorderPane noJVMArgsPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.no_jvm_args"));
                noJVMArgsPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkNoJVMArgs = new JFXToggleButton();
                noJVMArgsPane.setRight(chkNoJVMArgs);
                chkNoJVMArgs.setSize(8);
                FXUtils.setLimitHeight(chkNoJVMArgs, 20);
            }

            BorderPane noGameCheckPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.dont_check_game_completeness"));
                noGameCheckPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkNoGameCheck = new JFXToggleButton();
                noGameCheckPane.setRight(chkNoGameCheck);
                chkNoGameCheck.setSize(8);
                FXUtils.setLimitHeight(chkNoGameCheck, 20);
            }

            BorderPane noJVMCheckPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.dont_check_jvm_validity"));
                noJVMCheckPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkNoJVMCheck = new JFXToggleButton();
                noJVMCheckPane.setRight(chkNoJVMCheck);
                chkNoJVMCheck.setSize(8);
                FXUtils.setLimitHeight(chkNoJVMCheck, 20);
            }

            BorderPane useNativeGLFWPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.use_native_glfw"));
                useNativeGLFWPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkUseNativeGLFW = new JFXToggleButton();
                useNativeGLFWPane.setRight(chkUseNativeGLFW);
                chkUseNativeGLFW.setSize(8);
                FXUtils.setLimitHeight(chkUseNativeGLFW, 20);
            }

            BorderPane useNativeOpenALPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced.use_native_openal"));
                useNativeOpenALPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                chkUseNativeOpenAL = new JFXToggleButton();
                useNativeOpenALPane.setRight(chkUseNativeOpenAL);
                chkUseNativeOpenAL.setSize(8);
                FXUtils.setLimitHeight(chkUseNativeOpenAL, 20);
            }

            workaroundPane.getContent().setAll(nativesDirItem, noJVMArgsPane, noGameCheckPane, noJVMCheckPane, useNativeGLFWPane, useNativeOpenALPane);
        }

        rootPane.getChildren().setAll(iconPickerItemWrapper, settingsTypePane, componentList,
                advancedHintPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.custom_commands")), customCommandsPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.jvm")), jvmPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.workaround")), workaroundPane);

        initialize();

        specificSettingsListener = any -> {
            chkEnableSpecificSettings.setSelected(!lastVersionSetting.isUsesGlobal());
        };

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onDecoratorPageNavigating);

        cboLauncherVisibility.getItems().setAll(LauncherVisibility.values());
        cboLauncherVisibility.setConverter(stringConverter(e -> i18n("settings.advanced.launcher_visibility." + e.name().toLowerCase())));

        cboProcessPriority.getItems().setAll(ProcessPriority.values());
        cboProcessPriority.setConverter(stringConverter(e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase())));
    }

    private void initialize() {
        memoryStatusProperty.set(OperatingSystem.getPhysicalMemoryStatus().orElse(OperatingSystem.PhysicalMemoryStatus.INVALID));

        Task.supplyAsync(JavaVersion::getJavas).thenAcceptAsync(Schedulers.javafx(), list -> {
            javaItem.loadChildren(list.stream()
                    .map(javaVersion -> javaItem.createChildren(javaVersion.getVersion() + i18n("settings.game.java_directory.bit",
                            javaVersion.getBits().getBit()), javaVersion.getBinary().toString(), javaVersion))
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
                gameDirItem.createChildren(i18n("settings.advanced.game_dir.default"), GameDirectoryType.ROOT_FOLDER),
                gameDirItem.createChildren(i18n("settings.advanced.game_dir.independent"), GameDirectoryType.VERSION_FOLDER)
        ));

        nativesDirItem.setCustomUserData(NativesDirectoryType.CUSTOM);
        nativesDirItem.loadChildren(Arrays.asList(
                nativesDirItem.createChildren(i18n("settings.advanced.natives_directory.default"), NativesDirectoryType.VERSION_FOLDER)
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

        componentList.disableProperty().bind(chkEnableSpecificSettings.selectedProperty().not());
    }

    @Override
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

        // unbind data fields
        if (lastVersionSetting != null) {
            FXUtils.unbindInt(txtWidth, lastVersionSetting.widthProperty());
            FXUtils.unbindInt(txtHeight, lastVersionSetting.heightProperty());
            maxMemoryProperty.unbindBidirectional(lastVersionSetting.maxMemoryProperty());
            FXUtils.unbindString(javaItem.getTxtCustom(), lastVersionSetting.javaDirProperty());
            FXUtils.unbindString(gameDirItem.getTxtCustom(), lastVersionSetting.gameDirProperty());
            FXUtils.unbindString(nativesDirItem.getTxtCustom(), lastVersionSetting.nativesDirProperty());
            FXUtils.unbindString(txtJVMArgs, lastVersionSetting.javaArgsProperty());
            FXUtils.unbindString(txtGameArgs, lastVersionSetting.minecraftArgsProperty());
            FXUtils.unbindString(txtMetaspace, lastVersionSetting.permSizeProperty());
            FXUtils.unbindString(txtWrapper, lastVersionSetting.wrapperProperty());
            FXUtils.unbindString(txtPrecallingCommand, lastVersionSetting.preLaunchCommandProperty());
            FXUtils.unbindString(txtServerIP, lastVersionSetting.serverIpProperty());
            FXUtils.unbindBoolean(chkAutoAllocate, lastVersionSetting.autoMemoryProperty());
            FXUtils.unbindBoolean(chkFullscreen, lastVersionSetting.fullscreenProperty());
            FXUtils.unbindBoolean(chkNoGameCheck, lastVersionSetting.notCheckGameProperty());
            FXUtils.unbindBoolean(chkNoJVMCheck, lastVersionSetting.notCheckJVMProperty());
            FXUtils.unbindBoolean(chkNoJVMArgs, lastVersionSetting.noJVMArgsProperty());
            FXUtils.unbindBoolean(chkShowLogs, lastVersionSetting.showLogsProperty());
            FXUtils.unbindBoolean(chkUseNativeGLFW, lastVersionSetting.useNativeGLFWProperty());
            FXUtils.unbindBoolean(chkUseNativeOpenAL, lastVersionSetting.useNativeOpenALProperty());
            FXUtils.unbindEnum(cboLauncherVisibility);
            FXUtils.unbindEnum(cboProcessPriority);

            lastVersionSetting.usesGlobalProperty().removeListener(specificSettingsListener);
            lastVersionSetting.javaDirProperty().removeListener(javaListener);
            lastVersionSetting.javaProperty().removeListener(javaListener);

            gameDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.gameDirTypeProperty());
            gameDirItem.subtitleProperty().unbind();

            nativesDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.nativesDirTypeProperty());
            nativesDirItem.subtitleProperty().unbind();
        }

        // unbind data fields
        javaItem.setToggleSelectedListener(null);

        // bind new data fields
        FXUtils.bindInt(txtWidth, versionSetting.widthProperty());
        FXUtils.bindInt(txtHeight, versionSetting.heightProperty());
        maxMemoryProperty.bindBidirectional(versionSetting.maxMemoryProperty());
        FXUtils.bindString(javaItem.getTxtCustom(), versionSetting.javaDirProperty());
        FXUtils.bindString(gameDirItem.getTxtCustom(), versionSetting.gameDirProperty());
        FXUtils.bindString(nativesDirItem.getTxtCustom(), versionSetting.nativesDirProperty());
        FXUtils.bindString(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.bindString(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.bindString(txtPrecallingCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.bindString(txtServerIP, versionSetting.serverIpProperty());
        FXUtils.bindBoolean(chkAutoAllocate, versionSetting.autoMemoryProperty());
        FXUtils.bindBoolean(chkFullscreen, versionSetting.fullscreenProperty());
        FXUtils.bindBoolean(chkNoGameCheck, versionSetting.notCheckGameProperty());
        FXUtils.bindBoolean(chkNoJVMCheck, versionSetting.notCheckJVMProperty());
        FXUtils.bindBoolean(chkNoJVMArgs, versionSetting.noJVMArgsProperty());
        FXUtils.bindBoolean(chkShowLogs, versionSetting.showLogsProperty());
        FXUtils.bindBoolean(chkUseNativeGLFW, versionSetting.useNativeGLFWProperty());
        FXUtils.bindBoolean(chkUseNativeOpenAL, versionSetting.useNativeOpenALProperty());
        FXUtils.bindEnum(cboLauncherVisibility, versionSetting.launcherVisibilityProperty());
        FXUtils.bindEnum(cboProcessPriority, versionSetting.processPriorityProperty());

        versionSetting.usesGlobalProperty().addListener(specificSettingsListener);
        if (versionId != null)
            chkEnableSpecificSettings.setSelected(!versionSetting.isUsesGlobal());

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaItem.isCustomToggle(newValue)) {
                versionSetting.setUsesCustomJavaDir();
            } else {
                versionSetting.setJavaVersion((JavaVersion) newValue.getUserData());
            }
        });

        versionSetting.javaDirProperty().addListener(javaListener);
        versionSetting.javaProperty().addListener(javaListener);

        gameDirItem.selectedDataProperty().bindBidirectional(versionSetting.gameDirTypeProperty());
        gameDirItem.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath()).normalize().toString(),
                versionSetting.gameDirProperty(), versionSetting.gameDirTypeProperty()));
        
        nativesDirItem.selectedDataProperty().bindBidirectional(versionSetting.nativesDirTypeProperty());
        nativesDirItem.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath() + "/natives").normalize().toString(),
                versionSetting.nativesDirProperty(), versionSetting.nativesDirTypeProperty()));

        lastVersionSetting = versionSetting;

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

    private void editGlobalSettings() {
        Versions.modifyGlobalSettings(profile);
    }

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
