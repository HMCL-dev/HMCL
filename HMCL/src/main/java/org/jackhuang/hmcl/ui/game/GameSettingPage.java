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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.css.PseudoClass;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.setting.property.SettingProperty;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionIconDialog;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.Holder;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String I18N_OVERRIDE_GLOBAL_SETTING = "覆盖全局设置"; // TODO: i18n
    private static final Object INHERIT_BUTTON_TOOLTIP_KEY = new Object();
    private static final PseudoClass PSEUDO_OVERRIDDEN = PseudoClass.getPseudoClass("overridden");
    private static final String INHERIT_BUTTON_STYLE_CLASS = "toggle-icon-tiny";
    private static final int INHERIT_BUTTON_ICON_SIZE = 12;

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
    private boolean updatingParentSetting = false;

    // GUI
    private final ScrollPane scrollPane;
    private final VBox rootPane;

    private final @UnknownNullability ImagePickerItem iconPickerItem;

    private final ComponentSublist javaSublist;
    private final RadioChoiceList<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaItem;
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
        var gameSettings = new ComponentList();
        var launcherSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle("基本设置"), // TODO: i18n
                basicSettings,
                ComponentList.createComponentListTitle("游戏设置"), // TODO: i18n
                gameSettings,
                ComponentList.createComponentListTitle("启动器设置"), // TODO: i18n
                launcherSettings
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

                var parentGameSettingPane = new LineSelectButton<GameSetting.@Nullable Global>();
                basicSettings.getContent().add(parentGameSettingPane);
                parentGameSettingPane.setTitle("全局游戏设置"); // TODO: i18n
                parentGameSettingPane.setConverter(setting -> setting != null ? setting.nameProperty().getValue() : "默认全局设置"); // TODO: i18n
                bindInstanceParentSetting(parentGameSettingPane);
            }

            // Java Setting
            javaSublist = new ComponentSublist();
            gameSettings.getContent().add(javaSublist);
            javaSublist.setTitle(i18n("settings.game.java_directory"));
            javaSublist.setHasSubtitle(true);
            {
                javaItem = new RadioChoiceList<>();
                javaSublist.getContent().setAll(javaItem);
                bindJavaInheritanceButton(javaSublist);

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
                    setting.javaTypeProperty().setValue(JavaVersionType.VERSION);
                    setting.javaVersionProperty().setValue(newValue);
                    initJavaSubtitle();
                }
            });

            javaCustomOption.pathProperty().addListener((observable, oldValue, newValue) -> {
                S setting = currentSetting.get();
                if (setting != null && javaCustomOption.isSelected() && !updatingSelectedJava) {
                    setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
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
                var isolationButton = new LineToggleButton();
                basicSettings.getContent().add(isolationButton);
                isolationButton.setTitle("版本隔离"); // TODO: i18n
                isolationButton.setSubtitle("启用后，当前实例将使用独立的游戏文件夹。"); // TODO: i18n
                bindInstanceIsolationButton(isolationButton);

                var gameDirSublist = new ComponentSublist();
                basicSettings.getContent().add(gameDirSublist);
                gameDirSublist.setTitle(i18n("game.directory"));
                gameDirSublist.setHasSubtitle(true);
                {
                    var gameDirItem = new RadioChoiceList<GameDirectoryType>();
                    var gameDirCustomOption = new RadioChoiceList.FileChoice<>(i18n("settings.custom"), GameDirectoryType.CUSTOM)
                            .setChooserTitle(i18n("settings.game.working_directory.choose"))
                            .setSelectionMode(FileSelector.SelectionMode.DIRECTORY);

                    gameDirItem.setFallbackValue(GameDirectoryType.ROOT_FOLDER);
                    gameDirItem.setChoices(List.of(
                            new RadioChoiceList.Choice<>(i18n("settings.advanced.game_dir.default"), GameDirectoryType.ROOT_FOLDER),
                            gameDirCustomOption
                    ));
                    gameDirSublist.getContent().add(gameDirItem);
                    bindGameDirectoryChoiceList(gameDirSublist, gameDirItem, gameDirCustomOption);
                }
            }

            // Memory Setting
            @Nullable JFXButton autoMemoryButton = !isGlobalSetting ? createInheritanceButton() : null;
            var memorySublist = new ComponentSublist(() -> {
                var memoryItem = new RadioChoiceList<Boolean>();
                memoryItem.setFallbackValue(true);

                var maxMemorySlider = new JFXSlider(0, 1, 0);
                maxMemorySlider.setPrefWidth(220);
                HBox.setMargin(maxMemorySlider, new Insets(0, 0, 0, 8));
                HBox.setHgrow(maxMemorySlider, Priority.ALWAYS);

                var maxMemoryTextField = new JFXTextField();
                FXUtils.setLimitWidth(maxMemoryTextField, 60);
                FXUtils.setValidateWhileTextChanged(maxMemoryTextField, true);
                maxMemoryTextField.setValidators(new NumberValidator(i18n("input.number"), false));

                @Nullable JFXButton maxMemoryButton = null;
                if (!isGlobalSetting) {
                    maxMemoryButton = createInheritanceButton();
                }

                var options = new ArrayList<RadioChoiceList.Choice<Boolean>>();
                options.add(new RadioChoiceList.Choice<>(i18n("settings.memory.auto_allocate"), true));
                options.add(new ManualMemoryChoice(maxMemorySlider, maxMemoryTextField, maxMemoryButton));
                memoryItem.setChoices(options);

                var memoryStatusBar = new MemoryStatusBar();

                var digitalPane = new BorderPane();
                var physicalMemoryLabel = new Label();
                physicalMemoryLabel.getStyleClass().add("memory-label");
                digitalPane.setLeft(physicalMemoryLabel);
                var allocatedMemoryLabel = new Label();
                allocatedMemoryLabel.getStyleClass().add("memory-label");
                digitalPane.setRight(allocatedMemoryLabel);

                var memoryStatusPane = new VBox();
                memoryStatusPane.setPadding(new Insets(16, 16, 10, 16));
                memoryStatusPane.getChildren().setAll(memoryStatusBar, digitalPane);
                ComponentList.setNoPadding(memoryStatusPane);

                IndependentSettingBinder.bindMemoryChoiceList(
                        currentSetting,
                        memoryItem,
                        maxMemorySlider,
                        maxMemoryTextField,
                        memoryStatusBar,
                        digitalPane,
                        autoMemoryButton,
                        maxMemoryButton,
                        GameSettingPage::updateInheritanceButton,
                        this::getParentGameSetting);

                return List.of(memoryItem, memoryStatusPane);
            });
            if (autoMemoryButton != null) {
                memorySublist.setTitleRight(autoMemoryButton);
            }
            gameSettings.getContent().add(memorySublist);
            memorySublist.setTitle(i18n("settings.memory"));
            memorySublist.setHasSubtitle(true);
            memorySublist.setDescription(i18n("settings.memory.auto_allocate"));

            // Launcher Visibility Setting
            var launcherVisibilityPane = createInheritableButton(
                    GameSetting::launcherVisibilityProperty,
                    value -> i18n("settings.advanced.launcher_visibility." + value.name().toLowerCase(Locale.ROOT)),
                    null,
                    LauncherVisibility.values()
            );
            launcherSettings.getContent().add(launcherVisibilityPane);
            launcherVisibilityPane.setTitle(i18n("settings.advanced.launcher_visible"));

            // Game Window Setting
            var windowTypeSublist = new ComponentSublist();
            gameSettings.getContent().add(windowTypeSublist);
            windowTypeSublist.setTitle("游戏窗口类型"); // TODO: i18n
            windowTypeSublist.setHasSubtitle(true);
            {
                var windowTypeItem = new RadioChoiceList<GameWindowType>();
                var windowTypeOptions = new ArrayList<RadioChoiceList.Choice<GameWindowType>>();
                windowTypeItem.setFallbackValue(GameWindowType.WINDOWED);

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
                bindInheritableRadioChoiceList(windowTypeSublist, windowTypeItem, GameSetting::windowTypeProperty);
                bindInheritableSublistDescription(
                        windowTypeSublist,
                        GameSetting::windowTypeProperty,
                        GameSettingPage::getWindowTypeDisplayName);
            }

            // Show Logs Window Setting
            var showLogsPane = createInheritableBooleanButton(GameSetting::showLogsProperty);
            launcherSettings.getContent().add(showLogsPane);
            showLogsPane.setTitle(i18n("settings.show_log"));

            // Enable Debug Log Output Setting
            var enableDebugLogOutputPane = createInheritableBooleanButton(GameSetting::enableDebugLogOutputProperty);
            launcherSettings.getContent().add(enableDebugLogOutputPane);
            enableDebugLogOutputPane.setTitle(i18n("settings.enable_debug_log_output"));

            var noGameCheckPane = createInheritableBooleanButton(GameSetting::notCheckGameProperty);
            launcherSettings.getContent().add(noGameCheckPane);
            noGameCheckPane.setTitle(i18n("settings.advanced.dont_check_game_completeness"));

            // Quick Play
            var quickSublist = new ComponentSublist();
            {
                var quickPlayItem = new RadioChoiceList<QuickPlayType>();

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

                quickPlayItem.setFallbackValue(QuickPlayType.NONE);
                quickPlayItem.setChoices(List.of(
                        noneOption,
                        multiplayerOption,
                        singleplayerOption,
                        realmsOption
                ));

                bindInheritableRadioChoiceList(quickSublist, quickPlayItem, GameSetting::quickPlayProperty);
                bindSettingBidirectional(multiplayerOption.textProperty(), GameSetting::quickPlayMultiplayerProperty);
                bindSettingBidirectional(singleplayerOption.textProperty(), GameSetting::quickPlaySingleplayerProperty);
                bindSettingBidirectional(realmsOption.textProperty(), GameSetting::quickPlayRealmsProperty);
                quickSublist.getContent().setAll(quickPlayItem);
            }
            gameSettings.getContent().add(quickSublist);
            quickSublist.setTitle("快速游玩"); // TODO: i18n
            quickSublist.setSubtitle("启动游戏后直接进入指定服务器或世界"); // TODO: i18n
            quickSublist.setHasSubtitle(true);

            var advancedLaunchSublist = new ComponentSublist(() -> {
                var gameArgsPane = new LinePane();
                gameArgsPane.setTitle(i18n("settings.advanced.minecraft_arguments"));
                {
                    var txtGameArgs = new JFXTextField();
                    txtGameArgs.setPromptText(i18n("settings.advanced.minecraft_arguments.prompt"));
                    txtGameArgs.setPrefWidth(400);
                    gameArgsPane.setRight(txtGameArgs);
                    bindIndependentTextField(gameArgsPane, txtGameArgs, GameSetting::gameArgsProperty);
                }

                var environmentVariablesPane = new LinePane();
                environmentVariablesPane.setTitle("环境变量"); // TODO: i18n
                environmentVariablesPane.setSubtitle("传递给游戏进程的键值对"); // TODO: i18n
                {
                    var txtEnvironmentVariables = new JFXTextField();
                    txtEnvironmentVariables.setPrefWidth(400);
                    environmentVariablesPane.setRight(txtEnvironmentVariables);
                    bindIndependentTextField(environmentVariablesPane, txtEnvironmentVariables, GameSetting::environmentVariablesProperty);
                }

                var processPriorityPane = createInheritableButton(
                        GameSetting::processPriorityProperty,
                        e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT)),
                        e -> {
                            String bundleKey = "settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                            return I18n.hasKey(bundleKey) ? i18n(bundleKey) : "";
                        },
                        ProcessPriority.values()
                );
                processPriorityPane.setTitle(i18n("settings.advanced.process_priority"));

                return List.of(gameArgsPane, environmentVariablesPane, processPriorityPane);
            });
            gameSettings.getContent().add(advancedLaunchSublist);
            advancedLaunchSublist.setTitle("高级选项"); // TODO: i18n
            advancedLaunchSublist.setSubtitle("游戏参数、环境变量与进程优先级"); // TODO: i18n
            advancedLaunchSublist.setHasSubtitle(true);
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
            noOptimizingJVMArgsPane.disableProperty().bind(noJVMArgsPane.effectiveValueProperty());

            var noJVMCheckPane = createInheritableBooleanButton(GameSetting::notCheckJVMProperty);
            jvmSettings.getContent().add(noJVMCheckPane);
            noJVMCheckPane.setTitle(i18n("settings.advanced.dont_check_jvm_validity"));

            var jvmArgsPane = new LinePane();
            jvmSettings.getContent().add(jvmArgsPane);
            jvmArgsPane.setTitle(i18n("settings.advanced.jvm_args"));
            {
                var txtJVMArgs = new JFXTextField();
                // txtJVMArgs.setPromptText(i18n("settings.advanced.jvm_args.prompt"));
                txtJVMArgs.setPrefWidth(400);
                jvmArgsPane.setRight(txtJVMArgs);
                bindIndependentTextField(jvmArgsPane, txtJVMArgs, GameSetting::jvmOptionsProperty);
            }

            var deprecatedJvmMemorySettings = new ComponentSublist(() -> {
                var minMemoryPane = new LinePane();
                minMemoryPane.setTitle(i18n("settings.memory.lower_bound"));
                {
                    var txtMinMemory = new JFXTextField();
                    txtMinMemory.setPrefWidth(160);
                    minMemoryPane.setRight(new HBox(8, txtMinMemory, new Label("MiB"))); // TODO: i18n
                    bindIndependentIntegerTextField(minMemoryPane, txtMinMemory, GameSetting::minMemoryProperty);
                }

                var metaspacePane = new LinePane();
                metaspacePane.setTitle(i18n("settings.advanced.java_permanent_generation_space"));
                {
                    var txtMetaspace = new JFXTextField();
                    txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
                    txtMetaspace.setPrefWidth(160);
                    metaspacePane.setRight(new HBox(8, txtMetaspace, new Label("MiB"))); // TODO: i18n
                    bindIndependentTextField(metaspacePane, txtMetaspace, GameSetting::permSizeProperty);
                }

                return List.of(minMemoryPane, metaspacePane);
            });
            jvmSettings.getContent().add(deprecatedJvmMemorySettings);
            deprecatedJvmMemorySettings.setTitle("已弃用的 JVM 内存选项"); // TODO: i18n
            deprecatedJvmMemorySettings.setHasSubtitle(true);
            deprecatedJvmMemorySettings.setSubtitle("这些选项只为兼容旧版本保留"); // TODO: i18n
        }

        var customCommandSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced.custom_commands")),
                customCommandSettings
        );
        {
            var preLaunchCommandPane = new LinePane();
            customCommandSettings.getContent().add(preLaunchCommandPane);
            preLaunchCommandPane.setTitle(i18n("settings.advanced.precall_command"));
            {
                var txtPreLaunchCommand = new JFXTextField();
                txtPreLaunchCommand.setPromptText(i18n("settings.advanced.precall_command.prompt"));
                txtPreLaunchCommand.setPrefWidth(400);
                preLaunchCommandPane.setRight(txtPreLaunchCommand);
                bindInheritableTextField(preLaunchCommandPane, txtPreLaunchCommand, GameSetting::preLaunchCommandProperty);
            }

            var wrapperPane = new LinePane();
            customCommandSettings.getContent().add(wrapperPane);
            wrapperPane.setTitle(i18n("settings.advanced.wrapper_launcher"));
            {
                var txtWrapper = new JFXTextField();
                txtWrapper.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
                txtWrapper.setPrefWidth(400);
                wrapperPane.setRight(txtWrapper);
                bindInheritableTextField(wrapperPane, txtWrapper, GameSetting::commandWrapperProperty);
            }

            var postExitCommandPane = new LinePane();
            customCommandSettings.getContent().add(postExitCommandPane);
            postExitCommandPane.setTitle(i18n("settings.advanced.post_exit_command"));
            {
                var txtPostExitCommand = new JFXTextField();
                txtPostExitCommand.setPromptText(i18n("settings.advanced.post_exit_command.prompt"));
                txtPostExitCommand.setPrefWidth(400);
                postExitCommandPane.setRight(txtPostExitCommand);
                bindInheritableTextField(postExitCommandPane, txtPostExitCommand, GameSetting::postExitCommandProperty);
            }
        }

        var graphicsSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle("图形设置"), // TODO: i18n
                graphicsSettings
        );
        {
            var graphicsBackendPane = createInheritableButton(
                    GameSetting::graphicsBackendProperty,
                    backend -> i18n("settings.advanced.graphics_backend." + backend.name().toLowerCase(Locale.ROOT)),
                    backend -> switch (backend) {
                        case DEFAULT -> i18n("settings.advanced.graphics_backend.default.desc");
                        case OPENGL -> i18n("settings.advanced.graphics_backend.opengl.desc");
                        case VULKAN -> i18n("settings.advanced.graphics_backend.vulkan.desc");
                    },
                    GraphicsAPI.values());
            graphicsSettings.getContent().add(graphicsBackendPane);
            graphicsBackendPane.setTitle(i18n("settings.advanced.graphics_backend"));

            var openGLRendererPane = createInheritableButton(
                    GameSetting::openGLRendererProperty,
                    e -> i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT)),
                    e -> {
                        String bundleKey = "settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                        return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                    },
                    Renderer.getSupported(GraphicsAPI.OPENGL).toArray(Renderer[]::new));
            graphicsSettings.getContent().add(openGLRendererPane);
            openGLRendererPane.setTitle("OpenGL 渲染器/驱动"); // TODO: i18n

            var vulkanRendererPane = createInheritableButton(
                    GameSetting::vulkanRendererProperty,
                    e -> i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT)),
                    e -> {
                        String bundleKey = "settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                        return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                    },
                    Renderer.getSupported(GraphicsAPI.VULKAN).toArray(Renderer[]::new));
            graphicsSettings.getContent().add(vulkanRendererPane);
            vulkanRendererPane.setTitle("Vulkan 渲染器/驱动"); // TODO: i18n

        }

        var nativeLibrarySettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle("本机库设置"), // TODO: i18n
                nativeLibrarySettings
        );
        {
            var useCustomNativesDirPane = createIndependentNativesDirTypeButton();
            nativeLibrarySettings.getContent().add(useCustomNativesDirPane);
            useCustomNativesDirPane.setTitle(i18n("settings.advanced.natives_directory.custom.enabled"));

            var nativesDirPane = new LinePane();
            nativeLibrarySettings.getContent().add(nativesDirPane);
            nativesDirPane.setTitle(i18n("settings.advanced.natives_directory"));
            {
                var txtNativesDir = new JFXTextField();
                txtNativesDir.setPrefWidth(400);
                nativesDirPane.setRight(txtNativesDir);
                bindIndependentTextField(nativesDirPane, txtNativesDir, GameSetting::nativesDirProperty);
            }

            var noNativesPatchPane = createIndependentBooleanButton(GameSetting::notPatchNativesProperty);
            nativeLibrarySettings.getContent().add(noNativesPatchPane);
            noNativesPatchPane.setTitle(i18n("settings.advanced.dont_patch_natives"));

            var useNativeGLFWPane = createIndependentBooleanButton(GameSetting::useNativeGLFWProperty);
            nativeLibrarySettings.getContent().add(useNativeGLFWPane);
            useNativeGLFWPane.setTitle(i18n("settings.advanced.use_native_glfw"));
            useNativeGLFWPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));

            var useNativeOpenALPane = createIndependentBooleanButton(GameSetting::useNativeOpenALProperty);
            nativeLibrarySettings.getContent().add(useNativeOpenALPane);
            useNativeOpenALPane.setTitle(i18n("settings.advanced.use_native_openal"));
            useNativeOpenALPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));
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
        listButton.setOnAction(event -> {
            var transition = new PauseTransition(Duration.millis(120));
            transition.setOnFinished(ignored -> Controllers.navigateForward(new GlobalGameSettingListPage(
                    this::getCurrentGlobalSetting,
                    this::selectGlobalSetting,
                    setting -> {
                        selectGlobalSetting(setting);
                        Controllers.navigate(this);
                    })));
            transition.play();
        });
        list.getContent().add(listButton);
    }

    private @Nullable GameSetting.Global getCurrentGlobalSetting() {
        GameSetting setting = currentSetting.get();
        return setting instanceof GameSetting.Global global ? global : null;
    }

    private void bindInstanceParentSetting(LineSelectButton<GameSetting.@Nullable Global> button) {
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

    /// Adds the title-line inheritance button for the Java selection sublist.
    private void bindJavaInheritanceButton(ComponentSublist sublist) {
        if (isGlobalSetting) {
            return;
        }

        var button = createInheritanceButton();
        sublist.setTitleRight(button);

        InvalidationListener refresh = observable -> {
            S setting = currentSetting.get();
            updateInheritanceButton(button, setting == null || setting.javaTypeProperty().getValue() == null);
        };

        button.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            S setting = currentSetting.get();
            if (setting == null) {
                return;
            }

            updatingJavaSetting = true;
            try {
                if (setting.javaTypeProperty().getValue() == null) {
                    GameSetting source = getEffectiveInheritableSource(setting, GameSetting::javaTypeProperty);
                    setting.javaTypeProperty().setValue(getEffectiveValue(setting, GameSetting::javaTypeProperty));
                    setting.javaVersionProperty().setValue(source.javaVersionProperty().getValue());
                    setting.customJavaPathProperty().setValue(source.customJavaPathProperty().getValue());
                    setting.defaultJavaPathProperty().setValue(source.defaultJavaPathProperty().getValue());
                } else {
                    setting.javaTypeProperty().setValue(null);
                }
            } finally {
                updatingJavaSetting = false;
            }

            initializeSelectedJava();
            initJavaSubtitle();
            refresh.invalidated(setting);
            event.consume();
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            setting.addListener(refresh);
        }
        refresh.invalidated(setting);
    }

    /// Binds a text field to a setting property with independent override state.
    private void bindIndependentTextField(
            LineComponent line,
            JFXTextField textField,
            Function<GameSetting, SettingProperty<String>> propertyGetter) {
        IndependentSettingBinder.bindTextField(
                isGlobalSetting,
                currentSetting,
                line,
                textField,
                propertyGetter,
                this::createInheritanceButton,
                GameSettingPage::updateInheritanceButton,
                this::getParentGameSetting);
    }

    /// Binds an integer text field to a setting property with independent override state.
    private void bindIndependentIntegerTextField(
            LineComponent line,
            JFXTextField textField,
            Function<GameSetting, ? extends SettingProperty<Integer>> propertyGetter) {
        IndependentSettingBinder.bindIntegerTextField(
                isGlobalSetting,
                currentSetting,
                line,
                textField,
                propertyGetter,
                this::createInheritanceButton,
                GameSettingPage::updateInheritanceButton,
                this::getParentGameSetting);
    }

    private void bindWindowSizeComboBox(JFXComboBox<String> comboBox) {
        ObjectProperty<@Nullable Property<Double>> activeWidthProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable Property<Double>> activeHeightProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener propertyListener = observable -> {
            @Nullable Property<Double> widthProperty = activeWidthProperty.get();
            @Nullable Property<Double> heightProperty = activeHeightProperty.get();
            if (widthProperty == null || heightProperty == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                Double width = widthProperty.getValue();
                Double height = heightProperty.getValue();
                comboBox.setValue(width != null && height != null ? formatWindowSize(width, height) : null);
            } finally {
                updating.value = false;
            }
        };

        ChangeListener<@Nullable Boolean> focusedListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                applyWindowSizeComboBoxValue(comboBox, activeWidthProperty.get(), activeHeightProperty.get(), updating);
            }
        };

        ChangeListener<@Nullable Scene> sceneListener = (observable, oldValue, newValue) -> {
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
                                                     Holder<Boolean> updating) {
        if (widthProperty == null || heightProperty == null || updating.value) {
            return;
        }

        updating.value = true;
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
            updating.value = false;
        }
    }

    private static @Nullable String formatNullableWindowSize(@Nullable Double width, @Nullable Double height) {
        return width != null && height != null ? formatWindowSize(width, height) : null;
    }

    private static String formatWindowSize(double width, double height) {
        return Math.round(width) + "x" + Math.round(height);
    }

    /// Creates a compact button that displays inherited or overridden state.
    private JFXButton createInheritanceButton() {
        var button = new JFXButton();
        button.getStyleClass().add(INHERIT_BUTTON_STYLE_CLASS);
        Tooltip tooltip = new Tooltip();
        button.getProperties().put(INHERIT_BUTTON_TOOLTIP_KEY, tooltip);
        FXUtils.installFastTooltip(button, tooltip);
        updateInheritanceButton(button, true);
        return button;
    }

    /// Updates the icon and pseudo class of an inheritance state button.
    private static void updateInheritanceButton(JFXButton button, boolean inherited) {
        button.setGraphic((inherited ? SVG.PUBLIC : SVG.TUNE).createIcon(INHERIT_BUTTON_ICON_SIZE));
        button.pseudoClassStateChanged(PSEUDO_OVERRIDDEN, !inherited);

        Object tooltip = button.getProperties().get(INHERIT_BUTTON_TOOLTIP_KEY);
        if (tooltip instanceof Tooltip inheritTooltip) {
            inheritTooltip.setText(inherited ? I18N_INHERIT_GLOBAL_SETTING : I18N_OVERRIDE_GLOBAL_SETTING);
        }
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

    /// Binds a text field to an inheritable string setting.
    private void bindInheritableTextField(
            LineComponent line,
            JFXTextField textField,
            Function<GameSetting, InheritableProperty<String>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<String>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isGlobalSetting) {
            inheritButton = createInheritanceButton();
            line.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                textField.setText(getEffectiveValue(setting, propertyGetter));
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, property.getValue() == null);
                }
            } finally {
                updating.value = false;
            }
        };

        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<String> property = activeProperty.get();
            if (property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                property.setValue(newValue != null ? newValue : "");
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, false);
                }
            } finally {
                updating.value = false;
            }
        });

        if (finalInheritButton != null) {
            finalInheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                InheritableProperty<String> property = activeProperty.get();
                if (setting == null || property == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    if (property.getValue() == null) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                    } else {
                        property.setValue(null);
                    }
                } finally {
                    updating.value = false;
                }
                refresh.invalidated(property);
                event.consume();
            });
        }

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            InheritableProperty<String> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(refresh);
            }

            InheritableProperty<String> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            if (newProperty != null) {
                newProperty.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting != null) {
            InheritableProperty<String> property = propertyGetter.apply(setting);
            activeProperty.set(property);
            setting.addListener(refresh);
            property.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds the instance isolation toggle to the current instance setting.
    private void bindInstanceIsolationButton(LineToggleButton button) {
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            SettingProperty<Boolean> property = activeProperty.get();
            if (property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean forceIsolated = isCurrentInstanceModpack();
                button.setSelected(forceIsolated || Boolean.TRUE.equals(property.getValue()));
                button.setDisable(forceIsolated);
            } finally {
                updating.value = false;
            }
        };

        button.selectedProperty().addListener((observable, oldValue, newValue) -> {
            SettingProperty<Boolean> property = activeProperty.get();
            if (property == null || updating.value || isCurrentInstanceModpack()) {
                return;
            }

            updating.value = true;
            try {
                property.setValue(newValue);
            } finally {
                updating.value = false;
            }
            refresh.invalidated(property);
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue instanceof GameSetting.Instance oldInstance) {
                oldInstance.isolationProperty().removeListener(refresh);
            }

            SettingProperty<Boolean> newProperty = newValue instanceof GameSetting.Instance instance ? instance.isolationProperty() : null;
            activeProperty.set(newProperty);
            if (newValue instanceof GameSetting.Instance instance) {
                instance.isolationProperty().addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        S setting = currentSetting.get();
        if (setting instanceof GameSetting.Instance instance) {
            activeProperty.set(instance.isolationProperty());
            instance.isolationProperty().addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds the working directory choices and keeps isolated instances compatible with the new setting model.
    private void bindGameDirectoryChoiceList(
            ComponentSublist sublist,
            RadioChoiceList<GameDirectoryType> choiceList,
            RadioChoiceList.FileChoice<GameDirectoryType> customOption) {
        ObjectProperty<@Nullable InheritableProperty<GameDirectoryType>> activeTypeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<String>> activeDirProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable SettingProperty<Boolean>> activeIsolationProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<GameDirectoryType> typeProperty = activeTypeProperty.get();
            InheritableProperty<String> dirProperty = activeDirProperty.get();
            SettingProperty<Boolean> isolationProperty = activeIsolationProperty.get();
            if (setting == null || typeProperty == null || dirProperty == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                GameDirectoryType effectiveType = getEffectiveGameDirectoryType(setting);
                choiceList.setSelectedValue(effectiveType);
                customOption.setPath(effectiveType == GameDirectoryType.CUSTOM
                        ? getEffectiveValue(setting, GameSetting::runningDirProperty)
                        : "");
                sublist.setDescription(getEffectiveGameDirectorySubtitle());
                choiceList.setDisable(isCurrentInstanceModpack() ||
                        isolationProperty != null && Boolean.TRUE.equals(isolationProperty.getValue()));
            } finally {
                updating.value = false;
            }
        };

        choiceList.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<GameDirectoryType> typeProperty = activeTypeProperty.get();
            InheritableProperty<String> dirProperty = activeDirProperty.get();
            SettingProperty<Boolean> isolationProperty = activeIsolationProperty.get();
            if (typeProperty == null || dirProperty == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                applyGameDirectorySelection(newValue, customOption.getPath(), typeProperty, dirProperty);
            } finally {
                updating.value = false;
            }
        });

        customOption.pathProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<GameDirectoryType> typeProperty = activeTypeProperty.get();
            InheritableProperty<String> dirProperty = activeDirProperty.get();
            SettingProperty<Boolean> isolationProperty = activeIsolationProperty.get();
            if (typeProperty == null || dirProperty == null || updating.value || choiceList.getSelectedValue() != GameDirectoryType.CUSTOM) {
                return;
            }

            updating.value = true;
            try {
                applyGameDirectorySelection(GameDirectoryType.CUSTOM, newValue, typeProperty, dirProperty);
            } finally {
                updating.value = false;
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            InheritableProperty<GameDirectoryType> oldTypeProperty = activeTypeProperty.get();
            if (oldTypeProperty != null) {
                oldTypeProperty.removeListener(refresh);
            }

            InheritableProperty<String> oldDirProperty = activeDirProperty.get();
            if (oldDirProperty != null) {
                oldDirProperty.removeListener(refresh);
            }

            SettingProperty<Boolean> oldIsolationProperty = activeIsolationProperty.get();
            if (oldIsolationProperty != null) {
                oldIsolationProperty.removeListener(refresh);
            }

            activeTypeProperty.set(newValue != null ? newValue.gameDirTypeProperty() : null);
            activeDirProperty.set(newValue != null ? newValue.runningDirProperty() : null);
            activeIsolationProperty.set(newValue instanceof GameSetting.Instance instance ? instance.isolationProperty() : null);
            if (newValue != null) {
                newValue.addListener(refresh);
                newValue.gameDirTypeProperty().addListener(refresh);
                newValue.runningDirProperty().addListener(refresh);
                if (newValue instanceof GameSetting.Instance instance) {
                    instance.isolationProperty().addListener(refresh);
                }
            }
            refresh.invalidated(newValue);
        });

        S setting = currentSetting.get();
        if (setting != null) {
            activeTypeProperty.set(setting.gameDirTypeProperty());
            activeDirProperty.set(setting.runningDirProperty());
            activeIsolationProperty.set(setting instanceof GameSetting.Instance instance ? instance.isolationProperty() : null);
            setting.addListener(refresh);
            setting.gameDirTypeProperty().addListener(refresh);
            setting.runningDirProperty().addListener(refresh);
            if (setting instanceof GameSetting.Instance instance) {
                instance.isolationProperty().addListener(refresh);
            }
            refresh.invalidated(setting);
        }
    }

    private void applyGameDirectorySelection(
            @Nullable GameDirectoryType type,
            @Nullable String customPath,
            InheritableProperty<GameDirectoryType> typeProperty,
            InheritableProperty<String> dirProperty) {
        GameDirectoryType selectedType = type != null ? type : GameDirectoryType.ROOT_FOLDER;
        if (selectedType == GameDirectoryType.VERSION_FOLDER) {
            typeProperty.setValue(GameDirectoryType.ROOT_FOLDER);
            dirProperty.setValue("");
        } else {
            typeProperty.setValue(selectedType);
            dirProperty.setValue(selectedType == GameDirectoryType.CUSTOM && customPath != null ? customPath : "");
        }
    }

    private GameDirectoryType getEffectiveGameDirectoryType(GameSetting setting) {
        GameDirectoryType type = getEffectiveValue(setting, GameSetting::gameDirTypeProperty);
        return type == GameDirectoryType.VERSION_FOLDER ? GameDirectoryType.ROOT_FOLDER : type;
    }

    private String getEffectiveGameDirectorySubtitle() {
        if (profile == null || instanceId == null) {
            return "";
        }

        return profile.getRepository().getRunDirectory(instanceId).toAbsolutePath().normalize().toString();
    }

    private boolean isCurrentInstanceModpack() {
        return profile != null && instanceId != null && profile.getRepository().isModpack(instanceId);
    }

    /// Binds a radio choice list to an inheritable setting property.
    private <T> void bindInheritableRadioChoiceList(
            ComponentSublist sublist,
            RadioChoiceList<T> item,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<T>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isGlobalSetting) {
            inheritButton = createInheritanceButton();
            sublist.setTitleRight(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener propertyListener = observable -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<T> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                item.setSelectedValue(getEffectiveValue(setting, propertyGetter));
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, property.getValue() == null);
                }
            } finally {
                updating.value = false;
            }
        };

        ChangeListener<@Nullable T> itemListener = (observable, oldValue, newValue) -> {
            InheritableProperty<T> property = activeProperty.get();
            if (property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                property.setValue(newValue);
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, false);
                }
            } finally {
                updating.value = false;
            }
        };

        item.selectedValueProperty().addListener(itemListener);
        if (finalInheritButton != null) {
            finalInheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                InheritableProperty<T> property = activeProperty.get();
                if (setting == null || property == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    if (property.getValue() == null) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                    } else {
                        property.setValue(null);
                    }
                } finally {
                    updating.value = false;
                }
                propertyListener.invalidated(property);
                event.consume();
            });
        }
        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(propertyListener);
            }

            InheritableProperty<T> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(propertyListener);
            }

            InheritableProperty<T> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newValue != null) {
                newValue.addListener(propertyListener);
            }
            if (newProperty != null) {
                newProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newProperty);
        });
        config().getGameSettings().addListener(propertyListener);
        config().defaultGameSettingProperty().addListener(propertyListener);

        S setting = currentSetting.get();
        if (setting != null) {
            InheritableProperty<T> property = propertyGetter.apply(setting);
            activeProperty.set(property);
            setting.addListener(propertyListener);
            property.addListener(propertyListener);
            propertyListener.invalidated(property);
        }
    }

    private <T> void bindInheritableSublistDescription(ComponentSublist sublist,
                                                       Function<GameSetting, InheritableProperty<T>> propertyGetter,
                                                       Function<T, String> converter) {
        InvalidationListener propertyListener = observable -> initInheritableSublistDescription(sublist, propertyGetter, converter);

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                propertyGetter.apply(oldValue).removeListener(propertyListener);
            }

            if (newValue != null) {
                propertyGetter.apply(newValue).addListener(propertyListener);
            }

            initInheritableSublistDescription(sublist, propertyGetter, converter);
        });
        config().getGameSettings().addListener(propertyListener);
        config().defaultGameSettingProperty().addListener(propertyListener);

        S setting = currentSetting.get();
        if (setting != null) {
            propertyGetter.apply(setting).addListener(propertyListener);
        }
        initInheritableSublistDescription(sublist, propertyGetter, converter);
    }

    private <T> void initInheritableSublistDescription(ComponentSublist sublist,
                                                       Function<GameSetting, InheritableProperty<T>> propertyGetter,
                                                       Function<T, String> converter) {
        S setting = currentSetting.get();
        if (setting == null) {
            sublist.setDescription("");
            return;
        }

        sublist.setDescription(converter.apply(getEffectiveValue(setting, propertyGetter)));
    }

    private static String getWindowTypeDisplayName(GameWindowType type) {
        return switch (type) {
            case FULLSCREEN -> "全屏"; // TODO: i18n
            case MAXIMIZED -> "最大化"; // TODO: i18n
            case WINDOWED -> "窗口化"; // TODO: i18n
        };
    }

    /// Manual memory option with the maximum memory slider on the same row.
    private static final class ManualMemoryChoice extends RadioChoiceList.Choice<Boolean> {
        /// The right-side editor used to select maximum memory.
        private final HBox rightNode;

        /// Creates the manual memory option.
        private ManualMemoryChoice(
                JFXSlider maxMemorySlider,
                JFXTextField maxMemoryTextField,
                @Nullable JFXButton inheritButton) {
            super("手动选择内存", false); // TODO: i18n
            this.rightNode = new HBox(8);
            rightNode.setAlignment(Pos.CENTER_RIGHT);
            if (inheritButton != null) {
                radioButton.setGraphic(inheritButton);
                radioButton.setContentDisplay(ContentDisplay.RIGHT);
                radioButton.setGraphicTextGap(4);
            }
            rightNode.getChildren().setAll(
                    maxMemorySlider,
                    maxMemoryTextField,
                    new Label(i18n("settings.memory.unit.mib")));
        }

        /// Creates the right-side memory slider.
        @Override
        protected Node createRightNode() {
            return rightNode;
        }
    }

    /// Windowed game window mode option with the window size selector on the same row.
    private static final class WindowedWindowTypeOption extends RadioChoiceList.Choice<GameWindowType> {
        /// The selector used to edit the initial game window size.
        private final JFXComboBox<String> windowSizeComboBox;

        /// Creates the windowed option.
        private WindowedWindowTypeOption(JFXComboBox<String> windowSizeComboBox) {
            super(getWindowTypeDisplayName(GameWindowType.WINDOWED), GameWindowType.WINDOWED);
            this.windowSizeComboBox = windowSizeComboBox;
        }

        /// Creates the right-side size selector.
        @Override
        protected Node createRightNode() {
            return windowSizeComboBox;
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

    /// Creates a toggle-based editor for a setting property with independent override state.
    private LineInheritableToggleButton createIndependentBooleanButton(
            Function<GameSetting, SettingProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritedText("继承"); // TODO: i18n
        button.setOverriddenText("覆盖"); // TODO: i18n
        button.setInheritTooltip(I18N_INHERIT_GLOBAL_SETTING);
        button.setOverriddenTooltip(I18N_OVERRIDE_GLOBAL_SETTING);
        button.setInheritAvailable(!isGlobalSetting);

        IndependentSettingBinder.bindToggleButton(currentSetting, button, propertyGetter, this::getParentGameSetting);
        return button;
    }

    /// Creates the native directory mode editor with independent override state.
    private LineInheritableToggleButton createIndependentNativesDirTypeButton() {
        var button = new LineInheritableToggleButton();
        button.setInheritedText("继承"); // TODO: i18n
        button.setOverriddenText("覆盖"); // TODO: i18n
        button.setInheritTooltip(I18N_INHERIT_GLOBAL_SETTING);
        button.setOverriddenTooltip(I18N_OVERRIDE_GLOBAL_SETTING);
        button.setInheritAvailable(!isGlobalSetting);

        IndependentSettingBinder.bindNativesDirTypeButton(currentSetting, button, this::getParentGameSetting);
        return button;
    }

    /// Creates a toggle-based inheritable boolean editor that displays the effective value.
    private LineInheritableToggleButton createInheritableBooleanButton(
            Function<GameSetting, InheritableProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritedText("继承"); // TODO: i18n
        button.setOverriddenText("覆盖"); // TODO: i18n
        button.setInheritTooltip(I18N_INHERIT_GLOBAL_SETTING);
        button.setOverriddenTooltip(I18N_OVERRIDE_GLOBAL_SETTING);
        button.setInheritAvailable(!isGlobalSetting);

        bindEffectiveInheritableToggleButton(button, propertyGetter);
        return button;
    }

    /// Binds an inheritable select editor to an inheritable setting.
    private <T> void bindInheritableLineSelectButton(
            LineSelectButton<T> button,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<T>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isGlobalSetting) {
            inheritButton = createInheritanceButton();
            button.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<T> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                button.setValue(getEffectiveValue(setting, propertyGetter));
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, property.getValue() == null);
                }
            } finally {
                updating.value = false;
            }
        };

        button.valueProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<T> property = activeProperty.get();
            if (property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                property.setValue(newValue);
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, false);
                }
            } finally {
                updating.value = false;
            }
        });

        if (finalInheritButton != null) {
            finalInheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                GameSetting setting = currentSetting.get();
                InheritableProperty<T> property = activeProperty.get();
                if (setting == null || property == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    if (property.getValue() == null) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                    } else {
                        property.setValue(null);
                    }
                } finally {
                    updating.value = false;
                }
                refresh.invalidated(property);
                event.consume();
            });
        }

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            InheritableProperty<T> oldProperty = activeProperty.get();
            if (oldProperty != null) {
                oldProperty.removeListener(refresh);
            }

            InheritableProperty<T> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            if (newProperty != null) {
                newProperty.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting != null) {
            activeProperty.set(propertyGetter.apply(setting));
            setting.addListener(refresh);
            activeProperty.get().addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds an inheritable toggle editor to an inheritable boolean setting.
    private void bindEffectiveInheritableToggleButton(
            LineInheritableToggleButton button,
            Function<GameSetting, InheritableProperty<Boolean>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<Boolean>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<Boolean> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                button.setRawValue(property.getValue());
                button.setEffectiveValue(getEffectiveValue(setting, propertyGetter));
            } finally {
                updating.value = false;
            }
        };

        button.rawValueProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<Boolean> property = activeProperty.get();
            GameSetting setting = currentSetting.get();
            if (property == null || setting == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                property.setValue(newValue);
                button.setEffectiveValue(getEffectiveValue(setting, propertyGetter));
            } finally {
                updating.value = false;
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(refresh);
            }

            InheritableProperty<Boolean> newProperty = newValue != null ? propertyGetter.apply(newValue) : null;
            activeProperty.set(newProperty);
            if (newValue != null) {
                newValue.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting != null) {
            activeProperty.set(propertyGetter.apply(setting));
            setting.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Returns the effective value after applying parent inheritance.
    private <T> T getEffectiveValue(
            GameSetting setting,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        InheritableProperty<T> property = propertyGetter.apply(setting);
        @Nullable T value = property.getValue();
        if (value != null) {
            return value;
        }

        if (setting instanceof GameSetting.Instance instance) {
            GameSetting.Global parent = profile != null
                    ? profile.getRepository().getParentGameSetting(instance)
                    : getParentGameSetting(instance);
            InheritableProperty<T> parentProperty = propertyGetter.apply(parent);
            @Nullable T parentValue = parentProperty.getValue();
            return parentValue != null ? parentValue : parentProperty.defaultValue();
        }

        return property.defaultValue();
    }

    /// Returns the setting object that provides the effective inheritable value.
    private <T> GameSetting getEffectiveInheritableSource(
            GameSetting setting,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        if (propertyGetter.apply(setting).getValue() != null || !(setting instanceof GameSetting.Instance instance)) {
            return setting;
        }

        return profile != null
                ? profile.getRepository().getParentGameSetting(instance)
                : getParentGameSetting(instance);
    }

    /// Returns the configured parent global setting for an instance.
    private GameSetting.Global getParentGameSetting(GameSetting.Instance instance) {
        UUID parent = instance.parentProperty().getValue();
        GameSetting.Global parentSetting = config().getGameSetting(parent);
        return parentSetting != null ? parentSetting : config().getDefaultGameSettingOrCreate();
    }

    @SafeVarargs
    private <T extends @UnknownNullability Object> LineSelectButton<T> createInheritableButton(
            Function<GameSetting, InheritableProperty<T>> propertyGetter,
            Function<T, String> convert,
            @Nullable Function<T, String> descriptionConverter,
            T... items
    ) {
        var button = new LineSelectButton<T>();

        button.setNullSafeConverter(convert);
        if (descriptionConverter != null)
            button.setDescriptionConverter(value -> value != null ? descriptionConverter.apply(value) : "");
        button.setItems(items);
        bindInheritableLineSelectButton(button, propertyGetter);

        return button;
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
        GameSetting source = getEffectiveInheritableSource(setting, GameSetting::javaTypeProperty);
        JavaVersionType javaType = getEffectiveValue(setting, GameSetting::javaTypeProperty);
        switch (javaType) {
            case CUSTOM:
                javaCustomOption.setSelected(true);
                javaCustomOption.setPath(source.customJavaPathProperty().getValue());
                break;
            case VERSION:
                javaVersionOption.setSelected(true);
                javaVersionOption.setText(source.javaVersionProperty().getValue());
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
            javaSublist.setDescription(i18n("settings.game.java_directory.auto"));
            return;
        }

        var selectedJava = javaItem.getSelectedValue();
        if (selectedJava != null && selectedJava.getValue() != null) {
            javaSublist.setDescription(selectedJava.getValue().getBinary().toString());
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
                    javaSublist.setDescription(java.getBinary().toString());
                } else {
                    javaSublist.setDescription(autoSelected ? i18n("settings.game.java_directory.auto.not_found") : i18n("settings.game.java_directory.invalid"));
                }
                return;
            } catch (InterruptedException ignored) {
            }
        }

        javaSublist.setDescription("");
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
