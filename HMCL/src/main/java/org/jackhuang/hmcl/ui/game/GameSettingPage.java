/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.game;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXRadioButton;
import com.jfoenix.controls.JFXTextField;
import javafx.event.ActionEvent;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.setting.property.SettingGroup;
import org.jackhuang.hmcl.setting.property.SettingProperty;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionIconDialog;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.setting.ConfigHolder.config;

/// @author Glavo
@NotNullByDefault
public final class GameSettingPage<S extends GameSetting> extends StackPane
        implements DecoratorPage, VersionPage.VersionLoadable, PageAware {

    private static final String I18N_INHERIT_GLOBAL_SETTING = "继承全局设置"; // TODO: i18n

    private final boolean isGlobalSetting;

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", new State("", null, false, false, false));
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// The selected profile.
    private @Nullable Profile profile;

    /// The current instance ID.
    private @Nullable String instanceId;

    /// The current setting.
    private final ObjectProperty<@Nullable S> currentSetting = new SimpleObjectProperty<>(this, "setting");

    private boolean updatingJavaSetting = false;
    private boolean updatingSelectedJava = false;
    private boolean updatingOverrideGroup = false;
    private boolean updatingParentSetting = false;
    private boolean showingGlobalSettingList = false;

    // GUI
    private final ScrollPane scrollPane;
    private final VBox rootPane;
    private final List<Node> editorNodes = new ArrayList<>();

    private final @UnknownNullability ImagePickerItem iconPickerItem;

    private final ComponentSublist javaSublist;
    private final RadioChoiceList<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaItem;
    private final RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaInheritedOption;
    private final RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaAutoDeterminedOption;
    private final RadioChoiceList.TextChoice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaVersionOption;
    private final RadioChoiceList.FileChoice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaCustomOption;
    private final InvalidationListener javaListener = o -> initializeSelectedJava();

    public GameSettingPage(Class<S> settingType) {
        assert settingType == GameSetting.Global.class || settingType == GameSetting.Instance.class;

        this.isGlobalSetting = settingType == GameSetting.Global.class;

        this.scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        getChildren().setAll(scrollPane);

        this.rootPane = new VBox();
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");
        scrollPane.setContent(rootPane);

        if (isGlobalSetting) {
            var globalSettingListButton = new ComponentList();
            rootPane.getChildren().add(globalSettingListButton);
            createGlobalSettingListButton(globalSettingListButton);
        }

        var basicSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle("基本设置"), // TODO: i18n
                basicSettings
        );
        {
            if (isGlobalSetting) {
                iconPickerItem = null;
            } else {
                iconPickerItem = new ImagePickerItem();
                basicSettings.getContent().add(iconPickerItem);
                iconPickerItem.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
                iconPickerItem.setTitle(i18n("settings.icon"));
                iconPickerItem.setOnSelectButtonClicked(e -> onExploreIcon());
                iconPickerItem.setOnDeleteButtonClicked(e -> onDeleteIcon());

                var parentGameSettingPane = new LineSelectButton<GameSetting.Global>();
                basicSettings.getContent().add(parentGameSettingPane);
                parentGameSettingPane.setTitle("全局游戏设置"); // TODO: i18n
                parentGameSettingPane.setConverter(setting -> setting != null ? setting.nameProperty().getValue() : "默认全局设置"); // TODO: i18n
                bindInstanceParentSetting(parentGameSettingPane);
            }

            // Java Setting
            javaSublist = new ComponentSublist();
            basicSettings.getContent().add(javaSublist);
            javaSublist.setTitle(i18n("settings.game.java_directory"));
            javaSublist.setHasSubtitle(true);
            {
                javaItem = new RadioChoiceList<>();
                javaSublist.getContent().setAll(javaItem);

                javaInheritedOption = new RadioChoiceList.Choice<>(I18N_INHERIT_GLOBAL_SETTING, pair(null, null));
                javaAutoDeterminedOption = new RadioChoiceList.Choice<>(i18n("settings.game.java_directory.auto"), pair(JavaVersionType.AUTO, null));
                javaVersionOption = new RadioChoiceList.TextChoice<>(i18n("settings.game.java_directory.version"), pair(JavaVersionType.VERSION, null));
                javaVersionOption.setValidators(new NumberValidator(true));
                FXUtils.setLimitWidth(javaVersionOption.getTextField(), 40);

                javaCustomOption = new RadioChoiceList.FileChoice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>>(i18n("settings.custom"), pair(JavaVersionType.CUSTOM, null))
                        .setChooserTitle(i18n("settings.game.java_directory.choose"));
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                    javaCustomOption.addExtensionFilter(new FileChooser.ExtensionFilter("Java", "java.exe")); // TODO: i18n

                holder.add(FXUtils.onWeakChangeAndOperate(JavaManager.getAllJavaProperty(), allJava -> {
                    var options = new ArrayList<RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>>>();
                    if (!isGlobalSetting) {
                        options.add(javaInheritedOption);
                    }
                    options.add(javaAutoDeterminedOption);
                    options.add(javaVersionOption);
                    if (allJava != null) {
                        boolean isX86 = Architecture.SYSTEM_ARCH.isX86() && allJava.stream().allMatch(java -> java.getArchitecture().isX86());

                        for (JavaRuntime java : allJava) {
                            options.add(new RadioChoiceList.Choice<>(
                                    i18n("settings.game.java_directory.template",
                                            java.getVersion(),
                                            isX86 ? i18n("settings.game.java_directory.bit", java.getBits().getBit())
                                                    : java.getPlatform().getArchitecture().getDisplayName()),
                                    pair(JavaVersionType.DETECTED, java))
                                    .setSubtitle(java.getBinary().toString()));
                        }
                    }

                    options.add(javaCustomOption);
                    javaItem.setChoices(options);
                    initializeSelectedJava();
                }));
            }
            currentSetting.addListener((o, oldSetting, newSetting) -> {
                if (oldSetting != null) {
                    oldSetting.javaTypeProperty().removeListener(javaListener);
                    oldSetting.defaultJavaPathProperty().removeListener(javaListener);
                    oldSetting.customJavaPathProperty().removeListener(javaListener);
                    oldSetting.javaVersionProperty().removeListener(javaListener);
                }

                if (newSetting != null) {
                    newSetting.javaTypeProperty().addListener(javaListener);
                    newSetting.defaultJavaPathProperty().addListener(javaListener);
                    newSetting.customJavaPathProperty().addListener(javaListener);
                    newSetting.javaVersionProperty().addListener(javaListener);
                }

                initJavaSubtitle();
            });

            javaItem.selectedChoiceProperty().addListener((observable, oldChoice, newChoice) -> {
                S setting = currentSetting.get();
                if (setting == null || updatingSelectedJava) {
                    return;
                }

                updatingJavaSetting = true;
                try {
                    if (javaInheritedOption.isSelected()) {
                        setting.javaTypeProperty().setValue(null);
                        return;
                    }

                    if (javaCustomOption.isSelected()) {
                        setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
                        setting.customJavaPathProperty().setValue(javaCustomOption.getPath());
                        setting.javaVersionProperty().setValue("");
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (javaAutoDeterminedOption.isSelected()) {
                        setting.javaTypeProperty().setValue(JavaVersionType.AUTO);
                        setting.javaVersionProperty().setValue("");
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (javaVersionOption.isSelected()) {
                        setting.javaTypeProperty().setValue(JavaVersionType.VERSION);
                        setting.javaVersionProperty().setValue(javaVersionOption.getText());
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (newChoice != null) {
                        @Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime> selectedJava = newChoice.getValue();
                        if (selectedJava != null
                                && selectedJava.getKey() == JavaVersionType.DETECTED
                                && selectedJava.getValue() != null) {
                            JavaRuntime java = selectedJava.getValue();
                            setting.javaTypeProperty().setValue(JavaVersionType.DETECTED);
                            setting.javaVersionProperty().setValue(java.getVersion());
                            setting.defaultJavaPathProperty().setValue(java.getBinary().toString());
                        }
                    }
                } finally {
                    updatingJavaSetting = false;
                    initJavaSubtitle();
                }
            });

            javaVersionOption.textProperty().addListener((observable, oldValue, newValue) -> {
                S setting = currentSetting.get();
                if (setting != null && javaVersionOption.isSelected() && !updatingSelectedJava) {
                    setting.javaVersionProperty().setValue(newValue);
                    initJavaSubtitle();
                }
            });

            javaCustomOption.pathProperty().addListener((observable, oldValue, newValue) -> {
                S setting = currentSetting.get();
                if (setting != null && javaCustomOption.isSelected() && !updatingSelectedJava) {
                    setting.customJavaPathProperty().setValue(newValue);
                    initJavaSubtitle();
                }
            });

            // Isolation Setting

            if (isGlobalSetting) {
                var defaultIsolationTypePane = new LineSelectButton<DefaultIsolationType>();
                basicSettings.getContent().add(defaultIsolationTypePane);
                defaultIsolationTypePane.setTitle("默认版本隔离策略"); // TODO: i18n
                defaultIsolationTypePane.setItems(DefaultIsolationType.values());
                defaultIsolationTypePane.setNullSafeConverter(type -> switch (type) {
                        case NEVER -> "从不隔离"; // TODO: i18n
                        case ALWAYS -> "总是隔离"; // TODO: i18n
                        case MODED -> "仅隔离模组实例"; // TODO: i18n
                    }); // TODO: i18n

                bindGlobalSettingBidirectional(defaultIsolationTypePane.valueProperty(), GameSetting.Global::defaultIsolationTypeProperty);
            } else {
                var isolationPane = new LineToggleButton();
                basicSettings.getContent().add(isolationPane);
                isolationPane.setTitle("版本隔离"); // TODO: i18n
                bindInstanceSettingBidirectional(isolationPane.selectedProperty(), GameSetting.Instance::isolationProperty);
            }

            // Memory Setting
            var memorySublist = new ComponentSublist(() -> {
                var autoMemoryPane = new LineToggleButton();
                autoMemoryPane.setTitle(i18n("settings.memory.auto_allocate"));
                bindSettingBidirectional(autoMemoryPane.selectedProperty(), GameSetting::autoMemoryProperty);

                var txtMaxMemory = new JFXTextField();
                txtMaxMemory.setPrefWidth(160);
                bindIntegerTextField(txtMaxMemory, GameSetting::maxMemoryProperty, false);
                var maxMemoryPane = new LinePane();
                maxMemoryPane.setTitle(i18n("settings.memory"));
                maxMemoryPane.setRight(new HBox(8, txtMaxMemory, new Label("MiB"))); // TODO: i18n

                var txtMinMemory = new JFXTextField();
                txtMinMemory.setPrefWidth(160);
                bindIntegerTextField(txtMinMemory, GameSetting::minMemoryProperty, true);
                var minMemoryPane = new LinePane();
                minMemoryPane.setTitle(i18n("settings.memory.lower_bound"));
                minMemoryPane.setRight(new HBox(8, txtMinMemory, new Label("MiB"))); // TODO: i18n

                var txtMetaspace = new JFXTextField();
                txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
                txtMetaspace.setPrefWidth(160);
                bindSettingBidirectional(txtMetaspace.textProperty(), GameSetting::permSizeProperty);
                var metaspacePane = new LinePane();
                metaspacePane.setTitle(i18n("settings.advanced.java_permanent_generation_space"));
                metaspacePane.setRight(new HBox(8, txtMetaspace, new Label("MiB"))); // TODO: i18n

                return List.of(autoMemoryPane, maxMemoryPane, minMemoryPane, metaspacePane);
            });
            basicSettings.getContent().add(memorySublist);
            memorySublist.setTitle(i18n("settings.memory"));
            memorySublist.setHasSubtitle(true);
            memorySublist.setSubtitle(i18n("settings.memory.auto_allocate"));
            memorySublist.setHeaderRight(createHeaderRight(memorySublist, GameSetting.MEMORY_SETTINGS));

            // Launcher Visibility Setting
            var launcherVisibilityPane = createInheritableButton(
                    GameSetting::launcherVisibilityProperty,
                    value -> i18n("settings.advanced.launcher_visibility." + value.name().toLowerCase(Locale.ROOT)),
                    null,
                    LauncherVisibility.values()
            );
            basicSettings.getContent().add(launcherVisibilityPane);
            launcherVisibilityPane.setTitle(i18n("settings.advanced.launcher_visible"));

            // Game Window Setting
            var windowTypeSublist = new ComponentSublist();
            basicSettings.getContent().add(windowTypeSublist);
            windowTypeSublist.setTitle("游戏窗口类型"); // TODO: i18n
            windowTypeSublist.setHasSubtitle(true);
            {
                var windowTypeItem = new RadioChoiceList<@Nullable GameWindowType>();
                var windowTypeOptions = new ArrayList<RadioChoiceList.Choice<@Nullable GameWindowType>>();
                if (isGlobalSetting) {
                    windowTypeItem.setFallbackValue(GameWindowType.WINDOWED);
                } else {
                    windowTypeOptions.add(new RadioChoiceList.Choice<>(I18N_INHERIT_GLOBAL_SETTING, null));
                    windowTypeItem.setFallbackValue(null);
                }

                var cboWindowSize = new JFXComboBox<String>();
                cboWindowSize.setPrefWidth(150);
                cboWindowSize.setEditable(true);
                cboWindowSize.setPromptText("854x480"); // TODO: i18n
                cboWindowSize.getItems().setAll(getSupportedResolutions());
                bindWindowSizeComboBox(cboWindowSize);

                for (GameWindowType type : GameWindowType.values()) {
                    if (type == GameWindowType.WINDOWED) {
                        windowTypeOptions.add(new WindowedWindowTypeOption(cboWindowSize));
                    } else {
                        windowTypeOptions.add(new RadioChoiceList.Choice<>(getWindowTypeDisplayName(type), type));
                    }
                }

                windowTypeItem.setChoices(windowTypeOptions);
                windowTypeSublist.getContent().add(windowTypeItem);
                bindInheritableRadioChoiceList(windowTypeItem, GameSetting::windowTypeProperty);
                bindInheritableSublistSubtitle(
                        windowTypeSublist,
                        GameSetting::windowTypeProperty,
                        GameSettingPage::getWindowTypeDisplayName);
            }

            var gameDirTypePane = createInheritableButton(
                    GameSetting::gameDirTypeProperty,
                    type -> switch (type) {
                        case ROOT_FOLDER -> "默认运行路径"; // TODO: i18n
                        case CUSTOM -> i18n("settings.custom");
                        case VERSION_FOLDER -> "版本文件夹"; // TODO: i18n
                    },
                    null,
                    GameDirectoryType.ROOT_FOLDER,
                    GameDirectoryType.CUSTOM
            );
            basicSettings.getContent().add(gameDirTypePane);
            gameDirTypePane.setTitle("游戏运行路径"); // TODO: i18n

            var runningDirPane = new LinePane();
            basicSettings.getContent().add(runningDirPane);
            runningDirPane.setTitle("自定义运行路径"); // TODO: i18n
            {
                var txtRunningDir = new JFXTextField();
                txtRunningDir.setPrefWidth(400);
                runningDirPane.setRight(txtRunningDir);
                bindSettingBidirectional(txtRunningDir.textProperty(), GameSetting::runningDirProperty);
            }

            // Show Logs Window Setting
            var showLogsPane = createInheritableBooleanButton(GameSetting::showLogsProperty);
            basicSettings.getContent().add(showLogsPane);
            showLogsPane.setTitle(i18n("settings.show_log"));

            // Enable Debug Log Output Setting
            var enableDebugLogOutputPane = createInheritableBooleanButton(GameSetting::enableDebugLogOutputProperty);
            basicSettings.getContent().add(enableDebugLogOutputPane);
            enableDebugLogOutputPane.setTitle(i18n("settings.enable_debug_log_output"));

            // Process Priority Setting
            var processPriorityPane = createInheritableButton(
                    GameSetting::processPriorityProperty,
                    e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT)),
                    e -> {
                        String bundleKey = "settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                        return I18n.hasKey(bundleKey) ? i18n(bundleKey) : "";
                    },
                    ProcessPriority.values()
            );
            basicSettings.getContent().add(processPriorityPane);
            processPriorityPane.setTitle(i18n("settings.advanced.process_priority"));

            // Quick Play
            var quickSublist = new ComponentSublist(() -> {
                var quickPlayItem = new RadioChoiceList<@Nullable QuickPlayType>();

                var noneOption = new RadioChoiceList.Choice<>("无", QuickPlayType.NONE); // TODO: i18n

                var multiplayerOption = new RadioChoiceList.TextChoice<>("多人联机", QuickPlayType.MULTIPLAYER); // TODO: i18n
                multiplayerOption.setValidators(new Validator(str -> {
                    if (StringUtils.isBlank(str))
                        return true;
                    try {
                        ServerAddress.parse(str);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }));

                var singleplayerOption = new RadioChoiceList.TextChoice<>("单人游戏", QuickPlayType.SINGLEPLAYER); // TODO: i18n
                singleplayerOption.setValidators(new Validator(str -> {
                    if (StringUtils.isBlank(str))
                        return true;
                    return FileUtils.isNameValid(str);
                }));

                var realmsOption = new RadioChoiceList.TextChoice<>("领域服", QuickPlayType.REALMS); // TODO: i18n

                var options = new ArrayList<RadioChoiceList.Choice<@Nullable QuickPlayType>>();
                if (isGlobalSetting) {
                    quickPlayItem.setFallbackValue(QuickPlayType.NONE);
                } else {
                    options.add(new RadioChoiceList.Choice<>(I18N_INHERIT_GLOBAL_SETTING, null));
                    quickPlayItem.setFallbackValue(null);
                }
                options.addAll(List.of(
                        noneOption,
                        multiplayerOption,
                        singleplayerOption,
                        realmsOption
                ));

                quickPlayItem.setChoices(options);

                //noinspection NullableProblems
                bindSettingBidirectional(quickPlayItem.selectedValueProperty(), GameSetting::quickPlayProperty);
                bindSettingBidirectional(multiplayerOption.textProperty(), GameSetting::quickPlayMultiplayerProperty);
                bindSettingBidirectional(singleplayerOption.textProperty(), GameSetting::quickPlaySingleplayerProperty);
                bindSettingBidirectional(realmsOption.textProperty(), GameSetting::quickPlayRealmsProperty);
                return List.of(quickPlayItem);
            });
            basicSettings.getContent().add(quickSublist);
            quickSublist.setTitle("快速游玩"); // TODO: i18n
            quickSublist.setSubtitle("启动游戏后直接进入指定服务器或世界"); // TODO: i18n
            quickSublist.setHasSubtitle(true);
        }

        var jvmSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle("JVM 选项"), // TODO: i18n
                jvmSettings
        );
        {
            var noJVMArgsPane = createInheritableBooleanButton(GameSetting::noJVMOptionsProperty);
            jvmSettings.getContent().add(noJVMArgsPane);
            noJVMArgsPane.setTitle(i18n("settings.advanced.no_jvm_args"));

            var noOptimizingJVMArgsPane = createInheritableBooleanButton(GameSetting::noOptimizingJVMOptionsProperty);
            jvmSettings.getContent().add(noOptimizingJVMArgsPane);
            noOptimizingJVMArgsPane.setTitle(i18n("settings.advanced.no_optimizing_jvm_args"));
            noOptimizingJVMArgsPane.disableProperty().bind(noJVMArgsPane.valueProperty().isEqualTo(Boolean.TRUE));

            if (!isGlobalSetting) {
                var noInheritJVMArgsPane = new LineToggleButton();
                jvmSettings.getContent().add(noInheritJVMArgsPane);
                noInheritJVMArgsPane.setTitle("覆盖全局 JVM 参数"); // TODO: i18n
                bindOverrideGroup(noInheritJVMArgsPane.selectedProperty(), GameSetting.JVM_OPTIONS);
            }

            var jvmArgsPane = new LinePane();
            jvmSettings.getContent().add(jvmArgsPane);
            jvmArgsPane.setTitle(i18n("settings.advanced.jvm_args"));
            {
                var txtJVMArgs = new JFXTextField();
                // txtJVMArgs.setPromptText(i18n("settings.advanced.jvm_args.prompt"));
                txtJVMArgs.setPrefWidth(400);
                jvmArgsPane.setRight(txtJVMArgs);
                bindSettingBidirectional(txtJVMArgs.textProperty(), GameSetting::jvmOptionsProperty);
            }
        }

        var advancedSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced")),
                advancedSettings
        );

        var gameArgsPane = new LinePane();
        advancedSettings.getContent().add(gameArgsPane);
        gameArgsPane.setTitle(i18n("settings.advanced.minecraft_arguments"));
        {
            var txtGameArgs = new JFXTextField();
            txtGameArgs.setPromptText(i18n("settings.advanced.minecraft_arguments.prompt"));
            txtGameArgs.setPrefWidth(400);
            gameArgsPane.setRight(txtGameArgs);
            bindSettingBidirectional(txtGameArgs.textProperty(), GameSetting::gameArgsProperty);
        }

        if (!isGlobalSetting) {
            var noInheritGameArgsPane = new LineToggleButton();
            advancedSettings.getContent().add(noInheritGameArgsPane);
            noInheritGameArgsPane.setTitle("覆盖全局游戏参数"); // TODO: i18n
            bindOverrideGroup(noInheritGameArgsPane.selectedProperty(), GameSetting.GAME_ARGUMENTS);
        }

        var customCommandSettings = new ComponentSublist(() -> {
            var pane = new GridPane();
            pane.setPadding(new Insets(10, 16, 10, 16));
            pane.setHgap(16);
            pane.setVgap(8);
            pane.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());

            var txtPreLaunchCommand = new JFXTextField();
            txtPreLaunchCommand.setPromptText(i18n("settings.advanced.precall_command.prompt"));
            txtPreLaunchCommand.getStyleClass().add("fit-width");
            pane.addRow(0, new Label(i18n("settings.advanced.precall_command")), txtPreLaunchCommand);
            bindSettingBidirectional(txtPreLaunchCommand.textProperty(), GameSetting::preLaunchCommandProperty);

            var txtWrapper = new JFXTextField();
            txtWrapper.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
            txtWrapper.getStyleClass().add("fit-width");
            pane.addRow(1, new Label(i18n("settings.advanced.wrapper_launcher")), txtWrapper);
            bindSettingBidirectional(txtWrapper.textProperty(), GameSetting::commandWrapperProperty);

            var txtPostExitCommand = new JFXTextField();
            txtPostExitCommand.setPromptText(i18n("settings.advanced.post_exit_command.prompt"));
            txtPostExitCommand.getStyleClass().add("fit-width");
            pane.addRow(2, new Label(i18n("settings.advanced.post_exit_command")), txtPostExitCommand);
            bindSettingBidirectional(txtPostExitCommand.textProperty(), GameSetting::postExitCommandProperty);

            return List.of(pane);
        });
        advancedSettings.getContent().add(customCommandSettings);
        customCommandSettings.setHasSubtitle(true);
        customCommandSettings.setTitle(i18n("settings.advanced.custom_commands"));
        customCommandSettings.setSubtitle("自定义启动游戏时的命令"); // TODO: i18n
        customCommandSettings.setTip(i18n("settings.advanced.custom_commands.hint"));

        var environmentVariablesPane = new LinePane();
        advancedSettings.getContent().add(environmentVariablesPane);
        environmentVariablesPane.setTitle("环境变量"); // TODO: i18n
        environmentVariablesPane.setSubtitle("传递给游戏进程的键值对"); // TODO: i18n
        {
            var txtEnvironmentVariables = new JFXTextField();
            txtEnvironmentVariables.setPrefWidth(400);
            environmentVariablesPane.setRight(txtEnvironmentVariables);
            bindSettingBidirectional(txtEnvironmentVariables.textProperty(), GameSetting::environmentVariablesProperty);
        }

        if (!isGlobalSetting) {
            var noInheritEnvironmentVariablesPane = new LineToggleButton();
            advancedSettings.getContent().add(noInheritEnvironmentVariablesPane);
            noInheritEnvironmentVariablesPane.setTitle("覆盖全局环境变量"); // TODO: i18n
            bindOverrideGroup(noInheritEnvironmentVariablesPane.selectedProperty(), GameSetting.ENVIRONMENT_VARIABLES);
        }

        var nativesSettings = new ComponentSublist(() -> {
            var useCustomNativesDirPane = new LineToggleButton();
            useCustomNativesDirPane.setTitle(i18n("settings.advanced.natives_directory.custom.enabled"));
            bindNativesDirTypeButton(useCustomNativesDirPane.selectedProperty());

            var nativesDirPane = new LinePane();
            nativesDirPane.setTitle(i18n("settings.advanced.natives_directory"));
            {
                var txtNativesDir = new JFXTextField();
                txtNativesDir.setPrefWidth(400);
                nativesDirPane.setRight(txtNativesDir);
                bindSettingBidirectional(txtNativesDir.textProperty(), GameSetting::nativesDirProperty);
            }

            var noNativesPatchPane = new LineToggleButton();
            noNativesPatchPane.setTitle(i18n("settings.advanced.dont_patch_natives"));
            bindSettingBidirectional(noNativesPatchPane.selectedProperty(), GameSetting::notPatchNativesProperty);

            var useNativeGLFWPane = new LineToggleButton();
            useNativeGLFWPane.setTitle(i18n("settings.advanced.use_native_glfw"));
            useNativeGLFWPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));
            bindSettingBidirectional(useNativeGLFWPane.selectedProperty(), GameSetting::useNativeGLFWProperty);

            var useNativeOpenALPane = new LineToggleButton();
            useNativeOpenALPane.setTitle(i18n("settings.advanced.use_native_openal"));
            useNativeOpenALPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));
            bindSettingBidirectional(useNativeOpenALPane.selectedProperty(), GameSetting::useNativeOpenALProperty);

            return List.of(useCustomNativesDirPane, nativesDirPane, noNativesPatchPane, useNativeGLFWPane, useNativeOpenALPane);
        });
        advancedSettings.getContent().add(nativesSettings);
        nativesSettings.setTitle(i18n("settings.advanced.natives"));
        nativesSettings.setHeaderRight(createHeaderRight(nativesSettings, GameSetting.NATIVE_SETTINGS));

        var graphicsBackendPane = new LineSelectButton<GraphicsAPI>();
        advancedSettings.getContent().add(graphicsBackendPane);
        graphicsBackendPane.setTitle(i18n("settings.advanced.graphics_backend"));
        graphicsBackendPane.setConverter(backend -> backend != null
                ? i18n("settings.advanced.graphics_backend." + backend.name().toLowerCase(Locale.ROOT))
                : I18N_INHERIT_GLOBAL_SETTING);
        graphicsBackendPane.setDescriptionConverter(backend -> {
            if (backend == null) {
                return null;
            }

            return switch (backend) {
                case DEFAULT -> i18n("settings.advanced.graphics_backend.default.desc");
                case OPENGL -> i18n("settings.advanced.graphics_backend.opengl.desc");
                case VULKAN -> i18n("settings.advanced.graphics_backend.vulkan.desc");
            };
        });
        graphicsBackendPane.setValue(GraphicsAPI.DEFAULT);
        if (isGlobalSetting) {
            graphicsBackendPane.setItems(GraphicsAPI.values());
        } else {
            var graphicsBackends = new ArrayList<GraphicsAPI>();
            graphicsBackends.add(null);
            graphicsBackends.addAll(Arrays.asList(GraphicsAPI.values()));
            graphicsBackendPane.setItems(graphicsBackends);
        }
        bindSettingBidirectional(graphicsBackendPane.valueProperty(), GameSetting::graphicsBackendProperty);

        var rendererPane = new LineSelectButton<Renderer>();
        advancedSettings.getContent().add(rendererPane);
        rendererPane.setTitle(i18n("settings.advanced.renderer"));
        rendererPane.setConverter(e -> e != null
                ? i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT))
                : I18N_INHERIT_GLOBAL_SETTING);
        rendererPane.setDescriptionConverter(e -> {
            if (e == null) {
                return null;
            }
            String bundleKey = "settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT) + ".desc";
            return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
        });
        rendererPane.setValue(Renderer.DEFAULT);

        FXUtils.onChangeAndOperate(graphicsBackendPane.valueProperty(), backend -> {
            if (backend == null) {
                rendererPane.setDisable(false);
                if (!isGlobalSetting) {
                    var renderers = new ArrayList<Renderer>();
                    renderers.add(null);
                    renderers.add(Renderer.DEFAULT);
                    rendererPane.setItems(renderers);
                }
                return;
            }

            if (isGlobalSetting) {
                rendererPane.setItems(Renderer.getSupported(backend));
            } else {
                var renderers = new ArrayList<Renderer>();
                renderers.add(null);
                renderers.addAll(Renderer.getSupported(backend));
                rendererPane.setItems(renderers);
            }
            if (backend == GraphicsAPI.DEFAULT) {
                rendererPane.setDisable(true);
                rendererPane.setValue(Renderer.DEFAULT);
            } else {
                rendererPane.setDisable(false);
                if (!(rendererPane.getValue() instanceof Renderer.Driver driver) || driver.api() != backend)
                    rendererPane.setValue(Renderer.DEFAULT);
            }
        });
        bindSettingBidirectional(rendererPane.valueProperty(), GameSetting::rendererProperty);

        var noGameCheckPane = createInheritableBooleanButton(GameSetting::notCheckGameProperty);
        advancedSettings.getContent().add(noGameCheckPane);
        noGameCheckPane.setTitle(i18n("settings.advanced.dont_check_game_completeness"));

        var noJVMCheckPane = createInheritableBooleanButton(GameSetting::notCheckJVMProperty);
        advancedSettings.getContent().add(noJVMCheckPane);
        noJVMCheckPane.setTitle(i18n("settings.advanced.dont_check_jvm_validity"));

        if (isGlobalSetting) {
            editorNodes.clear();
            editorNodes.addAll(rootPane.getChildren());
        }
    }

    // region Helper Methods for UI

    @SuppressWarnings("unchecked")
    private void selectGlobalSetting(GameSetting.Global setting) {
        currentSetting.set((S) setting);
    }

    private void createGlobalSettingListButton(ComponentList list) {
        var listButton = LineButton.createNavigationButton();
        listButton.setTitle("管理所有全局游戏设置"); // TODO: i18n
        listButton.setOnAction(event -> showGlobalSettingList());
        list.getContent().add(listButton);
    }

    private void showGlobalSettingEditor() {
        showingGlobalSettingList = false;
        getChildren().setAll(scrollPane);
        scrollPane.setContent(rootPane);
        rootPane.getChildren().setAll(editorNodes);
    }

    private void showGlobalSettingList() {
        showingGlobalSettingList = true;

        StackPane pane = new StackPane();
        pane.setPadding(new Insets(10));
        pane.getStyleClass().addAll("notice-pane");

        ComponentList root = new ComponentList();
        root.getStyleClass().add("no-padding");

        HBox toolbar = new HBox();
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPickOnBounds(false);
        toolbar.getChildren().setAll(ToolbarListPageSkin.createToolbarButton2(
                "新建全局游戏设置", // TODO: i18n
                SVG.ADD,
                this::createGlobalSetting));
        root.getContent().add(toolbar);

        JFXListView<GameSetting.Global> listView = new JFXListView<>();
        listView.setPadding(Insets.EMPTY);
        listView.setItems(FXCollections.observableArrayList(config().getGameSettings()));
        listView.setCellFactory(ignored -> new GlobalSettingListCell());
        listView.getStyleClass().add("no-horizontal-scrollbar");

        SpinnerPane center = new SpinnerPane();
        ComponentList.setVgrow(center, Priority.ALWAYS);
        center.setContent(listView);
        root.getContent().add(center);

        pane.getChildren().setAll(root);
        getChildren().setAll(pane);
    }

    private static String getGlobalSettingDisplayName(GameSetting.Global setting) {
        return StringUtils.isBlank(setting.nameProperty().getValue())
                ? setting.idProperty().getValue().toString()
                : setting.nameProperty().getValue();
    }

    private void createGlobalSetting() {
        Controllers.prompt("新建全局游戏设置", (name, handler) -> { // TODO: i18n
            if (StringUtils.isBlank(name)) {
                handler.reject(i18n("input.not_empty"));
                return;
            }

            GameSetting.Global setting = new GameSetting.Global();
            setting.nameProperty().setValue(name.trim());
            config().getGameSettings().add(setting);
            selectGlobalSetting(setting);
            if (showingGlobalSettingList) {
                showGlobalSettingEditor();
            }
            handler.resolve();
        }, "新设置", new RequiredValidator()); // TODO: i18n
    }

    /// List cell for global game settings, matching the instance list row layout.
    private final class GlobalSettingListCell extends ListCell<GameSetting.Global> {
        /// The reusable row graphic.
        private final RipplerContainer graphic;

        /// The selected-state radio button.
        private final JFXRadioButton selectedButton;

        /// The row text.
        private final TwoLineListItem content;

        /// Creates a global setting list cell.
        private GlobalSettingListCell() {
            BorderPane root = new BorderPane();
            root.getStyleClass().add("md-list-cell");
            root.setPadding(new Insets(8, 8, 8, 0));
            root.setCursor(Cursor.HAND);
            this.graphic = new RipplerContainer(root);

            this.selectedButton = new JFXRadioButton() {
                @Override
                public void fire() {
                    if (!isDisable() && !isSelected()) {
                        fireEvent(new ActionEvent());
                        selectCurrentItem();
                    }
                }
            };
            root.setLeft(selectedButton);
            BorderPane.setAlignment(selectedButton, Pos.CENTER);

            HBox center = new HBox();
            center.setMouseTransparent(true);
            center.setPrefWidth(Region.USE_PREF_SIZE);
            center.setAlignment(Pos.CENTER_LEFT);

            this.content = new TwoLineListItem();
            BorderPane.setAlignment(content, Pos.CENTER);
            center.getChildren().setAll(content);
            root.setCenter(center);

            HBox right = new HBox();
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXButton editButton = FXUtils.newToggleButton4(SVG.EDIT, 20);
            editButton.setOnAction(event -> editCurrentItem());
            FXUtils.installFastTooltip(editButton, "编辑全局游戏设置"); // TODO: i18n
            right.getChildren().add(editButton);
            root.setRight(right);

            FXUtils.onClicked(graphic, this::selectCurrentItem);
        }

        @Override
        protected void updateItem(@Nullable GameSetting.Global item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            setGraphic(graphic);
            content.setTitle(getGlobalSettingDisplayName(item));
            selectedButton.setSelected(Objects.equals(currentSetting.get(), item));
        }

        /// Selects the item represented by this cell.
        private void selectCurrentItem() {
            GameSetting.Global item = getItem();
            if (item == null) {
                return;
            }

            selectGlobalSetting(item);
            if (getListView() != null) {
                getListView().refresh();
            }
        }

        /// Opens the editor for the item represented by this cell.
        private void editCurrentItem() {
            GameSetting.Global item = getItem();
            if (item == null) {
                return;
            }

            selectGlobalSetting(item);
            showGlobalSettingEditor();
        }
    }

    private void bindInstanceParentSetting(LineSelectButton<GameSetting.Global> button) {
        ObservableList<GameSetting.Global> items = FXCollections.observableArrayList();
        InvalidationListener updateItems = observable -> {
            @Nullable GameSetting.Global selected = button.getValue();
            items.setAll((GameSetting.Global) null);
            items.addAll(config().getGameSettings());
            if (selected != null && config().getGameSetting(selected.idProperty().getValue()) == null) {
                button.setValue(null);
            }
        };
        updateItems.invalidated(config().getGameSettings());
        config().getGameSettings().addListener(updateItems);
        button.setItems(items);

        button.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingParentSetting || !(currentSetting.get() instanceof GameSetting.Instance setting)) {
                return;
            }
            setting.parentProperty().setValue(newValue != null ? newValue.idProperty().getValue() : null);
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (newValue instanceof GameSetting.Instance setting) {
                updatingParentSetting = true;
                try {
                    UUID parent = setting.parentProperty().getValue();
                    button.setValue(parent != null ? config().getGameSetting(parent) : null);
                } finally {
                    updatingParentSetting = false;
                }
            }
        });
    }

    private void bindOverrideGroup(BooleanProperty selected, SettingGroup group) {
        if (isGlobalSetting) {
            return;
        }

        selected.addListener((observable, oldValue, newValue) -> {
            if (updatingOverrideGroup || !(currentSetting.get() instanceof GameSetting.Instance setting)) {
                return;
            }

            if (newValue) {
                setting.getOverrideGroups().add(group);
            } else {
                setting.getOverrideGroups().remove(group);
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            updatingOverrideGroup = true;
            try {
                selected.set(newValue instanceof GameSetting.Instance setting && setting.getOverrideGroups().contains(group));
            } finally {
                updatingOverrideGroup = false;
            }
        });

        updatingOverrideGroup = true;
        try {
            selected.set(currentSetting.get() instanceof GameSetting.Instance setting && setting.getOverrideGroups().contains(group));
        } finally {
            updatingOverrideGroup = false;
        }
    }

    @SuppressWarnings("unchecked")
    private void bindIntegerTextField(JFXTextField textField, Function<S, ? extends Property<Integer>> propertyGetter, boolean nullable) {
        ObjectProperty<Property<Integer>> activeProperty = new SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener propertyListener = observable -> {
            Property<Integer> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                Integer value = property.getValue();
                textField.setText(value != null ? value.toString() : "");
            } finally {
                updating[0] = false;
            }
        };

        ChangeListener<String> textListener = (observable, oldValue, newValue) -> {
            Property<Integer> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                property.setValue(parseInteger(newValue, nullable));
            } finally {
                updating[0] = false;
            }
        };

        textField.textProperty().addListener(textListener);
        currentSetting.addListener((observable, oldValue, newValue) -> {
            Property<Integer> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(propertyListener);
            }

            Property<Integer> newProperty = newValue != null ? (Property<Integer>) propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newProperty != null) {
                newProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newProperty);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            Property<Integer> property = (Property<Integer>) propertyGetter.apply(setting);
            activeProperty.set(property);
            property.addListener(propertyListener);
            propertyListener.invalidated(property);
        }
    }

    private static @Nullable Integer parseInteger(@Nullable String value, boolean nullable) {
        if (StringUtils.isBlank(value)) {
            return nullable ? null : 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return nullable ? null : 0;
        }
    }

    private void bindWindowSizeComboBox(JFXComboBox<String> comboBox) {
        ObjectProperty<Property<Double>> activeWidthProperty = new SimpleObjectProperty<>();
        ObjectProperty<Property<Double>> activeHeightProperty = new SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener propertyListener = observable -> {
            Property<Double> widthProperty = activeWidthProperty.get();
            Property<Double> heightProperty = activeHeightProperty.get();
            if (widthProperty == null || heightProperty == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                Double width = widthProperty.getValue();
                Double height = heightProperty.getValue();
                comboBox.setValue(width != null && height != null ? formatWindowSize(width, height) : null);
            } finally {
                updating[0] = false;
            }
        };

        ChangeListener<Boolean> focusedListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                applyWindowSizeComboBoxValue(comboBox, activeWidthProperty.get(), activeHeightProperty.get(), updating);
            }
        };

        ChangeListener<Scene> sceneListener = (observable, oldValue, newValue) -> {
            if (newValue == null) {
                applyWindowSizeComboBoxValue(comboBox, activeWidthProperty.get(), activeHeightProperty.get(), updating);
            }
        };

        comboBox.focusedProperty().addListener(focusedListener);
        comboBox.sceneProperty().addListener(sceneListener);
        currentSetting.addListener((observable, oldValue, newValue) -> {
            Property<Double> oldWidthProperty = activeWidthProperty.get();
            Property<Double> oldHeightProperty = activeHeightProperty.get();
            if (oldWidthProperty != null) {
                oldWidthProperty.removeListener(propertyListener);
            }
            if (oldHeightProperty != null) {
                oldHeightProperty.removeListener(propertyListener);
            }

            Property<Double> newWidthProperty = newValue != null ? newValue.widthProperty() : null;
            Property<Double> newHeightProperty = newValue != null ? newValue.heightProperty() : null;
            activeWidthProperty.set(newWidthProperty);
            activeHeightProperty.set(newHeightProperty);
            if (newWidthProperty != null) {
                newWidthProperty.addListener(propertyListener);
            }
            if (newHeightProperty != null) {
                newHeightProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newWidthProperty);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            Property<Double> widthProperty = setting.widthProperty();
            Property<Double> heightProperty = setting.heightProperty();
            activeWidthProperty.set(widthProperty);
            activeHeightProperty.set(heightProperty);
            widthProperty.addListener(propertyListener);
            heightProperty.addListener(propertyListener);
            propertyListener.invalidated(widthProperty);
        }
    }

    private static void applyWindowSizeComboBoxValue(JFXComboBox<String> comboBox,
                                                     @Nullable Property<Double> widthProperty,
                                                     @Nullable Property<Double> heightProperty,
                                                     boolean[] updating) {
        if (widthProperty == null || heightProperty == null || updating[0]) {
            return;
        }

        updating[0] = true;
        try {
            String value = comboBox.getValue();
            if (StringUtils.isBlank(value)) {
                widthProperty.setValue(null);
                heightProperty.setValue(null);
                return;
            }

            int idx = value.indexOf('x');
            if (idx < 0) {
                idx = value.indexOf('*');
            }

            if (idx < 0) {
                comboBox.setValue(formatNullableWindowSize(widthProperty.getValue(), heightProperty.getValue()));
                return;
            }

            try {
                widthProperty.setValue(Double.parseDouble(value.substring(0, idx).trim()));
                heightProperty.setValue(Double.parseDouble(value.substring(idx + 1).trim()));
            } catch (NumberFormatException e) {
                comboBox.setValue(formatNullableWindowSize(widthProperty.getValue(), heightProperty.getValue()));
            }
        } finally {
            updating[0] = false;
        }
    }

    private static @Nullable String formatNullableWindowSize(@Nullable Double width, @Nullable Double height) {
        return width != null && height != null ? formatWindowSize(width, height) : null;
    }

    private static String formatWindowSize(double width, double height) {
        return Math.round(width) + "x" + Math.round(height);
    }

    private @Nullable Pane createHeaderRight(ComponentSublist sublist, SettingGroup group) {
        if (isGlobalSetting) { // TODO: use inheritGlobalSettings
            return null;
        }

        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        var inherit = new JFXCheckBox();
        box.getChildren().addAll(inherit, new Label("覆盖全局设置")); // TODO: i18n
        bindOverrideGroup(inherit.selectedProperty(), group);
        sublist.disableProperty().bind(inherit.selectedProperty().not());

        return box;
    }

    private Node createLabelWithTip(String title, String tip) {
        var tipIcon = new StackPane(SVG.INFO.createIcon(16));
        FXUtils.installFastTooltip(tipIcon, tip);

        var box = new HBox(8, new Label(title), tipIcon);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private <T> void bindSettingBidirectional(Property<T> property, Function<S, Property<T>> propertyGetter) {
        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null)
                property.unbindBidirectional(propertyGetter.apply(oldValue));

            if (newValue != null)
                property.bindBidirectional(propertyGetter.apply(newValue));
        });

        S setting = currentSetting.get();
        if (setting != null) {
            property.bindBidirectional(propertyGetter.apply(setting));
        }
    }

    /// Binds a radio choice list to an inheritable setting property.
    private <T> void bindInheritableRadioChoiceList(RadioChoiceList<@Nullable T> item, Function<S, InheritableProperty<T>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<T>> activeProperty = new SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener propertyListener = observable -> {
            InheritableProperty<T> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                @Nullable T value = property.getValue();
                item.setSelectedValue(isGlobalSetting && value == null ? property.defaultValue() : value);
            } finally {
                updating[0] = false;
            }
        };

        ChangeListener<@Nullable T> itemListener = (observable, oldValue, newValue) -> {
            InheritableProperty<T> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                property.setValue(newValue);
            } finally {
                updating[0] = false;
            }
        };

        item.selectedValueProperty().addListener(itemListener);
        currentSetting.addListener((observable, oldValue, newValue) -> {
            InheritableProperty<T> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(propertyListener);
            }

            InheritableProperty<T> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newProperty != null) {
                newProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newProperty);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            InheritableProperty<T> property = propertyGetter.apply(setting);
            activeProperty.set(property);
            property.addListener(propertyListener);
            propertyListener.invalidated(property);
        }
    }

    /// Binds a boolean toggle to the native library directory mode.
    private void bindNativesDirTypeButton(BooleanProperty selected) {
        ObjectProperty<@Nullable SettingProperty<NativesDirectoryType>> activeProperty = new SimpleObjectProperty<>();
        final boolean[] updating = {false};

        InvalidationListener propertyListener = observable -> {
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                @Nullable NativesDirectoryType nativesDirType = property.getValue();
                if (nativesDirType == null) {
                    nativesDirType = property.defaultValue();
                }
                selected.set(nativesDirType == NativesDirectoryType.CUSTOM);
            } finally {
                updating[0] = false;
            }
        };

        ChangeListener<Boolean> selectedListener = (observable, oldValue, newValue) -> {
            SettingProperty<NativesDirectoryType> property = activeProperty.get();
            if (property == null || updating[0]) {
                return;
            }

            updating[0] = true;
            try {
                property.setValue(newValue ? NativesDirectoryType.CUSTOM : NativesDirectoryType.VERSION_FOLDER);
            } finally {
                updating[0] = false;
            }
        };

        selected.addListener(selectedListener);
        currentSetting.addListener((observable, oldValue, newValue) -> {
            SettingProperty<NativesDirectoryType> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(propertyListener);
            }

            SettingProperty<NativesDirectoryType> newProperty = newValue != null
                    ? newValue.nativesDirTypeProperty()
                    : null;
            activeProperty.set(newProperty);
            if (newProperty != null) {
                newProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newProperty);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            SettingProperty<NativesDirectoryType> property = setting.nativesDirTypeProperty();
            activeProperty.set(property);
            property.addListener(propertyListener);
            propertyListener.invalidated(property);
        }
    }

    private <T> void bindInheritableSublistSubtitle(ComponentSublist sublist,
                                                    Function<S, InheritableProperty<T>> propertyGetter,
                                                    Function<T, String> converter) {
        InvalidationListener propertyListener = observable -> initInheritableSublistSubtitle(sublist, propertyGetter, converter);

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                propertyGetter.apply(oldValue).removeListener(propertyListener);
            }

            if (newValue != null) {
                propertyGetter.apply(newValue).addListener(propertyListener);
            }

            initInheritableSublistSubtitle(sublist, propertyGetter, converter);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            propertyGetter.apply(setting).addListener(propertyListener);
        }
        initInheritableSublistSubtitle(sublist, propertyGetter, converter);
    }

    private <T> void initInheritableSublistSubtitle(ComponentSublist sublist,
                                                   Function<S, InheritableProperty<T>> propertyGetter,
                                                   Function<T, String> converter) {
        S setting = currentSetting.get();
        if (setting == null) {
            sublist.setSubtitle("");
            return;
        }

        InheritableProperty<T> property = propertyGetter.apply(setting);
        @Nullable T value = property.getValue();
        if (value != null) {
            sublist.setSubtitle(converter.apply(value));
        } else if (isGlobalSetting) {
            sublist.setSubtitle(converter.apply(property.defaultValue()));
        } else {
            sublist.setSubtitle(I18N_INHERIT_GLOBAL_SETTING);
        }
    }

    private static String getWindowTypeDisplayName(GameWindowType type) {
        return switch (type) {
            case FULLSCREEN -> "全屏"; // TODO: i18n
            case MAXIMIZED -> "最大化"; // TODO: i18n
            case WINDOWED -> "窗口化"; // TODO: i18n
        };
    }

    /// Windowed game window mode option with the window size selector on the same row.
    private static final class WindowedWindowTypeOption extends RadioChoiceList.Choice<@Nullable GameWindowType> {
        /// The selector used to edit the initial game window size.
        private final JFXComboBox<String> windowSizeComboBox;

        /// Creates the windowed option.
        private WindowedWindowTypeOption(JFXComboBox<String> windowSizeComboBox) {
            super(getWindowTypeDisplayName(GameWindowType.WINDOWED), GameWindowType.WINDOWED);
            this.windowSizeComboBox = windowSizeComboBox;
        }

        /// Creates the option row with the size selector placed on the right.
        @Override
        protected Node createNode(ToggleGroup group) {
            BorderPane pane = new BorderPane();
            pane.setPadding(new Insets(3));
            FXUtils.setLimitHeight(pane, 30);

            configureRadioButton(group);
            BorderPane.setAlignment(radioButton, Pos.CENTER_LEFT);
            pane.setLeft(radioButton);

            windowSizeComboBox.disableProperty().bind(radioButton.selectedProperty().not());
            BorderPane.setAlignment(windowSizeComboBox, Pos.CENTER_RIGHT);
            pane.setRight(windowSizeComboBox);
            return pane;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void bindInstanceSettingBidirectional(Property<T> property, Function<GameSetting.Instance, Property<T>> propertyGetter) {
        assert !isGlobalSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    @SuppressWarnings("unchecked")
    private <T> void bindGlobalSettingBidirectional(Property<T> property, Function<GameSetting.Global, Property<T>> propertyGetter) {
        assert isGlobalSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    private LineSelectButton<@Nullable Boolean> createInheritableBooleanButton(
            Function<S, InheritableProperty<Boolean>> propertyGetter) {
        return createInheritableButton(
                propertyGetter,
                value -> value ? "启用" : "禁用", // TODO: i18n
                null,
                true, false
        );
    }

    @SafeVarargs
    private <T> LineSelectButton<@Nullable T> createInheritableButton(
            Function<S, InheritableProperty<T>> propertyGetter,
            Function<T, String> convert,
            @Nullable Function<T, String> descriptionConverter,
            T... items
    ) {
        var button = new LineSelectButton<@Nullable T>();

        button.setConverter(value -> value != null ? convert.apply(value) : I18N_INHERIT_GLOBAL_SETTING);

        if (descriptionConverter != null)
            button.setDescriptionConverter(value -> value != null ? descriptionConverter.apply(value) : null); // TODO

        if (isGlobalSetting) {
            button.setItems(items);
        } else {
            var actualItems = new ArrayList<@Nullable T>(items.length + 1);
            actualItems.add(null);
            actualItems.addAll(Arrays.asList(items));
            button.setItems(actualItems);
        }

        this.currentSetting.addListener((o, oldValue, newValue) -> {
            if (oldValue != null) {
                var property = propertyGetter.apply(oldValue);
                var binding = new InheritableBidirectionalBinding<>(isGlobalSetting, button, property);
                button.valueProperty().removeListener(binding);
                oldValue.removeListener(binding);
                button.setValue(isGlobalSetting ? property.defaultValue() : null);
            }

            if (newValue != null) {
                var property = propertyGetter.apply(newValue);
                button.setValue(isGlobalSetting && property.getValue() == null ? property.defaultValue() : property.getValue());

                var binding = new InheritableBidirectionalBinding<>(isGlobalSetting, button, property);
                button.valueProperty().addListener(binding);
                newValue.addListener(binding);
            }
        });

        return button;
    }

    /// @see #createInheritableBooleanButton(Function)
    private static final class InheritableBidirectionalBinding<T> implements InvalidationListener, WeakListener {
        private final boolean isGlobalSetting;
        private final WeakReference<LineSelectButton<@Nullable T>> buttonRef;
        private final WeakReference<InheritableProperty<T>> propertyRef;
        private final int hashCode;

        private boolean updating = false;

        private InheritableBidirectionalBinding(boolean isGlobal,
                                                LineSelectButton<@Nullable T> button,
                                                InheritableProperty<T> property) {
            this.isGlobalSetting = isGlobal;
            this.buttonRef = new WeakReference<>(button);
            this.propertyRef = new WeakReference<>(property);
            this.hashCode = System.identityHashCode(button) ^ System.identityHashCode(property);
        }

        @Override
        public void invalidated(Observable sourceProperty) {
            if (!updating) {
                final LineSelectButton<@Nullable T> button = buttonRef.get();
                final InheritableProperty<T> property = propertyRef.get();

                if (button == null || property == null) {
                    if (button != null) {
                        button.valueProperty().removeListener(this);
                    }

                    if (property != null) {
                        property.removeListener(this);
                    }
                } else {
                    updating = true;
                    try {
                        if (property == sourceProperty) {
                            @Nullable T newValue = property.getValue();
                            if (newValue == null) {
                                button.setValue(isGlobalSetting ? property.defaultValue() : null);
                            } else {
                                button.setValue(newValue);
                            }
                        } else {
                            property.setValue(button.getValue());
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return buttonRef.get() == null || propertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof GameSettingPage.InheritableBidirectionalBinding<?> that))
                return false;

            final var button = this.buttonRef.get();
            final var property = this.propertyRef.get();

            final var thatColorPicker = that.buttonRef.get();
            final var thatProperty = that.propertyRef.get();

            if (button == null || property == null || thatColorPicker == null || thatProperty == null)
                return false;

            return button == thatColorPicker && property == thatProperty;
        }
    }

    // endregion

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadVersion(Profile profile, @Nullable String instanceId) {
        this.profile = profile;
        this.instanceId = instanceId;

        assert isGlobalSetting == (instanceId == null);

        if (instanceId != null) {
            this.currentSetting.set((S) profile.getRepository().getLocalGameSettingOrCreate(instanceId));
            loadIcon();
        } else {
            this.currentSetting.set((S) config().getDefaultGameSettingOrCreate());
        }
    }

    private void loadIcon() {
        if (profile == null || instanceId == null)
            return;

        iconPickerItem.setImage(profile.getRepository().getVersionIconImage(instanceId));
    }

    private void initializeSelectedJava() {
        S setting = currentSetting.get();

        if (setting == null || updatingJavaSetting)
            return;

        updatingSelectedJava = true;
        JavaVersionType javaType = setting.javaTypeProperty().getValue();
        if (javaType != null) {
            switch (javaType) {
                case CUSTOM:
                    javaCustomOption.setSelected(true);
                    break;
                case VERSION:
                    javaVersionOption.setSelected(true);
                    javaVersionOption.setText(setting.javaVersionProperty().getValue());
                    break;
                case AUTO:
                    javaAutoDeterminedOption.setSelected(true);
                    break;
                default:
                    RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> choice = null;
                    if (JavaManager.isInitialized()) {
                        try {
                            JavaRuntime java = setting.getJava(null, null);
                            if (java != null) {
                                for (var candidate : javaItem.getChoices()) {
                                    var value = candidate.getValue();
                                    if (value != null && value.getValue() != null && java.getBinary().equals(value.getValue().getBinary())) {
                                        choice = candidate;
                                        break;
                                    }
                                }
                            }
                        } catch (InterruptedException ignored) {
                        }
                    }

                    if (choice != null) {
                        choice.setSelected(true);
                    } else {
                        javaItem.clearSelection();
                    }
                    break;
            }
        } else {
            javaInheritedOption.setSelected(true);
        }
        updatingSelectedJava = false;
    }

    private void initJavaSubtitle() {
        S setting = currentSetting.get();

        if (setting == null || profile == null)
            return;
        initializeSelectedJava();

        HMCLGameRepository repository = this.profile.getRepository();
        JavaVersionType javaVersionType = setting.javaTypeProperty().getValue();
        GameSetting.Effective effectiveSetting = this.instanceId != null ? repository.getEffectiveGameSetting(this.instanceId) : null;
        JavaVersionType effectiveJavaVersionType = effectiveSetting != null ? effectiveSetting.getJavaVersionType() : javaVersionType;
        boolean autoSelected = effectiveJavaVersionType == JavaVersionType.AUTO || effectiveJavaVersionType == JavaVersionType.VERSION;

        if (instanceId == null && autoSelected) {
            javaSublist.setSubtitle(i18n("settings.game.java_directory.auto"));
            return;
        }

        var selectedJava = javaItem.getSelectedValue();
        if (selectedJava != null && selectedJava.getValue() != null) {
            javaSublist.setSubtitle(selectedJava.getValue().getBinary().toString());
            return;
        }

        if (JavaManager.isInitialized()) {
            GameVersionNumber gameVersionNumber;
            Version version;
            if (this.instanceId == null) {
                gameVersionNumber = GameVersionNumber.unknown();
                version = null;
            } else {
                gameVersionNumber = GameVersionNumber.asGameVersion(repository.getGameVersion(this.instanceId));
                version = repository.getResolvedVersion(this.instanceId);
            }

            try {
                JavaRuntime java = effectiveSetting != null
                        ? effectiveSetting.getJava(gameVersionNumber, version)
                        : setting.getJava(gameVersionNumber, version);
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
        if (profile != null)
            Versions.modifyGameSettings(profile, profile.getSelectedVersion());
    }

    private void onExploreIcon() {
        if (profile == null || instanceId == null)
            return;

        Controllers.dialog(new VersionIconDialog(profile, instanceId, this::loadIcon));
    }

    private void onDeleteIcon() {
        if (profile == null || instanceId == null)
            return;

        profile.getRepository().deleteIconFile(instanceId);
        GameSetting.Instance localGameSetting = profile.getRepository().getLocalGameSettingOrCreate(instanceId);
        if (localGameSetting != null) {
            localGameSetting.iconProperty().setValue(VersionIconType.DEFAULT);
        }
        loadIcon();
    }

    private static List<String> getSupportedResolutions() {
        int maxScreenWidth = 0;
        int maxScreenHeight = 0;

        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            int screenWidth = (int) (bounds.getWidth() * screen.getOutputScaleX());
            int screenHeight = (int) (bounds.getHeight() * screen.getOutputScaleY());

            maxScreenWidth = Math.max(maxScreenWidth, screenWidth);
            maxScreenHeight = Math.max(maxScreenHeight, screenHeight);
        }

        List<String> resolutions = new ArrayList<>(List.of("854x480", "1280x720", "1600x900"));

        if (maxScreenWidth >= 1920 && maxScreenHeight >= 1080) resolutions.add("1920x1080");
        if (maxScreenWidth >= 2560 && maxScreenHeight >= 1440) resolutions.add("2560x1440");
        if (maxScreenWidth >= 3840 && maxScreenHeight >= 2160) resolutions.add("3840x2160");

        return resolutions;
    }

}
