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
                ComponentList.createComponentListTitle(i18n("settings.game.section.basic")),
                basicSettings,
                ComponentList.createComponentListTitle(i18n("settings.game.section.game")),
                gameSettings,
                ComponentList.createComponentListTitle(i18n("settings.launcher")),
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
                parentGameSettingPane.setTitle(i18n("settings.type.global.manage"));
                parentGameSettingPane.setConverter(setting -> setting != null
                        ? setting.nameProperty().getValue()
                        : i18n("settings.type.global.default"));
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
                    javaCustomOption.addExtensionFilter(new FileChooser.ExtensionFilter(i18n("settings.game.java_directory"), "java.exe"));

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
                        setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                        setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
                        setting.customJavaPathProperty().setValue(javaCustomOption.getPath());
                        setting.javaVersionProperty().setValue("");
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (javaAutoDeterminedOption.isSelected()) {
                        setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                        setting.javaTypeProperty().setValue(JavaVersionType.AUTO);
                        setting.javaVersionProperty().setValue("");
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (javaVersionOption.isSelected()) {
                        setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                        setting.javaTypeProperty().setValue(JavaVersionType.VERSION);
                        setting.javaVersionProperty().setValue(javaVersionOption.getText());
                        setting.defaultJavaPathProperty().setValue("");
                    } else if (newChoice != null) {
                        @Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime> selectedJava = newChoice.getValue();
                        if (selectedJava != null
                                && selectedJava.getKey() == JavaVersionType.DETECTED
                                && selectedJava.getValue() != null) {
                            JavaRuntime java = selectedJava.getValue();
                            setPropertyOverridden(setting, setting.javaTypeProperty(), true);
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
                    setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                    setting.javaTypeProperty().setValue(JavaVersionType.VERSION);
                    setting.javaVersionProperty().setValue(newValue);
                    initJavaSubtitle();
                }
            });

            javaCustomOption.pathProperty().addListener((observable, oldValue, newValue) -> {
                S setting = currentSetting.get();
                if (setting != null && javaCustomOption.isSelected() && !updatingSelectedJava) {
                    setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                    setting.javaTypeProperty().setValue(JavaVersionType.CUSTOM);
                    setting.customJavaPathProperty().setValue(newValue);
                    initJavaSubtitle();
                }
            });

            // Isolation Setting
            if (isGlobalSetting) {
                var defaultIsolationTypePane = new LineSelectButton<DefaultIsolationType>();
                basicSettings.getContent().add(defaultIsolationTypePane);
                defaultIsolationTypePane.setTitle(i18n("settings.game.default_isolation"));
                defaultIsolationTypePane.setItems(DefaultIsolationType.values());
                defaultIsolationTypePane.setNullSafeConverter(type -> switch (type) {
                    case NEVER -> i18n("settings.game.default_isolation.never");
                    case ALWAYS -> i18n("settings.game.default_isolation.always");
                    case MODED -> i18n("settings.game.default_isolation.modded");
                });

                bindGlobalSettingBidirectional(defaultIsolationTypePane.valueProperty(), GameSetting.Global::defaultIsolationTypeProperty);
            } else {
                var isolationButton = new LineToggleButton();
                basicSettings.getContent().add(isolationButton);
                isolationButton.setTitle(i18n("settings.game.isolation"));
                isolationButton.setSubtitle(i18n("settings.game.isolation.subtitle"));
                bindInstanceIsolationButton(isolationButton);
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
            windowTypeSublist.setTitle(i18n("settings.game.window_type"));
            windowTypeSublist.setHasSubtitle(true);
            {
                var windowTypeItem = new RadioChoiceList<GameWindowType>();
                var windowTypeOptions = new ArrayList<RadioChoiceList.Choice<GameWindowType>>();
                windowTypeItem.setFallbackValue(GameWindowType.WINDOWED);

                var cboWindowSize = new JFXComboBox<String>();
                cboWindowSize.setPrefWidth(150);
                cboWindowSize.setEditable(true);
                cboWindowSize.setPromptText("854x480");
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

                var noneOption = new RadioChoiceList.Choice<>(i18n("settings.game.quick_play.none"), QuickPlayType.NONE);

                var multiplayerOption = new RadioChoiceList.TextChoice<>(
                        i18n("settings.game.quick_play.multiplayer"), QuickPlayType.MULTIPLAYER);
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

                var singleplayerOption = new RadioChoiceList.TextChoice<>(
                        i18n("settings.game.quick_play.singleplayer"), QuickPlayType.SINGLEPLAYER);
                singleplayerOption.setValidators(new Validator(str -> {
                    if (StringUtils.isBlank(str))
                        return true;
                    return FileUtils.isNameValid(str);
                }));

                var realmsOption = new RadioChoiceList.TextChoice<>(
                        i18n("settings.game.quick_play.realms"), QuickPlayType.REALMS);

                quickPlayItem.setFallbackValue(QuickPlayType.NONE);
                quickPlayItem.setChoices(List.of(
                        noneOption,
                        multiplayerOption,
                        singleplayerOption,
                        realmsOption
                ));

                bindInheritableRadioChoiceList(quickSublist, quickPlayItem, GameSetting::quickPlayProperty);
                bindInheritableStringValue(multiplayerOption.textProperty(), GameSetting::quickPlayMultiplayerProperty);
                bindInheritableStringValue(singleplayerOption.textProperty(), GameSetting::quickPlaySingleplayerProperty);
                bindInheritableStringValue(realmsOption.textProperty(), GameSetting::quickPlayRealmsProperty);
                quickSublist.getContent().setAll(quickPlayItem);
            }
            gameSettings.getContent().add(quickSublist);
            quickSublist.setTitle(i18n("settings.game.quick_play"));
            quickSublist.setSubtitle(i18n("settings.game.quick_play.subtitle"));
            quickSublist.setHasSubtitle(true);

            var advancedLaunchSublist = new ComponentSublist(() -> {
                var runningDirPane = new LinePane();
                runningDirPane.setTitle(i18n("settings.game.running_directory"));
                runningDirPane.setSubtitle(i18n(isGlobalSetting
                        ? "settings.game.running_directory.subtitle"
                        : "settings.game.running_directory.subtitle.instance"));
                {
                    var runningDirSelector = new FileSelector()
                            .setChooserTitle(i18n("settings.game.working_directory.choose"))
                            .setSelectionMode(FileSelector.SelectionMode.DIRECTORY);
                    runningDirSelector.setPrefWidth(400);
                    runningDirPane.setRight(runningDirSelector);
                    bindRunningDirectoryProperty(runningDirPane, runningDirSelector.valueProperty(), runningDirSelector);
                }

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
                environmentVariablesPane.setTitle(i18n("settings.advanced.environment_variables"));
                environmentVariablesPane.setSubtitle(i18n("settings.advanced.environment_variables.subtitle"));
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

                return List.of(runningDirPane, gameArgsPane, environmentVariablesPane, processPriorityPane);
            });
            gameSettings.getContent().add(advancedLaunchSublist);
            advancedLaunchSublist.setTitle(i18n("settings.advanced.launch_options"));
            advancedLaunchSublist.setSubtitle(i18n("settings.advanced.launch_options.subtitle"));
            advancedLaunchSublist.setHasSubtitle(true);
        }

        var jvmSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced.jvm")),
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
                    minMemoryPane.setRight(new HBox(8, txtMinMemory, new Label(i18n("settings.memory.unit.mib"))));
                    bindIndependentIntegerTextField(minMemoryPane, txtMinMemory, GameSetting::minMemoryProperty);
                }

                var metaspacePane = new LinePane();
                metaspacePane.setTitle(i18n("settings.advanced.java_permanent_generation_space"));
                {
                    var txtMetaspace = new JFXTextField();
                    txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
                    txtMetaspace.setPrefWidth(160);
                    metaspacePane.setRight(new HBox(8, txtMetaspace, new Label(i18n("settings.memory.unit.mib"))));
                    bindIndependentTextField(metaspacePane, txtMetaspace, GameSetting::permSizeProperty);
                }

                return List.of(minMemoryPane, metaspacePane);
            });
            jvmSettings.getContent().add(deprecatedJvmMemorySettings);
            deprecatedJvmMemorySettings.setTitle(i18n("settings.advanced.jvm_memory.deprecated"));
            deprecatedJvmMemorySettings.setHasSubtitle(true);
            deprecatedJvmMemorySettings.setSubtitle(i18n("settings.advanced.jvm_memory.deprecated.subtitle"));
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
                ComponentList.createComponentListTitle(i18n("settings.advanced.graphics")),
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
            openGLRendererPane.setTitle(i18n("settings.advanced.renderer.opengl"));

            var vulkanRendererPane = createInheritableButton(
                    GameSetting::vulkanRendererProperty,
                    e -> i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT)),
                    e -> {
                        String bundleKey = "settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                        return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                    },
                    Renderer.getSupported(GraphicsAPI.VULKAN).toArray(Renderer[]::new));
            graphicsSettings.getContent().add(vulkanRendererPane);
            vulkanRendererPane.setTitle(i18n("settings.advanced.renderer.vulkan"));

        }

        var nativeLibrarySettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced.natives_settings")),
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
        listButton.setTitle(i18n("settings.type.global.manage_all"));
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
            updateInheritanceButton(button, setting == null || !isPropertyOverridden(setting, setting.javaTypeProperty()));
        };

        button.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            S setting = currentSetting.get();
            if (setting == null) {
                return;
            }

            updatingJavaSetting = true;
            try {
                if (!isPropertyOverridden(setting, setting.javaTypeProperty())) {
                    GameSetting source = getEffectiveInheritableSource(setting, GameSetting::javaTypeProperty);
                    setting.javaTypeProperty().setValue(getEffectiveValue(setting, GameSetting::javaTypeProperty));
                    setting.javaVersionProperty().setValue(source.javaVersionProperty().getValue());
                    setting.customJavaPathProperty().setValue(source.customJavaPathProperty().getValue());
                    setting.defaultJavaPathProperty().setValue(source.defaultJavaPathProperty().getValue());
                    setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                } else {
                    setPropertyOverridden(setting, setting.javaTypeProperty(), false);
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
                S setting = currentSetting.get();
                Double width = setting != null ? getEffectiveValue(setting, GameSetting::widthProperty) : widthProperty.getValue();
                Double height = setting != null ? getEffectiveValue(setting, GameSetting::heightProperty) : heightProperty.getValue();
                comboBox.setValue(isSpecifiedWindowSize(width, height) ? formatWindowSize(width, height) : null);
            } finally {
                updating.value = false;
            }
        };

        ChangeListener<@Nullable Boolean> focusedListener = (observable, oldValue, newValue) -> {
            if (!newValue) {
                applyWindowSizeComboBoxValue(
                        comboBox,
                        currentSetting.get(),
                        activeWidthProperty.get(),
                        activeHeightProperty.get(),
                        updating);
            }
        };

        ChangeListener<@Nullable Scene> sceneListener = (observable, oldValue, newValue) -> {
            if (newValue == null) {
                applyWindowSizeComboBoxValue(
                        comboBox,
                        currentSetting.get(),
                        activeWidthProperty.get(),
                        activeHeightProperty.get(),
                        updating);
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
                                                     @Nullable GameSetting setting,
                                                     @Nullable Property<Double> widthProperty,
                                                     @Nullable Property<Double> heightProperty,
                                                     Holder<Boolean> updating) {
        if (widthProperty == null || heightProperty == null || updating.value) {
            return;
        }

        updating.value = true;
        try {
            setWindowSizeOverridden(setting, widthProperty, heightProperty);
            String value = comboBox.getValue();
            if (StringUtils.isBlank(value)) {
                widthProperty.setValue(0.0);
                heightProperty.setValue(0.0);
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

    private static void setWindowSizeOverridden(
            @Nullable GameSetting setting,
            Property<Double> widthProperty,
            Property<Double> heightProperty) {
        if (setting == null) {
            return;
        }
        if (widthProperty instanceof SettingProperty<?> property) {
            setPropertyOverridden(setting, property, true);
        }
        if (heightProperty instanceof SettingProperty<?> property) {
            setPropertyOverridden(setting, property, true);
        }
    }

    private static boolean isSpecifiedWindowSize(@Nullable Double width, @Nullable Double height) {
        return width != null && height != null && width > 0 && height > 0;
    }

    private static @Nullable String formatNullableWindowSize(@Nullable Double width, @Nullable Double height) {
        return isSpecifiedWindowSize(width, height) ? formatWindowSize(width, height) : null;
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
            inheritTooltip.setText(i18n(inherited
                    ? "settings.game.inherit_global"
                    : "settings.game.override_global"));
        }
    }

    /// Returns whether the setting uses its direct property value.
    private static boolean isPropertyOverridden(GameSetting setting, SettingProperty<?> property) {
        return !(setting instanceof GameSetting.Instance instance)
                || instance.getOverrideProperties().contains(property.getName());
    }

    /// Updates whether an instance setting uses its direct property value.
    private static void setPropertyOverridden(GameSetting setting, SettingProperty<?> property, boolean overridden) {
        if (!(setting instanceof GameSetting.Instance instance)) {
            return;
        }

        if (overridden) {
            instance.getOverrideProperties().add(property.getName());
        } else {
            instance.getOverrideProperties().remove(property.getName());
        }
    }

    /// Returns the direct property value, falling back to the property's default value.
    private static <T> T getDirectValue(SettingProperty<T> property) {
        T value = property.getValue();
        return value != null ? value : property.defaultValue();
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
        bindInheritableStringProperty(line, textField.textProperty(), propertyGetter);
    }

    /// Binds a string property to an inheritable string setting.
    private void bindInheritableStringProperty(
            LineComponent line,
            Property<String> textProperty,
            Function<GameSetting, InheritableProperty<String>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<String>> activeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        final Holder<InvalidationListener> refreshHolder = new Holder<>();
        @Nullable JFXButton inheritButton = null;
        if (!isGlobalSetting) {
            inheritButton = createInheritanceButton();
            line.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            updateParentInheritablePropertyListener(setting, activeParentProperty, propertyGetter, refreshHolder.value);
            InheritableProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                textProperty.setValue(getEffectiveValue(setting, propertyGetter));
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, !isPropertyOverridden(setting, property));
                }
            } finally {
                updating.value = false;
            }
        };
        refreshHolder.value = refresh;

        textProperty.addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setPropertyOverridden(setting, property, true);
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
                    if (!isPropertyOverridden(setting, property)) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                        setPropertyOverridden(setting, property, true);
                    } else {
                        setPropertyOverridden(setting, property, false);
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

    /// Binds a string value to an inheritable setting without adding a separate inheritance button.
    private void bindInheritableStringValue(
            Property<String> textProperty,
            Function<GameSetting, InheritableProperty<String>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<String>> activeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        final Holder<InvalidationListener> refreshHolder = new Holder<>();

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            updateParentInheritablePropertyListener(setting, activeParentProperty, propertyGetter, refreshHolder.value);
            InheritableProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                textProperty.setValue(getEffectiveValue(setting, propertyGetter));
            } finally {
                updating.value = false;
            }
        };
        refreshHolder.value = refresh;

        textProperty.addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<String> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setPropertyOverridden(setting, property, true);
                property.setValue(newValue != null ? newValue : "");
            } finally {
                updating.value = false;
            }
        });

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
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            S setting = currentSetting.get();
            if (!(setting instanceof GameSetting.Instance instance) || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean forceIsolated = isCurrentInstanceModpack();
                button.setSelected(forceIsolated
                        || instance.getOverrideProperties().contains(GameSetting.PROPERTY_RUNNING_DIR));
                button.setDisable(forceIsolated);
            } finally {
                updating.value = false;
            }
        };

        button.selectedProperty().addListener((observable, oldValue, newValue) -> {
            S setting = currentSetting.get();
            if (!(setting instanceof GameSetting.Instance instance) || updating.value || isCurrentInstanceModpack()) {
                return;
            }

            updating.value = true;
            try {
                if (newValue) {
                    instance.runningDirProperty().setValue("");
                    instance.getOverrideProperties().add(GameSetting.PROPERTY_RUNNING_DIR);
                } else {
                    instance.getOverrideProperties().remove(GameSetting.PROPERTY_RUNNING_DIR);
                }
            } finally {
                updating.value = false;
            }
            refresh.invalidated(instance);
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue instanceof GameSetting.Instance oldInstance) {
                oldInstance.removeListener(refresh);
            }

            if (newValue instanceof GameSetting.Instance instance) {
                instance.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        S setting = currentSetting.get();
        if (setting instanceof GameSetting.Instance instance) {
            instance.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds the running directory editor to the source selected by version isolation.
    private void bindRunningDirectoryProperty(
            LineComponent line,
            Property<String> textProperty,
            Node editor) {
        if (isGlobalSetting) {
            bindInheritableStringProperty(line, textProperty, GameSetting::runningDirProperty);
            return;
        }

        final Holder<Boolean> updating = new Holder<>(false);
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<InvalidationListener> refreshHolder = new Holder<>();
        JFXButton inheritButton = createInheritanceButton();
        line.setTitleTrailing(inheritButton);

        InvalidationListener refresh = observable -> {
            GameSetting setting = currentSetting.get();
            updateParentInheritablePropertyListener(setting, activeParentProperty, GameSetting::runningDirProperty, refreshHolder.value);
            if (!(setting instanceof GameSetting.Instance instance) || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean useInstanceRunningDirectory = isCurrentInstanceModpack()
                        || instance.getOverrideProperties().contains(GameSetting.PROPERTY_RUNNING_DIR);
                String runningDirectory;
                if (useInstanceRunningDirectory) {
                    String value = instance.runningDirProperty().getValue();
                    runningDirectory = value != null ? value : instance.runningDirProperty().defaultValue();
                } else {
                    runningDirectory = getParentValue(instance, GameSetting::runningDirProperty);
                }

                textProperty.setValue(runningDirectory);
                editor.setDisable(!useInstanceRunningDirectory);
                updateInheritanceButton(inheritButton, !useInstanceRunningDirectory);
                inheritButton.setDisable(isCurrentInstanceModpack());
            } finally {
                updating.value = false;
            }
        };
        refreshHolder.value = refresh;

        inheritButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            GameSetting setting = currentSetting.get();
            if (!(setting instanceof GameSetting.Instance instance) || updating.value || isCurrentInstanceModpack()) {
                return;
            }

            updating.value = true;
            try {
                if (instance.getOverrideProperties().contains(GameSetting.PROPERTY_RUNNING_DIR)) {
                    instance.getOverrideProperties().remove(GameSetting.PROPERTY_RUNNING_DIR);
                } else {
                    instance.runningDirProperty().setValue("");
                    instance.getOverrideProperties().add(GameSetting.PROPERTY_RUNNING_DIR);
                }
            } finally {
                updating.value = false;
            }
            refresh.invalidated(instance);
            event.consume();
        });

        textProperty.addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            if (!(setting instanceof GameSetting.Instance instance)
                    || updating.value
                    || (!isCurrentInstanceModpack()
                            && !instance.getOverrideProperties().contains(GameSetting.PROPERTY_RUNNING_DIR))) {
                return;
            }

            updating.value = true;
            try {
                instance.getOverrideProperties().add(GameSetting.PROPERTY_RUNNING_DIR);
                instance.runningDirProperty().setValue(newValue != null ? newValue : "");
            } finally {
                updating.value = false;
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue instanceof GameSetting.Instance oldInstance) {
                oldInstance.removeListener(refresh);
            }

            if (newValue instanceof GameSetting.Instance newInstance) {
                newInstance.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        config().getGameSettings().addListener(refresh);
        config().defaultGameSettingProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting instanceof GameSetting.Instance instance) {
            instance.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    private boolean isCurrentInstanceModpack() {
        return profile != null && instanceId != null && profile.getRepository().isModpack(instanceId);
    }

    /// Keeps a listener attached to the current instance's parent global property.
    private <T> void updateParentInheritablePropertyListener(
            @Nullable GameSetting setting,
            ObjectProperty<@Nullable InheritableProperty<T>> activeParentProperty,
            Function<GameSetting, InheritableProperty<T>> propertyGetter,
            InvalidationListener listener) {
        InheritableProperty<T> oldParentProperty = activeParentProperty.get();
        InheritableProperty<T> newParentProperty = setting instanceof GameSetting.Instance instance
                ? propertyGetter.apply(getParentGameSetting(instance))
                : null;
        if (oldParentProperty == newParentProperty) {
            return;
        }

        if (oldParentProperty != null) {
            oldParentProperty.removeListener(listener);
        }
        activeParentProperty.set(newParentProperty);
        if (newParentProperty != null) {
            newParentProperty.addListener(listener);
        }
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
                    updateInheritanceButton(finalInheritButton, !isPropertyOverridden(setting, property));
                }
            } finally {
                updating.value = false;
            }
        };

        ChangeListener<@Nullable T> itemListener = (observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<T> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setPropertyOverridden(setting, property, true);
                property.setValue(newValue != null ? newValue : property.defaultValue());
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
                    if (!isPropertyOverridden(setting, property)) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                        setPropertyOverridden(setting, property, true);
                    } else {
                        setPropertyOverridden(setting, property, false);
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
            case FULLSCREEN -> i18n("settings.game.window_type.fullscreen");
            case MAXIMIZED -> i18n("settings.game.window_type.maximized");
            case WINDOWED -> i18n("settings.game.window_type.windowed");
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
            super(i18n("settings.memory.manual_allocate"), false);
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
    private <T> void bindGlobalSettingBidirectional(Property<T> property, Function<GameSetting.Global, Property<T>> propertyGetter) {
        assert isGlobalSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    /// Creates a toggle-based editor for a setting property with independent override state.
    private LineInheritableToggleButton createIndependentBooleanButton(
            Function<GameSetting, SettingProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritedText(i18n("settings.game.inherit"));
        button.setOverriddenText(i18n("settings.game.override"));
        button.setInheritTooltip(i18n("settings.game.inherit_global"));
        button.setOverriddenTooltip(i18n("settings.game.override_global"));
        button.setInheritAvailable(!isGlobalSetting);

        IndependentSettingBinder.bindToggleButton(currentSetting, button, propertyGetter, this::getParentGameSetting);
        return button;
    }

    /// Creates the native directory mode editor with independent override state.
    private LineInheritableToggleButton createIndependentNativesDirTypeButton() {
        var button = new LineInheritableToggleButton();
        button.setInheritedText(i18n("settings.game.inherit"));
        button.setOverriddenText(i18n("settings.game.override"));
        button.setInheritTooltip(i18n("settings.game.inherit_global"));
        button.setOverriddenTooltip(i18n("settings.game.override_global"));
        button.setInheritAvailable(!isGlobalSetting);

        IndependentSettingBinder.bindNativesDirTypeButton(currentSetting, button, this::getParentGameSetting);
        return button;
    }

    /// Creates a toggle-based inheritable boolean editor that displays the effective value.
    private LineInheritableToggleButton createInheritableBooleanButton(
            Function<GameSetting, InheritableProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritedText(i18n("settings.game.inherit"));
        button.setOverriddenText(i18n("settings.game.override"));
        button.setInheritTooltip(i18n("settings.game.inherit_global"));
        button.setOverriddenTooltip(i18n("settings.game.override_global"));
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
                    updateInheritanceButton(finalInheritButton, !isPropertyOverridden(setting, property));
                }
            } finally {
                updating.value = false;
            }
        };

        button.valueProperty().addListener((observable, oldValue, newValue) -> {
            GameSetting setting = currentSetting.get();
            InheritableProperty<T> property = activeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setPropertyOverridden(setting, property, true);
                property.setValue(newValue != null ? newValue : property.defaultValue());
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
                    if (!isPropertyOverridden(setting, property)) {
                        property.setValue(getEffectiveValue(setting, propertyGetter));
                        setPropertyOverridden(setting, property, true);
                    } else {
                        setPropertyOverridden(setting, property, false);
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
                boolean overridden = isPropertyOverridden(setting, property);
                button.setRawValue(overridden ? getDirectValue(property) : getEffectiveValue(setting, propertyGetter));
                button.setOverridden(overridden);
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
                setPropertyOverridden(setting, property, true);
                property.setValue(newValue);
                button.setEffectiveValue(getEffectiveValue(setting, propertyGetter));
            } finally {
                updating.value = false;
            }
        });

        button.overriddenProperty().addListener((observable, oldValue, newValue) -> {
            InheritableProperty<Boolean> property = activeProperty.get();
            GameSetting setting = currentSetting.get();
            if (property == null || setting == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setPropertyOverridden(setting, property, newValue);
                if (newValue) {
                    property.setValue(button.getRawValue());
                }
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
        if (isPropertyOverridden(setting, property)) {
            return getDirectValue(property);
        }

        if (setting instanceof GameSetting.Instance instance) {
            return getParentValue(instance, propertyGetter);
        }

        return getDirectValue(property);
    }

    /// Returns the value provided by an instance's parent global setting.
    private <T> T getParentValue(
            GameSetting.Instance instance,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        GameSetting.Global parent = profile != null
                ? profile.getRepository().getParentGameSetting(instance)
                : getParentGameSetting(instance);
        return getDirectValue(propertyGetter.apply(parent));
    }

    /// Returns the setting object that provides the effective inheritable value.
    private <T> GameSetting getEffectiveInheritableSource(
            GameSetting setting,
            Function<GameSetting, InheritableProperty<T>> propertyGetter) {
        if (isPropertyOverridden(setting, propertyGetter.apply(setting)) || !(setting instanceof GameSetting.Instance instance)) {
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
