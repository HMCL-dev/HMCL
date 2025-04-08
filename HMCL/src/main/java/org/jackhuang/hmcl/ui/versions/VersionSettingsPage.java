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
import javafx.beans.value.ChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.javafx.PropertyUtils;
import org.jackhuang.hmcl.util.javafx.SafeStringConverter;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.Lang.getTimer;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionSettingsPage extends StackPane implements DecoratorPage, VersionPage.VersionLoadable, PageAware {

    private static final ObjectProperty<OperatingSystem.PhysicalMemoryStatus> memoryStatus = new SimpleObjectProperty<>(OperatingSystem.PhysicalMemoryStatus.INVALID);
    private static TimerTask memoryStatusUpdateTask;

    private static void initMemoryStatusUpdateTask() {
        FXUtils.checkFxUserThread();
        if (memoryStatusUpdateTask != null)
            return;
        memoryStatusUpdateTask = new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> memoryStatus.set(OperatingSystem.getPhysicalMemoryStatus()));
            }
        };
        getTimer().scheduleAtFixedRate(memoryStatusUpdateTask, 0, 1000);
    }

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(new State("", null, false, false, false));

    private AdvancedVersionSettingPage advancedVersionSettingPage;

    private VersionSetting lastVersionSetting = null;
    private Profile profile;
    private WeakListenerHolder listenerHolder;
    private String versionId;

    private final VBox rootPane;
    private final JFXTextField txtWidth;
    private final JFXTextField txtHeight;
    private final JFXTextField txtServerIP;
    private final ComponentList componentList;
    private final JFXComboBox<LauncherVisibility> cboLauncherVisibility;
    private final JFXCheckBox chkAutoAllocate;
    private final JFXCheckBox chkFullscreen;
    private final ComponentSublist javaSublist;
    private final MultiFileItem<Pair<JavaVersionType, JavaRuntime>> javaItem;
    private final MultiFileItem.Option<Pair<JavaVersionType, JavaRuntime>> javaAutoDeterminedOption;
    private final MultiFileItem.StringOption<Pair<JavaVersionType, JavaRuntime>> javaVersionOption;
    private final MultiFileItem.FileOption<Pair<JavaVersionType, JavaRuntime>> javaCustomOption;

    private final ComponentSublist gameDirSublist;
    private final MultiFileItem<GameDirectoryType> gameDirItem;
    private final MultiFileItem.FileOption<GameDirectoryType> gameDirCustomOption;
    private final JFXComboBox<ProcessPriority> cboProcessPriority;
    private final OptionToggleButton showLogsPane;
    private final ImagePickerItem iconPickerItem;

    private final ChangeListener<Collection<JavaRuntime>> javaListChangeListener;
    private final InvalidationListener usesGlobalListener;
    private final ChangeListener<Boolean> specificSettingsListener;
    private final InvalidationListener javaListener = any -> initJavaSubtitle();
    private boolean updatingJavaSetting = false;
    private boolean updatingSelectedJava = false;

    private final StringProperty selectedVersion = new SimpleStringProperty();
    private final BooleanProperty navigateToSpecificSettings = new SimpleBooleanProperty(false);
    private final BooleanProperty enableSpecificSettings = new SimpleBooleanProperty(false);
    private final IntegerProperty maxMemory = new SimpleIntegerProperty();
    private final BooleanProperty modpack = new SimpleBooleanProperty();

    public VersionSettingsPage(boolean globalSetting) {
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
            HintPane specificSettingsHint = new HintPane(MessageDialogPane.MessageType.WARNING);
            Text text = new Text();
            text.textProperty().bind(BindingMapping.of(selectedVersion)
                    .map(selectedVersion -> i18n("settings.type.special.edit.hint", selectedVersion)));

            JFXHyperlink specificSettingsLink = new JFXHyperlink(i18n("settings.type.special.edit"));
            specificSettingsLink.setOnAction(e -> editSpecificSettings());

            specificSettingsHint.setChildren(text, specificSettingsLink);
            specificSettingsHint.managedProperty().bind(navigateToSpecificSettings);
            specificSettingsHint.visibleProperty().bind(navigateToSpecificSettings);

            iconPickerItem = null;

            rootPane.getChildren().addAll(specificSettingsHint);
        } else {
            HintPane gameDirHint = new HintPane(MessageDialogPane.MessageType.INFO);
            gameDirHint.setText(i18n("settings.game.working_directory.hint"));
            rootPane.getChildren().add(gameDirHint);

            ComponentList iconPickerItemWrapper = new ComponentList();
            rootPane.getChildren().add(iconPickerItemWrapper);

            iconPickerItem = new ImagePickerItem();
            iconPickerItem.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
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

            JFXButton editGlobalSettingsButton = FXUtils.newRaisedButton(i18n("settings.type.global.edit"));
            settingsTypePane.setRight(editGlobalSettingsButton);
            editGlobalSettingsButton.disableProperty().bind(enableSpecificCheckBox.selectedProperty());
            BorderPane.setAlignment(editGlobalSettingsButton, Pos.CENTER_RIGHT);
            editGlobalSettingsButton.setOnAction(e -> editGlobalSettings());
        }

        {
            componentList = new ComponentList();
            componentList.setDepth(1);

            if (!globalSetting) {
                BorderPane copyGlobalPane = new BorderPane();
                {
                    Label label = new Label(i18n("settings.game.copy_global"));
                    copyGlobalPane.setLeft(label);
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                    JFXButton copyAll = new JFXButton(i18n("settings.game.copy_global.copy_all"));
                    copyAll.disableProperty().bind(modpack);
                    copyGlobalPane.setRight(copyAll);
                    copyAll.setOnAction(e -> Controllers.confirm(i18n("settings.game.copy_global.copy_all.confirm"), null, () -> {
                        Set<String> ignored = new HashSet<>(Arrays.asList(
                                "usesGlobal",
                                "versionIcon"
                        ));

                        PropertyUtils.copyProperties(profile.getGlobal(), lastVersionSetting, name -> !ignored.contains(name));
                    }, null));
                    copyAll.getStyleClass().add("jfx-button-border");
                    BorderPane.setAlignment(copyAll, Pos.CENTER_RIGHT);
                }

                componentList.getContent().add(copyGlobalPane);
            }

            javaItem = new MultiFileItem<>();
            javaSublist = new ComponentSublist();
            javaSublist.getContent().add(javaItem);
            javaSublist.setTitle(i18n("settings.game.java_directory"));
            javaSublist.setHasSubtitle(true);
            javaAutoDeterminedOption = new MultiFileItem.Option<>(i18n("settings.game.java_directory.auto"), pair(JavaVersionType.AUTO, null));
            javaVersionOption = new MultiFileItem.StringOption<>(i18n("settings.game.java_directory.version"), pair(JavaVersionType.VERSION, null));
            javaVersionOption.setValidators(new NumberValidator(true));
            FXUtils.setLimitWidth(javaVersionOption.getCustomField(), 40);
            javaCustomOption = new MultiFileItem.FileOption<Pair<JavaVersionType, JavaRuntime>>(i18n("settings.custom"), pair(JavaVersionType.CUSTOM, null))
                    .setChooserTitle(i18n("settings.game.java_directory.choose"));
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                javaCustomOption.addExtensionFilter(new FileChooser.ExtensionFilter("Java", "java.exe"));

            javaListChangeListener = FXUtils.onWeakChangeAndOperate(JavaManager.getAllJavaProperty(), allJava -> {
                List<MultiFileItem.Option<Pair<JavaVersionType, JavaRuntime>>> options = new ArrayList<>();
                options.add(javaAutoDeterminedOption);
                options.add(javaVersionOption);
                if (allJava != null) {
                    boolean isX86 = Architecture.SYSTEM_ARCH.isX86() && allJava.stream().allMatch(java -> java.getArchitecture().isX86());

                    for (JavaRuntime java : allJava) {
                        options.add(new MultiFileItem.Option<>(
                                i18n("settings.game.java_directory.template",
                                        java.getVersion(),
                                        isX86 ? i18n("settings.game.java_directory.bit", java.getBits().getBit())
                                                : java.getPlatform().getArchitecture().getDisplayName()),
                                pair(JavaVersionType.DETECTED, java))
                                .setSubtitle(java.getBinary().toString()));
                    }
                }

                options.add(javaCustomOption);
                javaItem.loadChildren(options);
                initializeSelectedJava();
            });

            gameDirItem = new MultiFileItem<>();
            gameDirSublist = new ComponentSublist();
            gameDirSublist.getContent().add(gameDirItem);
            gameDirSublist.setTitle(i18n("settings.game.working_directory"));
            gameDirSublist.setHasSubtitle(versionId != null);
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
                lowerBoundPane.setStyle("-fx-view-order: -1;"); // prevent the indicator from being covered by the progress bar
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
                FXUtils.setLimitWidth(txtServerIP, 300);
                serverPane.addRow(0, new Label(i18n("settings.advanced.server_ip")), txtServerIP);
            }

            BorderPane showAdvancedSettingPane = new BorderPane();
            {
                Label label = new Label(i18n("settings.advanced"));
                showAdvancedSettingPane.setLeft(label);
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);

                JFXButton button = new JFXButton(i18n("settings.advanced.modify"));
                button.setOnAction(e -> {
                    if (lastVersionSetting != null) {
                        if (advancedVersionSettingPage == null)
                            advancedVersionSettingPage = new AdvancedVersionSettingPage(profile, versionId, lastVersionSetting);

                        Controllers.navigate(advancedVersionSettingPage);
                    }
                });
                button.getStyleClass().add("jfx-button-border");
                showAdvancedSettingPane.setRight(button);
            }

            componentList.getContent().addAll(
                    javaSublist,
                    gameDirSublist,
                    maxMemoryPane,
                    launcherVisibilityPane,
                    dimensionPane,
                    showLogsPane,
                    processPriorityPane,
                    serverPane,
                    showAdvancedSettingPane
            );
        }

        rootPane.getChildren().add(componentList);

        usesGlobalListener = any -> enableSpecificSettings.set(!lastVersionSetting.isUsesGlobal());
        specificSettingsListener = (a, b, newValue) -> {
            if (versionId == null) return;

            // do not call versionSettings.setUsesGlobal(true/false)
            // because versionSettings can be the global one.
            // global versionSettings.usesGlobal is always true.
            if (newValue)
                profile.getRepository().specializeVersionSetting(versionId);
            else
                profile.getRepository().globalizeVersionSetting(versionId);

            Platform.runLater(() -> loadVersion(profile, versionId));
        };

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onDecoratorPageNavigating);

        cboLauncherVisibility.getItems().setAll(LauncherVisibility.values());
        cboLauncherVisibility.setConverter(stringConverter(e -> i18n("settings.advanced.launcher_visibility." + e.name().toLowerCase(Locale.ROOT))));

        cboProcessPriority.getItems().setAll(ProcessPriority.values());
        cboProcessPriority.setConverter(stringConverter(e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT))));

        memoryStatus.set(OperatingSystem.getPhysicalMemoryStatus());
        componentList.disableProperty().bind(enableSpecificSettings.not());

        initMemoryStatusUpdateTask();
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
            FXUtils.unbind(txtWidth, lastVersionSetting.widthProperty());
            FXUtils.unbind(txtHeight, lastVersionSetting.heightProperty());
            maxMemory.unbindBidirectional(lastVersionSetting.maxMemoryProperty());
            javaCustomOption.valueProperty().unbindBidirectional(lastVersionSetting.javaDirProperty());
            gameDirCustomOption.valueProperty().unbindBidirectional(lastVersionSetting.gameDirProperty());
            FXUtils.unbind(txtServerIP, lastVersionSetting.serverIpProperty());
            chkAutoAllocate.selectedProperty().unbindBidirectional(lastVersionSetting.autoMemoryProperty());
            chkFullscreen.selectedProperty().unbindBidirectional(lastVersionSetting.fullscreenProperty());
            showLogsPane.selectedProperty().unbindBidirectional(lastVersionSetting.showLogsProperty());
            FXUtils.unbindEnum(cboLauncherVisibility, lastVersionSetting.launcherVisibilityProperty());
            FXUtils.unbindEnum(cboProcessPriority, lastVersionSetting.processPriorityProperty());

            lastVersionSetting.usesGlobalProperty().removeListener(usesGlobalListener);
            lastVersionSetting.javaVersionTypeProperty().removeListener(javaListener);
            lastVersionSetting.javaDirProperty().removeListener(javaListener);
            lastVersionSetting.defaultJavaPathPropertyProperty().removeListener(javaListener);
            lastVersionSetting.javaVersionProperty().removeListener(javaListener);

            gameDirItem.selectedDataProperty().unbindBidirectional(lastVersionSetting.gameDirTypeProperty());
            gameDirSublist.subtitleProperty().unbind();

            enableSpecificSettings.removeListener(specificSettingsListener);

            if (advancedVersionSettingPage != null) {
                advancedVersionSettingPage.unbindProperties();
                advancedVersionSettingPage = null;
            }
        }

        // unbind data fields
        javaItem.setToggleSelectedListener(null);
        javaVersionOption.valueProperty().unbind();

        // bind new data fields
        FXUtils.bindInt(txtWidth, versionSetting.widthProperty());
        FXUtils.bindInt(txtHeight, versionSetting.heightProperty());
        maxMemory.bindBidirectional(versionSetting.maxMemoryProperty());

        javaCustomOption.bindBidirectional(versionSetting.javaDirProperty());
        gameDirCustomOption.bindBidirectional(versionSetting.gameDirProperty());
        FXUtils.bindString(txtServerIP, versionSetting.serverIpProperty());
        chkAutoAllocate.selectedProperty().bindBidirectional(versionSetting.autoMemoryProperty());
        chkFullscreen.selectedProperty().bindBidirectional(versionSetting.fullscreenProperty());
        showLogsPane.selectedProperty().bindBidirectional(versionSetting.showLogsProperty());
        FXUtils.bindEnum(cboLauncherVisibility, versionSetting.launcherVisibilityProperty());
        FXUtils.bindEnum(cboProcessPriority, versionSetting.processPriorityProperty());

        if (versionId != null)
            enableSpecificSettings.set(!versionSetting.isUsesGlobal());
        versionSetting.usesGlobalProperty().addListener(usesGlobalListener);
        enableSpecificSettings.addListener(specificSettingsListener);

        javaItem.setToggleSelectedListener(newValue -> {
            if (javaItem.getSelectedData() == null || updatingSelectedJava)
                return;

            updatingJavaSetting = true;

            if (javaVersionOption.isSelected()) {
                javaVersionOption.valueProperty().bindBidirectional(versionSetting.javaVersionProperty());
            } else {
                javaVersionOption.valueProperty().unbind();
                javaVersionOption.setValue("");
            }

            if (javaCustomOption.isSelected()) {
                versionSetting.setUsesCustomJavaDir();
            } else if (javaAutoDeterminedOption.isSelected()) {
                versionSetting.setJavaAutoSelected();
            } else if (javaVersionOption.isSelected()) {
                if (versionSetting.getJavaVersionType() != JavaVersionType.VERSION)
                    versionSetting.setJavaVersion("");
                versionSetting.setJavaVersionType(JavaVersionType.VERSION);
                versionSetting.setDefaultJavaPath(null);
            } else {
                @SuppressWarnings("unchecked")
                JavaRuntime java = ((Pair<JavaVersionType, JavaRuntime>) newValue.getUserData()).getValue();
                versionSetting.setJavaVersionType(JavaVersionType.DETECTED);
                versionSetting.setJavaVersion(java.getVersion());
                versionSetting.setDefaultJavaPath(java.getBinary().toString());
            }

            updatingJavaSetting = false;
        });

        versionSetting.javaVersionTypeProperty().addListener(javaListener);
        versionSetting.javaDirProperty().addListener(javaListener);
        versionSetting.defaultJavaPathPropertyProperty().addListener(javaListener);
        versionSetting.javaVersionProperty().addListener(javaListener);

        gameDirItem.selectedDataProperty().bindBidirectional(versionSetting.gameDirTypeProperty());
        gameDirSublist.subtitleProperty().bind(Bindings.createStringBinding(() -> Paths.get(profile.getRepository().getRunDirectory(versionId).getAbsolutePath()).normalize().toString(),
                versionSetting.gameDirProperty(), versionSetting.gameDirTypeProperty()));

        lastVersionSetting = versionSetting;

        initJavaSubtitle();

        loadIcon();
    }

    private void initializeSelectedJava() {
        if (lastVersionSetting == null || updatingJavaSetting)
            return;

        updatingSelectedJava = true;
        switch (lastVersionSetting.getJavaVersionType()) {
            case CUSTOM:
                javaCustomOption.setSelected(true);
                break;
            case VERSION:
                javaVersionOption.setSelected(true);
                javaVersionOption.setValue(lastVersionSetting.getJavaVersion());
                break;
            case AUTO:
                javaAutoDeterminedOption.setSelected(true);
                break;
            default:
                Toggle toggle = null;
                if (JavaManager.isInitialized()) {
                    try {
                        JavaRuntime java = lastVersionSetting.getJava(null, null);
                        if (java != null) {
                            for (Toggle t : javaItem.getGroup().getToggles()) {
                                if (t.getUserData() != null) {
                                    @SuppressWarnings("unchecked")
                                    Pair<JavaVersionType, JavaRuntime> userData = (Pair<JavaVersionType, JavaRuntime>) t.getUserData();
                                    if (userData.getValue() != null && java.getBinary().equals(userData.getValue().getBinary())) {
                                        toggle = t;
                                        break;

                                    }
                                }
                            }
                        }
                    } catch (InterruptedException ignored) {
                    }
                }

                if (toggle != null) {
                    toggle.setSelected(true);
                } else {
                    Toggle selectedToggle = javaItem.getGroup().getSelectedToggle();
                    if (selectedToggle != null) {
                        selectedToggle.setSelected(false);
                    }
                }
                break;
        }
        updatingSelectedJava = false;
    }

    private void initJavaSubtitle() {
        FXUtils.checkFxUserThread();
        if (lastVersionSetting == null)
            return;
        initializeSelectedJava();
        HMCLGameRepository repository = this.profile.getRepository();
        String versionId = this.versionId;
        JavaVersionType javaVersionType = lastVersionSetting.getJavaVersionType();
        boolean autoSelected = javaVersionType == JavaVersionType.AUTO || javaVersionType == JavaVersionType.VERSION;

        if (versionId == null && autoSelected) {
            javaSublist.setSubtitle(i18n("settings.game.java_directory.auto"));
            return;
        }

        Pair<JavaVersionType, JavaRuntime> selectedData = javaItem.getSelectedData();
        if (selectedData != null && selectedData.getValue() != null) {
            javaSublist.setSubtitle(selectedData.getValue().getBinary().toString());
            return;
        }

        if (JavaManager.isInitialized()) {
            GameVersionNumber gameVersionNumber;
            Version version;
            if (versionId == null) {
                gameVersionNumber = GameVersionNumber.unknown();
                version = null;
            } else {
                gameVersionNumber = GameVersionNumber.asGameVersion(repository.getGameVersion(versionId));
                version = repository.getResolvedVersion(versionId);
            }

            try {
                JavaRuntime java = lastVersionSetting.getJava(gameVersionNumber, version);
                if (java != null) {
                    javaSublist.setSubtitle(java.getBinary().toString());
                } else {
                    javaSublist.setSubtitle(autoSelected ? i18n("settings.game.java_directory.auto.not_found") : i18n("settings.game.java_directory.invalid"));
                }
                return;
            } catch (InterruptedException ignored) {
            }
        }

        javaSublist.setSubtitle("");
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

        Controllers.dialog(new VersionIconDialog(profile, versionId, this::loadIcon));
    }

    private void onDeleteIcon() {
        if (versionId == null)
            return;

        profile.getRepository().deleteIconFile(versionId);
        VersionSetting localVersionSetting = profile.getRepository().getLocalVersionSettingOrCreate(versionId);
        if (localVersionSetting != null) {
            localVersionSetting.setVersionIcon(VersionIconType.DEFAULT);
        }
        loadIcon();
    }

    private void loadIcon() {
        if (versionId == null) {
            return;
        }

        iconPickerItem.setImage(profile.getRepository().getVersionIconImage(versionId));
        FXUtils.limitSize(iconPickerItem.getImageView(), 32, 32);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
