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
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.newImage;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionSettingsPage extends StackPane implements DecoratorPage, VersionPage.VersionLoadable, PageAware {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State("", null, false, false, false));

    private final boolean globalSetting;

    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private WeakListenerHolder listenerHolder;
    private String versionId;
    private boolean javaItemsLoaded;

    private final VBox rootPane;
    private final JFXTextField txtWidth;
    private final JFXTextField txtHeight;
    private final JFXTextField txtJVMArgs;
    private final JFXTextField txtGameArgs;
    private final JFXTextField txtMetaspace;
    private final JFXTextField txtWrapper;
    private final JFXTextField txtPreLaunchCommand;
    private final JFXTextField txtPostExitCommand;
    private final JFXTextField txtServerIP;
    private final ComponentList componentList;
    private final JFXComboBox<LauncherVisibility> cboLauncherVisibility;
    private final JFXCheckBox chkAutoAllocate;
    private final JFXCheckBox chkFullscreen;
    private final OptionToggleButton noJVMArgsPane;
    private final OptionToggleButton noGameCheckPane;
    private final OptionToggleButton noJVMCheckPane;
    private final OptionToggleButton useNativeGLFWPane;
    private final OptionToggleButton useNativeOpenALPane;
    private final ComponentSublist javaSublist;
    private final MultiFileItem<JavaVersion> javaItem;
    private final MultiFileItem.Option<JavaVersion> javaAutoDeterminedOption;
    private final MultiFileItem.FileOption<JavaVersion> javaCustomOption;
    private final ComponentSublist gameDirSublist;
    private final MultiFileItem<GameDirectoryType> gameDirItem;
    private final MultiFileItem.FileOption<GameDirectoryType> gameDirCustomOption;
    private final ComponentSublist nativesDirSublist;
    private final MultiFileItem<NativesDirectoryType> nativesDirItem;
    private final MultiFileItem.FileOption<NativesDirectoryType> nativesDirCustomOption;
    private final JFXComboBox<ProcessPriority> cboProcessPriority;
    private final OptionToggleButton showLogsPane;
    private ImagePickerItem iconPickerItem;

    private final InvalidationListener specificSettingsListener;

    private final InvalidationListener javaListener = any -> initJavaSubtitle();

    private boolean uiVisible = false;
    private final StringProperty selectedVersion = new SimpleStringProperty();
    private final BooleanProperty navigateToSpecificSettings = new SimpleBooleanProperty(false);
    private final BooleanProperty enableSpecificSettings = new SimpleBooleanProperty(true);
    private final IntegerProperty maxMemory = new SimpleIntegerProperty();
    private final ObjectProperty<OperatingSystem.PhysicalMemoryStatus> memoryStatus = new SimpleObjectProperty<>(OperatingSystem.PhysicalMemoryStatus.INVALID);
    private final BooleanProperty modpack = new SimpleBooleanProperty();

    public VersionSettingsPage(boolean globalSetting) {
        this.globalSetting = globalSetting;

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        getChildren().setAll(scrollPane);

        rootPane = new VBox();
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");

        if (globalSetting) {
            HintPane skinHint = new HintPane(MessageDialogPane.MessageType.INFO);
            skinHint.setText(i18n("settings.skin"));
            rootPane.getChildren().add(skinHint);

            HintPane specificSettingsHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            Text text = new Text();
            text.textProperty().bind(BindingMapping.of(selectedVersion)
                    .map(selectedVersion -> i18n("settings.type.special.edit.hint", selectedVersion)));

            JFXHyperlink specificSettingsLink = new JFXHyperlink();
            specificSettingsLink.setText(i18n("settings.type.special.edit"));
            specificSettingsLink.setOnMouseClicked(e -> editSpecificSettings());

            specificSettingsHint.setChildren(text, specificSettingsLink);
            specificSettingsHint.managedProperty().bind(navigateToSpecificSettings);
            specificSettingsHint.visibleProperty().bind(navigateToSpecificSettings);

            rootPane.getChildren().addAll(specificSettingsHint);
        }

        if (!globalSetting) {
            ComponentList iconPickerItemWrapper = new ComponentList();
            rootPane.getChildren().add(iconPickerItemWrapper);

            iconPickerItem = new ImagePickerItem();
            iconPickerItem.setImage(new Image("/assets/img/icon.png"));
            iconPickerItem.setTitle(i18n("settings.icon"));
            iconPickerItem.setOnSelectButtonClicked(e -> onExploreIcon());
            iconPickerItem.setOnDeleteButtonClicked(e -> onDeleteIcon());
            iconPickerItemWrapper.getContent().setAll(iconPickerItem);


            BorderPane settingsTypePane = new BorderPane();
            settingsTypePane.disableProperty().bind(modpack);
            rootPane.getChildren().add(settingsTypePane);

            JFXCheckBox enableSpecificCheckBox = new JFXCheckBox();
            enableSpecificCheckBox.selectedProperty().bindBidirectional(enableSpecificSettings);
            settingsTypePane.setLeft(enableSpecificCheckBox);
            enableSpecificCheckBox.setText(i18n("settings.type.special.enable"));
            BorderPane.setAlignment(enableSpecificCheckBox, Pos.CENTER_RIGHT);

            JFXButton editGlobalSettingsButton = new JFXButton();
            settingsTypePane.setRight(editGlobalSettingsButton);
            editGlobalSettingsButton.setText(i18n("settings.type.global.edit"));
            editGlobalSettingsButton.getStyleClass().add("jfx-button-raised");
            editGlobalSettingsButton.setButtonType(JFXButton.ButtonType.RAISED);
            editGlobalSettingsButton.disableProperty().bind(enableSpecificCheckBox.selectedProperty());
            BorderPane.setAlignment(editGlobalSettingsButton, Pos.CENTER_RIGHT);
            editGlobalSettingsButton.setOnMouseClicked(e -> editGlobalSettings());
        }

        {
            componentList = new ComponentList();
            componentList.setDepth(1);

            javaItem = new MultiFileItem<>();
            javaSublist = new ComponentSublist();
            javaSublist.getContent().add(javaItem);
            javaSublist.setTitle(i18n("settings.game.java_directory"));
            javaSublist.setHasSubtitle(true);
            javaAutoDeterminedOption = new MultiFileItem.Option<>(i18n("settings.game.java_directory.auto"), null);
            javaCustomOption = new MultiFileItem.FileOption<JavaVersion>(i18n("settings.custom"), null)
                    .setChooserTitle(i18n("settings.game.java_directory.choose"));

            gameDirItem = new MultiFileItem<>();
            gameDirSublist = new ComponentSublist();
            gameDirSublist.getContent().add(gameDirItem);
            gameDirSublist.setTitle(i18n("settings.game.working_directory"));
            gameDirSublist.setHasSubtitle(true);
            gameDirItem.disableProperty().bind(modpack);
            gameDirCustomOption = new MultiFileItem.FileOption<>(i18n("settings.custom"), GameDirectoryType.CUSTOM)
                            .setChooserTitle(i18n("settings.game.working_directory.choose"))
                            .setDirectory(true);

            gameDirItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("settings.advanced.game_dir.default"), GameDirectoryType.ROOT_FOLDER),
                    new MultiFileItem.Option<>(i18n("settings.advanced.game_dir.independent"), GameDirectoryType.VERSION_FOLDER),
                    gameDirCustomOption
            ));

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
                    FXUtils.onChangeAndOperate(maxMemory, maxMemory -> {
                        changedByTextField.set(true);
                        slider.setValue(maxMemory.intValue() * 1.0 / OperatingSystem.TOTAL_MEMORY);
                        changedByTextField.set(false);
                    });
                    slider.valueProperty().addListener((value, oldVal, newVal) -> {
                        if (changedByTextField.get()) return;
                        maxMemory.set((int) (value.getValue().doubleValue() * OperatingSystem.TOTAL_MEMORY));
                    });

                    JFXTextField txtMaxMemory = new JFXTextField();
                    FXUtils.setLimitWidth(txtMaxMemory, 60);
                    FXUtils.setValidateWhileTextChanged(txtMaxMemory, true);
                    txtMaxMemory.textProperty().bindBidirectional(maxMemory, SafeStringConverter.fromInteger());
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
                                            (memoryStatus.get().getUsed() * 1.0 / memoryStatus.get().getTotal()), progressBarPane.widthProperty(),
                            memoryStatus));
                    StackPane allocateMemory = new StackPane();
                    allocateMemory.getStyleClass().add("memory-allocate");
                    allocateMemory.maxWidthProperty().bind(Bindings.createDoubleBinding(() ->
                                    progressBarPane.getWidth() *
                                            Math.min(1.0,
                                                    (double) (HMCLGameRepository.getAllocatedMemory(maxMemory.get() * 1024L * 1024L, memoryStatus.get().getAvailable(), chkAutoAllocate.isSelected())
                                                            + memoryStatus.get().getUsed()) / memoryStatus.get().getTotal()), progressBarPane.widthProperty(),
                            maxMemory, memoryStatus, chkAutoAllocate.selectedProperty()));

                    progressBarPane.getChildren().setAll(allocateMemory, usedMemory);
                }

                BorderPane digitalPane = new BorderPane();
                VBox.setMargin(digitalPane, new Insets(0, 0, 0, 16));
                {
                    Label lblPhysicalMemory = new Label();
                    lblPhysicalMemory.getStyleClass().add("memory-label");
                    digitalPane.setLeft(lblPhysicalMemory);
                    lblPhysicalMemory.textProperty().bind(Bindings.createStringBinding(() -> {
                        return i18n("settings.memory.used_per_total", memoryStatus.get().getUsedGB(), memoryStatus.get().getTotalGB());
                    }, memoryStatus));

                    Label lblAllocateMemory = new Label();
                    lblAllocateMemory.textProperty().bind(Bindings.createStringBinding(() -> {
                        long maxMemory = Lang.parseInt(this.maxMemory.get(), 0) * 1024L * 1024L;
                        return i18n(memoryStatus.get().hasAvailable() && maxMemory > memoryStatus.get().getAvailable()
                                        ? (chkAutoAllocate.isSelected() ? "settings.memory.allocate.auto.exceeded" : "settings.memory.allocate.manual.exceeded")
                                        : (chkAutoAllocate.isSelected() ? "settings.memory.allocate.auto" : "settings.memory.allocate.manual"),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(maxMemory),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(HMCLGameRepository.getAllocatedMemory(maxMemory, memoryStatus.get().getAvailable(), chkAutoAllocate.isSelected())),
                                OperatingSystem.PhysicalMemoryStatus.toGigaBytes(memoryStatus.get().getAvailable()));
                    }, memoryStatus, maxMemory, chkAutoAllocate.selectedProperty()));
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

            showLogsPane = new OptionToggleButton();
            showLogsPane.setTitle(i18n("settings.show_log"));

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

            componentList.getContent().setAll(javaSublist, gameDirSublist, maxMemoryPane, launcherVisibilityPane, dimensionPane, showLogsPane, processPriorityPane, serverPane);
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
        customCommandsPane.disableProperty().bind(enableSpecificSettings.not());
        {
            GridPane pane = new GridPane();
            pane.setHgap(16);
            pane.setVgap(8);
            pane.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());

            txtGameArgs = new JFXTextField();
            txtGameArgs.setPromptText(i18n("settings.advanced.minecraft_arguments.prompt"));
            txtGameArgs.getStyleClass().add("fit-width");
            pane.addRow(0, new Label(i18n("settings.advanced.minecraft_arguments")), txtGameArgs);

            txtPreLaunchCommand = new JFXTextField();
            txtPreLaunchCommand.setPromptText(i18n("settings.advanced.precall_command.prompt"));
            txtPreLaunchCommand.getStyleClass().add("fit-width");
            pane.addRow(1, new Label(i18n("settings.advanced.precall_command")), txtPreLaunchCommand);

            txtWrapper = new JFXTextField();
            txtWrapper.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
            txtWrapper.getStyleClass().add("fit-width");
            pane.addRow(2, new Label(i18n("settings.advanced.wrapper_launcher")), txtWrapper);

            txtPostExitCommand = new JFXTextField();
            txtPostExitCommand.setPromptText(i18n("settings.advanced.post_exit_command.prompt"));
            txtPostExitCommand.getStyleClass().add("fit-width");
            pane.addRow(3, new Label(i18n("settings.advanced.post_exit_command")), txtPostExitCommand);

            HintPane hintPane = new HintPane();
            hintPane.setText(i18n("settings.advanced.custom_commands.hint"));
            GridPane.setColumnSpan(hintPane, 2);
            pane.addRow(4, hintPane);

            customCommandsPane.getContent().setAll(pane);
        }

        ComponentList jvmPane = new ComponentList();
        jvmPane.disableProperty().bind(enableSpecificSettings.not());
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
        workaroundPane.disableProperty().bind(enableSpecificSettings.not());

        HintPane workaroundWarning = new HintPane(MessageDialogPane.MessageType.WARNING);
        workaroundWarning.setText(i18n("settings.advanced.workaround.warning"));

        {
            nativesDirItem = new MultiFileItem<>();
            nativesDirSublist = new ComponentSublist();
            nativesDirSublist.getContent().add(nativesDirItem);
            nativesDirSublist.setTitle(i18n("settings.advanced.natives_directory"));
            nativesDirSublist.setHasSubtitle(true);
            nativesDirCustomOption = new MultiFileItem.FileOption<>(i18n("settings.advanced.natives_directory.custom"), NativesDirectoryType.CUSTOM)
                    .setChooserTitle(i18n("settings.advanced.natives_directory.choose"))
                    .setDirectory(true);
            nativesDirItem.loadChildren(Arrays.asList(
                    new MultiFileItem.Option<>(i18n("settings.advanced.natives_directory.default"), NativesDirectoryType.VERSION_FOLDER),
                    nativesDirCustomOption
            ));
            HintPane nativesDirHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            nativesDirHint.setText(i18n("settings.advanced.natives_directory.hint"));
            nativesDirItem.getChildren().add(nativesDirHint);

            noJVMArgsPane = new OptionToggleButton();
            noJVMArgsPane.setTitle(i18n("settings.advanced.no_jvm_args"));

            noGameCheckPane = new OptionToggleButton();
            noGameCheckPane.setTitle(i18n("settings.advanced.dont_check_game_completeness"));

            noJVMCheckPane = new OptionToggleButton();
            noJVMCheckPane.setTitle(i18n("settings.advanced.dont_check_jvm_validity"));

            useNativeGLFWPane = new OptionToggleButton();
            useNativeGLFWPane.setTitle(i18n("settings.advanced.use_native_glfw"));

            useNativeOpenALPane = new OptionToggleButton();
            useNativeOpenALPane.setTitle(i18n("settings.advanced.use_native_openal"));

            workaroundPane.getContent().setAll(nativesDirSublist, noJVMArgsPane, noGameCheckPane, noJVMCheckPane, useNativeGLFWPane, useNativeOpenALPane);
        }

        rootPane.getChildren().addAll(componentList,
                advancedHintPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.custom_commands")), customCommandsPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.jvm")), jvmPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.workaround")), workaroundWarning, workaroundPane);

        initialize();

        specificSettingsListener = any -> {
            enableSpecificSettings.set(!lastVersionSetting.isUsesGlobal());
        };

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onDecoratorPageNavigating);

        cboLauncherVisibility.getItems().setAll(LauncherVisibility.values());
        cboLauncherVisibility.setConverter(stringConverter(e -> i18n("settings.advanced.launcher_visibility." + e.name().toLowerCase())));

        cboProcessPriority.getItems().setAll(ProcessPriority.values());
        cboProcessPriority.setConverter(stringConverter(e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase())));
    }

    private void initialize() {
        memoryStatus.set(OperatingSystem.getPhysicalMemoryStatus().orElse(OperatingSystem.PhysicalMemoryStatus.INVALID));

        Task.supplyAsync(JavaVersion::getJavas).thenAcceptAsync(Schedulers.javafx(), list -> {
            boolean isX86 = (Architecture.SYSTEM_ARCH.isX86())
                    && list.stream().allMatch(java -> java.getArchitecture().isX86());

            // boolean showSystem = list.stream().anyMatch(java -> java.getPlatform().getOperatingSystem() != OperatingSystem.CURRENT_OS);

            List<MultiFileItem.Option<JavaVersion>> options = list.stream()
                    .map(javaVersion -> new MultiFileItem.Option<>(
                             i18n("settings.game.java_directory.template",
                                     javaVersion.getVersion(),
                                     isX86 ? i18n("settings.game.java_directory.bit",javaVersion.getBits().getBit())
                                             : javaVersion.getPlatform().getArchitecture().getDisplayName()), javaVersion)
                            .setSubtitle(javaVersion.getBinary().toString()))
                    .collect(Collectors.toList());
            options.add(0, javaAutoDeterminedOption);
            options.add(javaCustomOption);
            javaItem.loadChildren(options);
            javaItemsLoaded = true;
            initializeSelectedJava();
        }).start();

        javaItem.setSelectedData(null);
        javaItem.setFallbackData(JavaVersion.fromCurrentEnvironment());
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            javaCustomOption.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java", "java.exe", "javaw.exe"));

        enableSpecificSettings.addListener((a, b, newValue) -> {
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

        componentList.disableProperty().bind(enableSpecificSettings.not());
    }

    @Override
    public void loadVersion(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;
        this.listenerHolder = new WeakListenerHolder();

        if (versionId == null) {
            enableSpecificSettings.set(true);
            state.set(State.fromTitle(Profiles.getProfileDisplayName(profile) + " - " + i18n("settings.type.global.manage")));

            listenerHolder.add(FXUtils.onWeakChangeAndOperate(profile.selectedVersionProperty(), selectedVersion -> {
                this.selectedVersion.setValue(selectedVersion);

                VersionSetting specializedVersionSetting = profile.getRepository().getLocalVersionSetting(selectedVersion);
                if (specializedVersionSetting != null) {
                    navigateToSpecificSettings.bind(specializedVersionSetting.usesGlobalProperty().not());
                } else {
                    navigateToSpecificSettings.unbind();
                    navigateToSpecificSettings.set(false);
                }
            }));
        } else {
            navigateToSpecificSettings.unbind();
            navigateToSpecificSettings.set(false);
        }

        VersionSetting versionSetting = profile.getVersionSetting(versionId);

        modpack.set(versionId != null && profile.getRepository().isModpack(versionId));

        // unbind data fields
        if (lastVersionSetting != null) {
            FXUtils.unbindInt(txtWidth, lastVersionSetting.widthProperty());
            FXUtils.unbindInt(txtHeight, lastVersionSetting.heightProperty());
            maxMemory.unbindBidirectional(lastVersionSetting.maxMemoryProperty());
            javaCustomOption.valueProperty().unbindBidirectional(lastVersionSetting.javaDirProperty());
            gameDirCustomOption.valueProperty().unbindBidirectional(lastVersionSetting.gameDirProperty());
            nativesDirCustomOption.valueProperty().unbindBidirectional(lastVersionSetting.nativesDirProperty());
            FXUtils.unbindString(txtJVMArgs, lastVersionSetting.javaArgsProperty());
            FXUtils.unbindString(txtGameArgs, lastVersionSetting.minecraftArgsProperty());
            FXUtils.unbindString(txtMetaspace, lastVersionSetting.permSizeProperty());
            FXUtils.unbindString(txtWrapper, lastVersionSetting.wrapperProperty());
            FXUtils.unbindString(txtPreLaunchCommand, lastVersionSetting.preLaunchCommandProperty());
            FXUtils.unbindString(txtPostExitCommand, lastVersionSetting.postExitCommandProperty());
            FXUtils.unbindString(txtServerIP, lastVersionSetting.serverIpProperty());
            FXUtils.unbindBoolean(chkAutoAllocate, lastVersionSetting.autoMemoryProperty());
            FXUtils.unbindBoolean(chkFullscreen, lastVersionSetting.fullscreenProperty());
            noGameCheckPane.selectedProperty().unbindBidirectional(lastVersionSetting.notCheckGameProperty());
            noJVMCheckPane.selectedProperty().unbindBidirectional(lastVersionSetting.notCheckJVMProperty());
            noJVMArgsPane.selectedProperty().unbindBidirectional(lastVersionSetting.noJVMArgsProperty());
            showLogsPane.selectedProperty().unbindBidirectional(lastVersionSetting.showLogsProperty());
            useNativeGLFWPane.selectedProperty().unbindBidirectional(lastVersionSetting.useNativeGLFWProperty());
            useNativeOpenALPane.selectedProperty().unbindBidirectional(lastVersionSetting.useNativeOpenALProperty());
            FXUtils.unbindEnum(cboLauncherVisibility);
            FXUtils.unbindEnum(cboProcessPriority);

            lastVersionSetting.usesGlobalProperty().removeListener(specificSettingsListener);
            lastVersionSetting.javaDirProperty().removeListener(javaListener);
            lastVersionSetting.javaProperty().removeListener(javaListener);

            gameDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.gameDirTypeProperty());
            gameDirSublist.subtitleProperty().unbind();

            nativesDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.nativesDirTypeProperty());
            nativesDirSublist.subtitleProperty().unbind();
        }

        // unbind data fields
        javaItem.setToggleSelectedListener(null);

        // bind new data fields
        FXUtils.bindInt(txtWidth, versionSetting.widthProperty());
        FXUtils.bindInt(txtHeight, versionSetting.heightProperty());
        maxMemory.bindBidirectional(versionSetting.maxMemoryProperty());

        javaCustomOption.bindBidirectional(versionSetting.javaDirProperty());
        gameDirCustomOption.bindBidirectional(versionSetting.gameDirProperty());
        nativesDirCustomOption.bindBidirectional(versionSetting.nativesDirProperty());
        FXUtils.bindString(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.bindString(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.bindString(txtPreLaunchCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.bindString(txtServerIP, versionSetting.serverIpProperty());
        FXUtils.bindBoolean(chkAutoAllocate, versionSetting.autoMemoryProperty());
        FXUtils.bindBoolean(chkFullscreen, versionSetting.fullscreenProperty());
        noGameCheckPane.selectedProperty().bindBidirectional(versionSetting.notCheckGameProperty());
        noJVMCheckPane.selectedProperty().bindBidirectional(versionSetting.notCheckJVMProperty());
        noJVMArgsPane.selectedProperty().bindBidirectional(versionSetting.noJVMArgsProperty());
        showLogsPane.selectedProperty().bindBidirectional(versionSetting.showLogsProperty());
        useNativeGLFWPane.selectedProperty().bindBidirectional(versionSetting.useNativeGLFWProperty());
        useNativeOpenALPane.selectedProperty().bindBidirectional(versionSetting.useNativeOpenALProperty());
        FXUtils.bindEnum(cboLauncherVisibility, versionSetting.launcherVisibilityProperty());
        FXUtils.bindEnum(cboProcessPriority, versionSetting.processPriorityProperty());

        versionSetting.usesGlobalProperty().addListener(specificSettingsListener);
        if (versionId != null)
            enableSpecificSettings.set(!versionSetting.isUsesGlobal());

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaCustomOption.isSelected()) {
                versionSetting.setUsesCustomJavaDir();
            } else if (javaAutoDeterminedOption.isSelected()) {
                versionSetting.setJavaAutoSelected();
            } else {
                versionSetting.setJavaVersion((JavaVersion) newValue.getUserData());
            }
        });

        versionSetting.javaDirProperty().addListener(javaListener);
        versionSetting.defaultJavaPathPropertyProperty().addListener(javaListener);
        versionSetting.javaProperty().addListener(javaListener);

        gameDirItem.selectedDataProperty().bindBidirectional(versionSetting.gameDirTypeProperty());
        gameDirSublist.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath()).normalize().toString(),
                versionSetting.gameDirProperty(), versionSetting.gameDirTypeProperty()));
        
        nativesDirItem.selectedDataProperty().bindBidirectional(versionSetting.nativesDirTypeProperty());
        nativesDirSublist.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath() + "/natives").normalize().toString(),
                versionSetting.nativesDirProperty(), versionSetting.nativesDirTypeProperty()));

        lastVersionSetting = versionSetting;

        initJavaSubtitle();

        loadIcon();
    }

    private void initializeSelectedJava() {
        if (lastVersionSetting == null
                || !javaItemsLoaded /* JREs are still being loaded */) {
            return;
        }

        if (lastVersionSetting.isUsesCustomJavaDir()) {
            javaCustomOption.setSelected(true);
        } else if (lastVersionSetting.isJavaAutoSelected()) {
            javaAutoDeterminedOption.setSelected(true);
        } else {
//            javaLoading.set(true);
            lastVersionSetting.getJavaVersion(null, null)
                    .thenAcceptAsync(Schedulers.javafx(), javaVersion -> {
                        javaItem.setSelectedData(javaVersion);
//                        javaLoading.set(false);
                    }).start();
        }
    }

    private void initJavaSubtitle() {
        FXUtils.checkFxUserThread();
        initializeSelectedJava();
        VersionSetting versionSetting = lastVersionSetting;
        if (versionSetting == null)
            return;
        Profile profile = this.profile;
        String versionId = this.versionId;
        boolean autoSelected = versionSetting.isJavaAutoSelected();

        if (autoSelected && versionId == null) {
            javaSublist.setSubtitle(i18n("settings.game.java_directory.auto"));
            return;
        }

        Task.composeAsync(Schedulers.javafx(), () -> {
            if (versionId == null) {
                return versionSetting.getJavaVersion(VersionNumber.asVersion("Unknown"), null);
            } else {
                return versionSetting.getJavaVersion(
                        VersionNumber.asVersion(GameVersion.minecraftVersion(profile.getRepository().getVersionJar(versionId)).orElse("Unknown")),
                        profile.getRepository().getVersion(versionId));
            }
        }).thenAcceptAsync(Schedulers.javafx(), javaVersion -> javaSublist.setSubtitle(Optional.ofNullable(javaVersion)
                .map(JavaVersion::getBinary).map(Path::toString).orElse("Invalid Java Path")))
                .start();
    }

    private void editSpecificSettings() {
        Versions.modifyGameSettings(profile, profile.getSelectedVersion());
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
