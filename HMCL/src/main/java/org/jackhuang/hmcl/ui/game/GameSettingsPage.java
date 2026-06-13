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
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
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
import java.util.Objects;
import java.util.function.Function;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
@NotNullByDefault
public final class GameSettingsPage<S extends GameSettings> extends StackPane
        implements DecoratorPage, VersionPage.VersionLoadable, PageAware {

    private static final Object INHERIT_BUTTON_TOOLTIP_KEY = new Object();
    private static final PseudoClass PSEUDO_OVERRIDDEN = PseudoClass.getPseudoClass("overridden");
    private static final String INHERIT_BUTTON_STYLE_CLASS = "toggle-icon-tiny";
    private static final int INHERIT_BUTTON_ICON_SIZE = 12;

    private final boolean isPresetSetting;

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
    private final HintPane readOnlySettingsHint;

    private final @UnknownNullability ImagePickerItem iconPickerItem;

    private final ComponentSublist javaSublist;
    private final RadioChoiceList<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaItem;
    private final RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaAutoDeterminedOption;
    private final RadioChoiceList.TextChoice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaVersionOption;
    private final RadioChoiceList.FileChoice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> javaCustomOption;
    private final InvalidationListener javaListener = o -> initializeSelectedJava();

    public GameSettingsPage(Class<S> settingType) {
        assert settingType == GameSettings.Preset.class || settingType == GameSettings.Instance.class;

        this.isPresetSetting = settingType == GameSettings.Preset.class;

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

        readOnlySettingsHint = new HintPane(MessageDialogPane.MessageType.WARNING);
        readOnlySettingsHint.setVisible(false);
        readOnlySettingsHint.setManaged(false);
        rootPane.getChildren().add(readOnlySettingsHint);

        var basicSettings = new ComponentList();
        var gameSettings = new ComponentList();
        var launcherSettings = new ComponentList();
        {
            if (isPresetSetting) {
                var presetSettings = new ComponentList();
                rootPane.getChildren().addAll(
                        ComponentList.createComponentListTitle(i18n("settings.type.global.preset")),
                        presetSettings,
                        ComponentList.createComponentListTitle(i18n("settings.game.section.basic")),
                        basicSettings,
                        ComponentList.createComponentListTitle(i18n("settings.game.section.game")),
                        gameSettings,
                        ComponentList.createComponentListTitle(i18n("settings.launcher")),
                        launcherSettings
                );

                iconPickerItem = null;
                presetSettings.getContent().add(new PresetManagementPane(currentSetting, this::selectPreset, holder));
            } else {
                rootPane.getChildren().addAll(
                        ComponentList.createComponentListTitle(i18n("settings.game.section.basic")),
                        basicSettings,
                        ComponentList.createComponentListTitle(i18n("settings.game.section.game")),
                        gameSettings,
                        ComponentList.createComponentListTitle(i18n("settings.launcher")),
                        launcherSettings
                );

                iconPickerItem = new ImagePickerItem();
                basicSettings.getContent().add(iconPickerItem);
                iconPickerItem.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
                iconPickerItem.setTitle(i18n("settings.icon"));
                iconPickerItem.setOnSelectButtonClicked(e -> onExploreIcon());
                iconPickerItem.setOnDeleteButtonClicked(e -> onDeleteIcon());

                var parentGameSettingsPane = new LineSelectButton<GameSettings.@Nullable Preset>();
                basicSettings.getContent().add(parentGameSettingsPane);
                parentGameSettingsPane.setTitle(i18n("settings.type.global.preset"));
                parentGameSettingsPane.setConverter(setting -> setting != null
                        ? PresetManagementPane.getPresetDisplayName(setting)
                        : getImplicitParentGameSettingsDisplayName());
                bindInstanceParentSetting(parentGameSettingsPane);
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
                    oldSetting.detectedJavaProperty().removeListener(javaListener);
                    oldSetting.customJavaPathProperty().removeListener(javaListener);
                    oldSetting.customJavaVersionProperty().removeListener(javaListener);
                }

                if (newSetting != null) {
                    newSetting.javaTypeProperty().addListener(javaListener);
                    newSetting.detectedJavaProperty().addListener(javaListener);
                    newSetting.customJavaPathProperty().addListener(javaListener);
                    newSetting.customJavaVersionProperty().addListener(javaListener);
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
                    } else if (javaAutoDeterminedOption.isSelected()) {
                        setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                        setting.javaTypeProperty().setValue(JavaVersionType.AUTO);
                    } else if (javaVersionOption.isSelected()) {
                        setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                        setting.javaTypeProperty().setValue(JavaVersionType.VERSION);
                        setting.customJavaVersionProperty().setValue(javaVersionOption.getText());
                    } else if (newChoice != null) {
                        @Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime> selectedJava = newChoice.getValue();
                        if (selectedJava != null
                                && selectedJava.getKey() == JavaVersionType.DETECTED
                                && selectedJava.getValue() != null) {
                            JavaRuntime java = selectedJava.getValue();
                            setPropertyOverridden(setting, setting.javaTypeProperty(), true);
                            setting.javaTypeProperty().setValue(JavaVersionType.DETECTED);
                            setting.detectedJavaProperty().setValue(GameSettings.DetectedJava.of(java));
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
                    setting.customJavaVersionProperty().setValue(newValue);
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
            if (isPresetSetting) {
                var defaultIsolationTypePane = new LineSelectButton<DefaultIsolationType>();
                basicSettings.getContent().add(defaultIsolationTypePane);
                defaultIsolationTypePane.setTitle(i18n("settings.game.default_isolation"));
                defaultIsolationTypePane.setSubtitle(i18n("settings.game.default_isolation.subtitle"));
                defaultIsolationTypePane.setItems(DefaultIsolationType.values());
                defaultIsolationTypePane.setNullSafeConverter(type -> switch (type) {
                        case NEVER -> i18n("settings.game.default_isolation.never");
                        case ALWAYS -> i18n("settings.game.default_isolation.always");
                        case MODDED -> i18n("settings.game.default_isolation.modded");
                    });
                defaultIsolationTypePane.setDescriptionConverter(type -> switch (type) {
                        case NEVER -> i18n("settings.game.default_isolation.never.desc");
                        case ALWAYS -> i18n("settings.game.default_isolation.always.desc");
                        case MODDED -> i18n("settings.game.default_isolation.modded.desc");
                    });
                bindPresetBidirectional(defaultIsolationTypePane.valueProperty(), GameSettings.Preset::defaultIsolationTypeProperty);
            } else {
                var isolationButton = new LineToggleButton();
                basicSettings.getContent().add(isolationButton);
                isolationButton.setTitle(i18n("settings.game.isolation"));
                isolationButton.setSubtitle(i18n("settings.game.isolation.subtitle"));
                bindInstanceIsolationButton(isolationButton);
            }

            // Memory Setting
            @Nullable JFXButton autoMemoryButton = !isPresetSetting ? createInheritanceButton() : null;
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
                if (!isPresetSetting) {
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
                        GameSettingsPage::updateInheritanceButton,
                        this::getEffectiveParentGameSettings);

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
                    GameSettings::launcherVisibilityProperty,
                    value -> i18n("settings.advanced.launcher_visibility." + value.name().toLowerCase(Locale.ROOT)),
                    null,
                    LauncherVisibility.values()
            );
            launcherSettings.getContent().add(launcherVisibilityPane);
            launcherVisibilityPane.setTitle(i18n("settings.advanced.launcher_visible"));

            var allowAutoAgentPane = createInheritableBooleanButton(GameSettings::allowAutoAgentProperty);
            launcherSettings.getContent().add(allowAutoAgentPane);
            allowAutoAgentPane.setTitle(i18n("settings.launcher.allow_auto_agent"));
            allowAutoAgentPane.setSubtitle(i18n("settings.launcher.allow_auto_agent.subtitle"));

            var disableAutoGameOptionsPane = createInheritableBooleanButton(GameSettings::disableAutoGameOptionsProperty);
            launcherSettings.getContent().add(disableAutoGameOptionsPane);
            disableAutoGameOptionsPane.setTitle(i18n("settings.launcher.disable_auto_game_options"));

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
                bindWindowSettings(windowTypeSublist, windowTypeItem);
                bindInheritableSublistDescription(
                        windowTypeSublist,
                        GameSettings::windowTypeProperty,
                        GameSettingsPage::getWindowTypeDisplayName);
            }

            // Show Logs Window Setting
            var showLogsPane = createInheritableBooleanButton(GameSettings::showLogsProperty);
            launcherSettings.getContent().add(showLogsPane);
            showLogsPane.setTitle(i18n("settings.show_log"));

            // Enable Debug Log Output Setting
            var enableDebugLogOutputPane = createInheritableBooleanButton(GameSettings::enableDebugLogOutputProperty);
            launcherSettings.getContent().add(enableDebugLogOutputPane);
            enableDebugLogOutputPane.setTitle(i18n("settings.enable_debug_log_output"));

            var noGameCheckPane = createInheritableBooleanButton(GameSettings::notCheckGameProperty);
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

                bindInheritableRadioChoiceList(quickSublist, quickPlayItem, GameSettings::quickPlayProperty);
                bindInheritableStringValue(multiplayerOption.textProperty(), GameSettings::quickPlayMultiplayerProperty);
                bindInheritableStringValue(singleplayerOption.textProperty(), GameSettings::quickPlaySingleplayerProperty);
                bindInheritableStringValue(realmsOption.textProperty(), GameSettings::quickPlayRealmsProperty);
                quickSublist.getContent().setAll(quickPlayItem);
            }
            gameSettings.getContent().add(quickSublist);
            quickSublist.setTitle(i18n("settings.game.quick_play"));
            quickSublist.setSubtitle(i18n("settings.game.quick_play.subtitle"));
            quickSublist.setHasSubtitle(true);

            var advancedLaunchSublist = new ComponentSublist(() -> {
                var runningDirPane = new LinePane();
                runningDirPane.setTitle(i18n("settings.game.running_directory"));
                runningDirPane.setSubtitle(i18n(isPresetSetting
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
                    bindIndependentTextField(gameArgsPane, txtGameArgs, GameSettings::gameArgumentsProperty);
                }

                var environmentVariablesPane = new LinePane();
                environmentVariablesPane.setTitle(i18n("settings.advanced.environment_variables"));
                environmentVariablesPane.setSubtitle(i18n("settings.advanced.environment_variables.subtitle"));
                {
                    var txtEnvironmentVariables = new JFXTextField();
                    txtEnvironmentVariables.setPrefWidth(400);
                    environmentVariablesPane.setRight(txtEnvironmentVariables);
                    bindIndependentTextField(environmentVariablesPane, txtEnvironmentVariables, GameSettings::environmentVariablesProperty);
                }

                var processPriorityPane = createInheritableButton(
                        GameSettings::processPriorityProperty,
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
            var noJVMArgsPane = createInheritableBooleanButton(GameSettings::noJVMOptionsProperty);
            jvmSettings.getContent().add(noJVMArgsPane);
            noJVMArgsPane.setTitle(i18n("settings.advanced.no_jvm_args"));

            var noOptimizingJVMArgsPane = createInheritableBooleanButton(GameSettings::noOptimizingJVMOptionsProperty);
            jvmSettings.getContent().add(noOptimizingJVMArgsPane);
            noOptimizingJVMArgsPane.setTitle(i18n("settings.advanced.no_optimizing_jvm_args"));
            noOptimizingJVMArgsPane.disableProperty().bind(noJVMArgsPane.effectiveValueProperty());

            var noJVMCheckPane = createInheritableBooleanButton(GameSettings::notCheckJVMProperty);
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
                bindIndependentTextField(jvmArgsPane, txtJVMArgs, GameSettings::jvmOptionsProperty);
            }

            var deprecatedJvmMemorySettings = new ComponentSublist(() -> {
                var minMemoryPane = new LinePane();
                minMemoryPane.setTitle(i18n("settings.memory.lower_bound"));
                {
                    var txtMinMemory = new JFXTextField();
                    txtMinMemory.setPrefWidth(160);
                    minMemoryPane.setRight(new HBox(8, txtMinMemory, new Label(i18n("settings.memory.unit.mib"))));
                    bindIndependentIntegerTextField(minMemoryPane, txtMinMemory, GameSettings::minMemoryProperty);
                }

                var metaspacePane = new LinePane();
                metaspacePane.setTitle(i18n("settings.advanced.java_permanent_generation_space"));
                {
                    var txtMetaspace = new JFXTextField();
                    txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
                    txtMetaspace.setPrefWidth(160);
                    metaspacePane.setRight(new HBox(8, txtMetaspace, new Label(i18n("settings.memory.unit.mib"))));
                    bindIndependentTextField(metaspacePane, txtMetaspace, GameSettings::permSizeProperty);
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
                bindInheritableTextField(preLaunchCommandPane, txtPreLaunchCommand, GameSettings::preLaunchCommandProperty);
            }

            var wrapperPane = new LinePane();
            customCommandSettings.getContent().add(wrapperPane);
            wrapperPane.setTitle(i18n("settings.advanced.wrapper_launcher"));
            {
                var txtWrapper = new JFXTextField();
                txtWrapper.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
                txtWrapper.setPrefWidth(400);
                wrapperPane.setRight(txtWrapper);
                bindInheritableTextField(wrapperPane, txtWrapper, GameSettings::commandWrapperProperty);
            }

            var postExitCommandPane = new LinePane();
            customCommandSettings.getContent().add(postExitCommandPane);
            postExitCommandPane.setTitle(i18n("settings.advanced.post_exit_command"));
            {
                var txtPostExitCommand = new JFXTextField();
                txtPostExitCommand.setPromptText(i18n("settings.advanced.post_exit_command.prompt"));
                txtPostExitCommand.setPrefWidth(400);
                postExitCommandPane.setRight(txtPostExitCommand);
                bindInheritableTextField(postExitCommandPane, txtPostExitCommand, GameSettings::postExitCommandProperty);
            }
        }

        var graphicsSettings = new ComponentList();
        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced.graphics")),
                graphicsSettings
        );
        {
            var graphicsBackendPane = createInheritableButton(
                    GameSettings::graphicsBackendProperty,
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
                    GameSettings::openGLRendererProperty,
                    e -> i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT)),
                    e -> {
                        String bundleKey = "settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                        return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
                    },
                    Renderer.getSupported(GraphicsAPI.OPENGL).toArray(Renderer[]::new));
            graphicsSettings.getContent().add(openGLRendererPane);
            openGLRendererPane.setTitle(i18n("settings.advanced.renderer.opengl"));

            var vulkanRendererPane = createInheritableButton(
                    GameSettings::vulkanRendererProperty,
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
            var useCustomNativesDirPane = createIndependentBooleanButton(GameSettings::useCustomNativesProperty);
            nativeLibrarySettings.getContent().add(useCustomNativesDirPane);
            useCustomNativesDirPane.setTitle(i18n("settings.advanced.natives_directory.custom.enabled"));

            var nativesDirPane = new LinePane();
            nativeLibrarySettings.getContent().add(nativesDirPane);
            nativesDirPane.setTitle(i18n("settings.advanced.natives_directory"));
            {
                var txtNativesDir = new JFXTextField();
                txtNativesDir.setPrefWidth(400);
                nativesDirPane.setRight(txtNativesDir);
                bindIndependentTextField(nativesDirPane, txtNativesDir, GameSettings::nativesDirectoryProperty);
            }

            var noNativesPatchPane = createIndependentBooleanButton(GameSettings::notPatchNativesProperty);
            nativeLibrarySettings.getContent().add(noNativesPatchPane);
            noNativesPatchPane.setTitle(i18n("settings.advanced.dont_patch_natives"));

            var useNativeGLFWPane = createIndependentBooleanButton(GameSettings::useNativeGLFWProperty);
            nativeLibrarySettings.getContent().add(useNativeGLFWPane);
            useNativeGLFWPane.setTitle(i18n("settings.advanced.use_native_glfw"));
            useNativeGLFWPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));

            var useNativeOpenALPane = createIndependentBooleanButton(GameSettings::useNativeOpenALProperty);
            nativeLibrarySettings.getContent().add(useNativeOpenALPane);
            useNativeOpenALPane.setTitle(i18n("settings.advanced.use_native_openal"));
            useNativeOpenALPane.setSubtitle(i18n("settings.advanced.linux_freebsd_only"));
        }

    }

    // region Helper Methods for UI

    @SuppressWarnings("unchecked")
    private void selectPreset(GameSettings.Preset setting) {
        currentSetting.set((S) setting);
    }

    private void bindInstanceParentSetting(LineSelectButton<GameSettings.@Nullable Preset> button) {
        ObservableList<GameSettings.Preset> items = FXCollections.observableArrayList();
        InvalidationListener updateItems = observable -> {
            @Nullable GameSettings.Preset selected = button.getValue();
            items.setAll((GameSettings.Preset) null);
            items.addAll(SettingsManager.getGameSettings());
            refreshInstanceParentSettingConverter(button);
            if (selected != null && SettingsManager.getGameSettings(selected.idProperty().getValue()) == null) {
                button.setValue(null);
            }
        };
        updateItems.invalidated(SettingsManager.getGameSettings());
        SettingsManager.getGameSettings().addListener(updateItems);
        button.setItems(items);
        refreshInstanceParentSettingConverter(button);

        button.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingParentSetting || !(currentSetting.get() instanceof GameSettings.Instance setting)) {
                return;
            }
            setting.parentProperty().setValue(newValue != null ? newValue.idProperty().getValue() : null);
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (newValue instanceof GameSettings.Instance setting) {
                updatingParentSetting = true;
                try {
                    refreshInstanceParentSettingConverter(button);
                    SettingID parent = setting.parentProperty().getValue();
                    button.setValue(parent != null ? SettingsManager.getGameSettings(parent) : null);
                } finally {
                    updatingParentSetting = false;
                }
            }
        });
    }

    /// Refreshes parent preset display text because the implicit parent depends on the current profile.
    private void refreshInstanceParentSettingConverter(LineSelectButton<GameSettings.@Nullable Preset> button) {
        button.setConverter(setting -> setting != null
                ? PresetManagementPane.getPresetDisplayName(setting)
                : getImplicitParentGameSettingsDisplayName());
    }

    /// Returns the label for the implicit parent preset selected by a null instance parent.
    private String getImplicitParentGameSettingsDisplayName() {
        GameSettings.Preset legacyParent = getProfileLegacyGameSettings();
        return legacyParent != null
                ? PresetManagementPane.getPresetDisplayName(legacyParent)
                : i18n("settings.type.global.preset.default");
    }

    /// Returns the migrated profile-level parent preset, or null when this profile uses the default preset.
    private @Nullable GameSettings.Preset getProfileLegacyGameSettings() {
        if (profile == null || profile.getLegacyGameSettings() == null) {
            return null;
        }

        return SettingsManager.getGameSettings(profile.getLegacyGameSettings());
    }

    /// Adds the title-line inheritance button for the Java selection sublist.
    private void bindJavaInheritanceButton(ComponentSublist sublist) {
        if (isPresetSetting) {
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
                    GameSettings source = getEffectiveInheritableSource(setting, GameSettings::javaTypeProperty);
                    setting.javaTypeProperty().setValue(getEffectiveValue(setting, GameSettings::javaTypeProperty));
                    setting.customJavaVersionProperty().setValue(source.customJavaVersionProperty().getValue());
                    setting.customJavaPathProperty().setValue(source.customJavaPathProperty().getValue());
                    setting.detectedJavaProperty().setValue(source.detectedJavaProperty().getValue());
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
            Function<GameSettings, SettingProperty<String>> propertyGetter) {
        IndependentSettingBinder.bindTextField(
                isPresetSetting,
                currentSetting,
                line,
                textField,
                propertyGetter,
                this::createInheritanceButton,
                GameSettingsPage::updateInheritanceButton,
                this::getEffectiveParentGameSettings);
    }

    /// Binds an integer text field to a setting property with independent override state.
    private void bindIndependentIntegerTextField(
            LineComponent line,
            JFXTextField textField,
            Function<GameSettings, ? extends SettingProperty<Integer>> propertyGetter) {
        IndependentSettingBinder.bindIntegerTextField(
                isPresetSetting,
                currentSetting,
                line,
                textField,
                propertyGetter,
                this::createInheritanceButton,
                GameSettingsPage::updateInheritanceButton,
                this::getEffectiveParentGameSettings);
    }

    private void bindWindowSizeComboBox(JFXComboBox<String> comboBox) {
        ObjectProperty<@Nullable Property<Double>> activeWidthProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable Property<Double>> activeHeightProperty = new SimpleObjectProperty<>();
        final Holder<@Nullable String> committedValue = new Holder<>();
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
                Double width = setting != null ? getEffectiveValue(setting, GameSettings::widthProperty) : widthProperty.getValue();
                Double height = setting != null ? getEffectiveValue(setting, GameSettings::heightProperty) : heightProperty.getValue();
                String value = isSpecifiedWindowSize(width, height) ? formatWindowSize(width, height) : null;
                committedValue.value = value;
                comboBox.setValue(value);
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
                        committedValue,
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
                        committedValue,
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

    private void applyWindowSizeComboBoxValue(JFXComboBox<String> comboBox,
                                              @Nullable GameSettings setting,
                                              @Nullable Property<Double> widthProperty,
                                              @Nullable Property<Double> heightProperty,
                                              Holder<@Nullable String> committedValue,
                                              Holder<Boolean> updating) {
        if (widthProperty == null || heightProperty == null || updating.value) {
            return;
        }

        String value = comboBox.getValue();
        if (Objects.equals(value, committedValue.value)) {
            return;
        }

        updating.value = true;
        try {
            if (StringUtils.isBlank(value)) {
                setWindowSizeOverridden(setting);
                widthProperty.setValue(0.0);
                heightProperty.setValue(0.0);
                comboBox.setValue(null);
                committedValue.value = null;
                return;
            }

            int idx = value.indexOf('x');
            if (idx < 0) {
                idx = value.indexOf('*');
            }

            if (idx < 0) {
                comboBox.setValue(committedValue.value);
                return;
            }

            try {
                double width = Double.parseDouble(value.substring(0, idx).trim());
                double height = Double.parseDouble(value.substring(idx + 1).trim());
                setWindowSizeOverridden(setting);
                widthProperty.setValue(width);
                heightProperty.setValue(height);
                String formattedValue = formatNullableWindowSize(width, height);
                comboBox.setValue(formattedValue);
                committedValue.value = formattedValue;
            } catch (NumberFormatException e) {
                comboBox.setValue(committedValue.value);
            }
        } finally {
            updating.value = false;
        }
    }

    private void setWindowSizeOverridden(@Nullable GameSettings setting) {
        if (setting == null) {
            return;
        }
        setWindowSettingsOverridden(setting, true);
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
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
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
    private static boolean isPropertyOverridden(GameSettings setting, SettingProperty<?> property) {
        return !(setting instanceof GameSettings.Instance instance)
                || instance.getOverrideProperties().contains(property.getName());
    }

    /// Updates whether an instance setting uses its direct property value.
    private static void setPropertyOverridden(GameSettings setting, SettingProperty<?> property, boolean overridden) {
        if (!(setting instanceof GameSettings.Instance instance)) {
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
            Function<GameSettings, InheritableProperty<String>> propertyGetter) {
        bindInheritableStringProperty(line, textField.textProperty(), propertyGetter);
    }

    /// Binds a string property to an inheritable string setting.
    private void bindInheritableStringProperty(
            LineComponent line,
            Property<String> textProperty,
            Function<GameSettings, InheritableProperty<String>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<String>> activeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        final Holder<InvalidationListener> refreshHolder = new Holder<>();
        @Nullable JFXButton inheritButton = null;
        if (!isPresetSetting) {
            inheritButton = createInheritanceButton();
            line.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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
                GameSettings setting = currentSetting.get();
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

        SettingsManager.getGameSettings().addListener(refresh);
        settings().defaultGameSettingsPresetProperty().addListener(refresh);

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
            Function<GameSettings, InheritableProperty<String>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<String>> activeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        final Holder<InvalidationListener> refreshHolder = new Holder<>();

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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

        SettingsManager.getGameSettings().addListener(refresh);
        settings().defaultGameSettingsPresetProperty().addListener(refresh);

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
            if (!(setting instanceof GameSettings.Instance instance) || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean forceIsolated = isCurrentInstanceModpack();
                button.setSelected(forceIsolated
                        || instance.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY));
                button.setDisable(forceIsolated);
            } finally {
                updating.value = false;
            }
        };

        button.selectedProperty().addListener((observable, oldValue, newValue) -> {
            S setting = currentSetting.get();
            if (!(setting instanceof GameSettings.Instance instance) || updating.value || isCurrentInstanceModpack()) {
                return;
            }

            updating.value = true;
            try {
                if (newValue) {
                    instance.runningDirectoryProperty().setValue("");
                    instance.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                } else {
                    instance.getOverrideProperties().remove(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                }
            } finally {
                updating.value = false;
            }
            refresh.invalidated(instance);
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue instanceof GameSettings.Instance oldInstance) {
                oldInstance.removeListener(refresh);
            }

            if (newValue instanceof GameSettings.Instance instance) {
                instance.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        S setting = currentSetting.get();
        if (setting instanceof GameSettings.Instance instance) {
            instance.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Binds the running directory editor to the source selected by version isolation.
    private void bindRunningDirectoryProperty(
            LineComponent line,
            Property<String> textProperty,
            Node editor) {
        if (isPresetSetting) {
            bindInheritableStringProperty(line, textProperty, GameSettings::runningDirectoryProperty);
            return;
        }

        final Holder<Boolean> updating = new Holder<>(false);
        ObjectProperty<@Nullable InheritableProperty<String>> activeParentProperty = new SimpleObjectProperty<>();
        final Holder<InvalidationListener> refreshHolder = new Holder<>();
        JFXButton inheritButton = createInheritanceButton();
        line.setTitleTrailing(inheritButton);

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
            updateParentInheritablePropertyListener(setting, activeParentProperty, GameSettings::runningDirectoryProperty, refreshHolder.value);
            if (!(setting instanceof GameSettings.Instance instance) || updating.value) {
                return;
            }

            updating.value = true;
            try {
                boolean useInstanceRunningDirectory = isCurrentInstanceModpack()
                        || instance.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                String runningDirectory;
                if (useInstanceRunningDirectory) {
                    String value = instance.runningDirectoryProperty().getValue();
                    runningDirectory = value != null ? value : instance.runningDirectoryProperty().defaultValue();
                } else {
                    runningDirectory = getParentValue(instance, GameSettings::runningDirectoryProperty);
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
            GameSettings setting = currentSetting.get();
            if (!(setting instanceof GameSettings.Instance instance) || updating.value || isCurrentInstanceModpack()) {
                return;
            }

            updating.value = true;
            try {
                if (instance.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY)) {
                    instance.getOverrideProperties().remove(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                } else {
                    instance.runningDirectoryProperty().setValue("");
                    instance.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                }
            } finally {
                updating.value = false;
            }
            refresh.invalidated(instance);
            event.consume();
        });

        textProperty.addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            if (!(setting instanceof GameSettings.Instance instance)
                    || updating.value
                    || (!isCurrentInstanceModpack()
                    && !instance.getOverrideProperties().contains(GameSettings.PROPERTY_RUNNING_DIRECTORY))) {
                return;
            }

            updating.value = true;
            try {
                instance.getOverrideProperties().add(GameSettings.PROPERTY_RUNNING_DIRECTORY);
                instance.runningDirectoryProperty().setValue(newValue != null ? newValue : "");
            } finally {
                updating.value = false;
            }
        });

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue instanceof GameSettings.Instance oldInstance) {
                oldInstance.removeListener(refresh);
            }

            if (newValue instanceof GameSettings.Instance newInstance) {
                newInstance.addListener(refresh);
            }
            refresh.invalidated(newValue);
        });

        SettingsManager.getGameSettings().addListener(refresh);
        settings().defaultGameSettingsPresetProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting instanceof GameSettings.Instance instance) {
            instance.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    private boolean isCurrentInstanceModpack() {
        return profile != null && instanceId != null && profile.getRepository().isModpack(instanceId);
    }

    /// Keeps a listener attached to the current instance's parent preset property.
    private <T> void updateParentInheritablePropertyListener(
            @Nullable GameSettings setting,
            ObjectProperty<@Nullable InheritableProperty<T>> activeParentProperty,
            Function<GameSettings, InheritableProperty<T>> propertyGetter,
            InvalidationListener listener) {
        InheritableProperty<T> oldParentProperty = activeParentProperty.get();
        InheritableProperty<T> newParentProperty = setting instanceof GameSettings.Instance instance
                ? propertyGetter.apply(getEffectiveParentGameSettings(instance))
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
            Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<T>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isPresetSetting) {
            inheritButton = createInheritanceButton();
            sublist.setTitleRight(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener propertyListener = observable -> {
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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
                GameSettings setting = currentSetting.get();
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
        SettingsManager.getGameSettings().addListener(propertyListener);
        settings().defaultGameSettingsPresetProperty().addListener(propertyListener);

        S setting = currentSetting.get();
        if (setting != null) {
            InheritableProperty<T> property = propertyGetter.apply(setting);
            activeProperty.set(property);
            setting.addListener(propertyListener);
            property.addListener(propertyListener);
            propertyListener.invalidated(property);
        }
    }

    /// Binds the window type and size editor as one inheritable setting group.
    private void bindWindowSettings(ComponentSublist sublist, RadioChoiceList<GameWindowType> item) {
        ObjectProperty<@Nullable InheritableProperty<GameWindowType>> activeWindowTypeProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<Double>> activeWidthProperty = new SimpleObjectProperty<>();
        ObjectProperty<@Nullable InheritableProperty<Double>> activeHeightProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isPresetSetting) {
            inheritButton = createInheritanceButton();
            sublist.setTitleRight(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener propertyListener = observable -> {
            GameSettings setting = currentSetting.get();
            InheritableProperty<GameWindowType> property = activeWindowTypeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                item.setSelectedValue(getEffectiveValue(setting, GameSettings::windowTypeProperty));
                if (finalInheritButton != null) {
                    updateInheritanceButton(finalInheritButton, !isWindowSettingsOverridden(setting));
                }
            } finally {
                updating.value = false;
            }
        };

        item.selectedValueProperty().addListener((observable, oldValue, newValue) -> {
            GameSettings setting = currentSetting.get();
            InheritableProperty<GameWindowType> property = activeWindowTypeProperty.get();
            if (setting == null || property == null || updating.value) {
                return;
            }

            updating.value = true;
            try {
                setWindowSettingsOverridden(setting, true);
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
                GameSettings setting = currentSetting.get();
                if (setting == null || updating.value) {
                    return;
                }

                updating.value = true;
                try {
                    setWindowSettingsOverridden(setting, !isWindowSettingsOverridden(setting));
                } finally {
                    updating.value = false;
                }
                propertyListener.invalidated(setting);
                event.consume();
            });
        }

        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.removeListener(propertyListener);
            }

            InheritableProperty<GameWindowType> oldWindowTypeProperty = activeWindowTypeProperty.get();
            if (oldWindowTypeProperty != null) {
                oldWindowTypeProperty.removeListener(propertyListener);
            }
            InheritableProperty<Double> oldWidthProperty = activeWidthProperty.get();
            if (oldWidthProperty != null) {
                oldWidthProperty.removeListener(propertyListener);
            }
            InheritableProperty<Double> oldHeightProperty = activeHeightProperty.get();
            if (oldHeightProperty != null) {
                oldHeightProperty.removeListener(propertyListener);
            }

            InheritableProperty<GameWindowType> newWindowTypeProperty = newValue != null ? newValue.windowTypeProperty() : null;
            InheritableProperty<Double> newWidthProperty = newValue != null ? newValue.widthProperty() : null;
            InheritableProperty<Double> newHeightProperty = newValue != null ? newValue.heightProperty() : null;
            activeWindowTypeProperty.set(newWindowTypeProperty);
            activeWidthProperty.set(newWidthProperty);
            activeHeightProperty.set(newHeightProperty);
            if (newValue != null) {
                newValue.addListener(propertyListener);
            }
            if (newWindowTypeProperty != null) {
                newWindowTypeProperty.addListener(propertyListener);
            }
            if (newWidthProperty != null) {
                newWidthProperty.addListener(propertyListener);
            }
            if (newHeightProperty != null) {
                newHeightProperty.addListener(propertyListener);
            }
            propertyListener.invalidated(newValue);
        });

        SettingsManager.getGameSettings().addListener(propertyListener);
        settings().defaultGameSettingsPresetProperty().addListener(propertyListener);

        S setting = currentSetting.get();
        if (setting != null) {
            activeWindowTypeProperty.set(setting.windowTypeProperty());
            activeWidthProperty.set(setting.widthProperty());
            activeHeightProperty.set(setting.heightProperty());
            setting.addListener(propertyListener);
            setting.windowTypeProperty().addListener(propertyListener);
            setting.widthProperty().addListener(propertyListener);
            setting.heightProperty().addListener(propertyListener);
        }
        propertyListener.invalidated(setting);
    }

    /// Returns whether any property in the window settings group uses a direct value.
    private static boolean isWindowSettingsOverridden(GameSettings setting) {
        return isPropertyOverridden(setting, setting.windowTypeProperty())
                || isPropertyOverridden(setting, setting.widthProperty())
                || isPropertyOverridden(setting, setting.heightProperty());
    }

    /// Updates whether the window settings group uses direct property values.
    private void setWindowSettingsOverridden(GameSettings setting, boolean overridden) {
        if (!(setting instanceof GameSettings.Instance)) {
            return;
        }

        if (overridden) {
            if (!isPropertyOverridden(setting, setting.windowTypeProperty())) {
                setting.windowTypeProperty().setValue(getEffectiveValue(setting, GameSettings::windowTypeProperty));
            }
            if (!isPropertyOverridden(setting, setting.widthProperty())) {
                setting.widthProperty().setValue(getEffectiveValue(setting, GameSettings::widthProperty));
            }
            if (!isPropertyOverridden(setting, setting.heightProperty())) {
                setting.heightProperty().setValue(getEffectiveValue(setting, GameSettings::heightProperty));
            }
        }

        setPropertyOverridden(setting, setting.windowTypeProperty(), overridden);
        setPropertyOverridden(setting, setting.widthProperty(), overridden);
        setPropertyOverridden(setting, setting.heightProperty(), overridden);
    }

    private <T> void bindInheritableSublistDescription(ComponentSublist sublist,
                                                       Function<GameSettings, InheritableProperty<T>> propertyGetter,
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
        SettingsManager.getGameSettings().addListener(propertyListener);
        settings().defaultGameSettingsPresetProperty().addListener(propertyListener);

        S setting = currentSetting.get();
        if (setting != null) {
            propertyGetter.apply(setting).addListener(propertyListener);
        }
        initInheritableSublistDescription(sublist, propertyGetter, converter);
    }

    private <T> void initInheritableSublistDescription(ComponentSublist sublist,
                                                       Function<GameSettings, InheritableProperty<T>> propertyGetter,
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
    private <T> void bindPresetBidirectional(Property<T> property, Function<GameSettings.Preset, Property<T>> propertyGetter) {
        assert isPresetSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    /// Creates a toggle-based editor for a setting property with independent override state.
    private LineInheritableToggleButton createIndependentBooleanButton(
            Function<GameSettings, SettingProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritTooltip(i18n("settings.game.inherit_global"));
        button.setOverriddenTooltip(i18n("settings.game.override_global"));
        button.setInheritAvailable(!isPresetSetting);

        IndependentSettingBinder.bindToggleButton(currentSetting, button, propertyGetter, this::getEffectiveParentGameSettings);
        return button;
    }

    /// Creates a toggle-based inheritable boolean editor that displays the effective value.
    private LineInheritableToggleButton createInheritableBooleanButton(
            Function<GameSettings, InheritableProperty<Boolean>> propertyGetter) {
        var button = new LineInheritableToggleButton();
        button.setInheritTooltip(i18n("settings.game.inherit_global"));
        button.setOverriddenTooltip(i18n("settings.game.override_global"));
        button.setInheritAvailable(!isPresetSetting);

        bindEffectiveInheritableToggleButton(button, propertyGetter);
        return button;
    }

    /// Binds an inheritable select editor to an inheritable setting.
    private <T> void bindInheritableLineSelectButton(
            LineSelectButton<T> button,
            Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<T>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);
        @Nullable JFXButton inheritButton = null;
        if (!isPresetSetting) {
            inheritButton = createInheritanceButton();
            button.setTitleTrailing(inheritButton);
        }
        @Nullable JFXButton finalInheritButton = inheritButton;

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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
                GameSettings setting = currentSetting.get();
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

        SettingsManager.getGameSettings().addListener(refresh);
        settings().defaultGameSettingsPresetProperty().addListener(refresh);

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
            Function<GameSettings, InheritableProperty<Boolean>> propertyGetter) {
        ObjectProperty<@Nullable InheritableProperty<Boolean>> activeProperty = new SimpleObjectProperty<>();
        final Holder<Boolean> updating = new Holder<>(false);

        InvalidationListener refresh = observable -> {
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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
            GameSettings setting = currentSetting.get();
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

        SettingsManager.getGameSettings().addListener(refresh);
        settings().defaultGameSettingsPresetProperty().addListener(refresh);

        S setting = currentSetting.get();
        if (setting != null) {
            activeProperty.set(propertyGetter.apply(setting));
            setting.addListener(refresh);
            refresh.invalidated(setting);
        }
    }

    /// Resolves the setting with its parent preset for effective-value queries.
    private GameSettings.Effective resolveEffectiveSetting(GameSettings setting) {
        if (setting instanceof GameSettings.Preset preset) {
            return GameSettings.resolve(preset, null);
        }

        if (setting instanceof GameSettings.Instance instance) {
            return GameSettings.resolve(getEffectiveParentGameSettings(instance), instance);
        }

        throw new AssertionError("Unknown game setting type: " + setting.getClass());
    }

    /// Returns the effective value after applying parent inheritance.
    private <T> T getEffectiveValue(
            GameSettings setting,
            Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        InheritableProperty<T> property = propertyGetter.apply(setting);
        if (isPropertyOverridden(setting, property)) {
            return getDirectValue(property);
        }

        if (setting instanceof GameSettings.Instance instance) {
            return getParentValue(instance, propertyGetter);
        }

        return getDirectValue(property);
    }

    /// Returns the value provided by an instance's parent preset.
    private <T> T getParentValue(
            GameSettings.Instance instance,
            Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        GameSettings.Preset parent = getEffectiveParentGameSettings(instance);
        return getDirectValue(propertyGetter.apply(parent));
    }

    /// Returns the setting object that provides the effective inheritable value.
    private <T> GameSettings getEffectiveInheritableSource(
            GameSettings setting,
            Function<GameSettings, InheritableProperty<T>> propertyGetter) {
        if (isPropertyOverridden(setting, propertyGetter.apply(setting)) || !(setting instanceof GameSettings.Instance instance)) {
            return setting;
        }

        return getEffectiveParentGameSettings(instance);
    }

    /// Returns the runtime parent preset for an instance, including profile-level legacy migration fallback.
    private GameSettings.Preset getEffectiveParentGameSettings(GameSettings.Instance instance) {
        if (profile != null) {
            return profile.getRepository().getParentGameSettings(instance);
        }

        return getExplicitParentGameSettings(instance);
    }

    /// Returns the explicitly configured parent preset for an instance, falling back to the default preset.
    private GameSettings.Preset getExplicitParentGameSettings(GameSettings.Instance instance) {
        SettingID parent = instance.parentProperty().getValue();
        GameSettings.Preset parentSetting = SettingsManager.getGameSettings(parent);
        return parentSetting != null ? parentSetting : SettingsManager.getDefaultGameSettingsPresetOrCreate();
    }

    @SafeVarargs
    private <T extends @UnknownNullability Object> LineSelectButton<T> createInheritableButton(
            Function<GameSettings, InheritableProperty<T>> propertyGetter,
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

        assert isPresetSetting == (instanceId == null);

        if (instanceId != null) {
            HMCLGameRepository repository = profile.getRepository();
            @Nullable GameSettings.Instance setting = repository.getInstanceGameSettingsOrCreate(instanceId);
            this.currentSetting.set((S) setting);
            setSettingsReadOnly(
                    setting == null || repository.isInstanceGameSettingsReadOnly(instanceId),
                    i18n("settings.game.instance_settings.unsupported"),
                    setting != null ? this::forceOverwriteInstanceGameSettings : null);
            loadIcon();
        } else {
            this.currentSetting.set((S) SettingsManager.getDefaultGameSettingsPresetOrCreate());
            setSettingsReadOnly(
                    SettingsManager.isGameSettingsReadOnly(),
                    i18n("settings.game.presets.unsupported"),
                    this::forceOverwriteGameSettings);
        }
    }

    /// Updates the page read-only state used when settings cannot be saved safely.
    ///
    /// @param readOnly whether the current settings should be displayed read-only
    /// @param message the warning message shown when the page is read-only
    private void setSettingsReadOnly(boolean readOnly, String message) {
        setSettingsReadOnly(readOnly, message, null);
    }

    /// Updates the page read-only state used when settings cannot be saved safely.
    ///
    /// @param readOnly whether the current settings should be displayed read-only
    /// @param message the warning message shown when the page is read-only
    /// @param forceOverwrite the action used to back up and overwrite the read-only file
    private void setSettingsReadOnly(boolean readOnly, String message, @Nullable Runnable forceOverwrite) {
        if (readOnly && forceOverwrite != null) {
            readOnlySettingsHint.setSegment(
                    message + "<br/><a href=\"force-overwrite\">" + i18n("settings.file.force_write") + "</a>",
                    href -> {
                        if ("force-overwrite".equals(href)) {
                            forceOverwrite.run();
                        } else {
                            Controllers.onHyperlinkAction(href);
                        }
                    });
        } else {
            readOnlySettingsHint.setSegment(message);
        }
        readOnlySettingsHint.setVisible(readOnly);
        readOnlySettingsHint.setManaged(readOnly);

        for (Node child : rootPane.getChildren()) {
            if (child != readOnlySettingsHint) {
                child.setDisable(readOnly);
            }
        }
    }

    /// Backs up and overwrites the current instance's `instance-game-settings.json`.
    private void forceOverwriteInstanceGameSettings() {
        if (profile == null || instanceId == null) {
            return;
        }

        Controllers.confirmBackupAndOverwrite(i18n("settings.game.instance_settings.unsupported"), () -> {
            profile.getRepository().forceOverwriteInstanceGameSettings(instanceId);
            setSettingsReadOnly(false, "");
        });
    }

    /// Backs up and overwrites `game-settings.json` using the currently loaded presets.
    private void forceOverwriteGameSettings() {
        Controllers.confirmBackupAndOverwrite(i18n("settings.game.presets.unsupported"), () -> {
            SettingsManager.forceOverwriteGameSettings();
            setSettingsReadOnly(false, "");
        });
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
        GameSettings source = getEffectiveInheritableSource(setting, GameSettings::javaTypeProperty);
        JavaVersionType javaType = getEffectiveValue(setting, GameSettings::javaTypeProperty);
        switch (javaType) {
            case CUSTOM:
                javaCustomOption.setSelected(true);
                javaCustomOption.setPath(source.customJavaPathProperty().getValue());
                break;
            case VERSION:
                javaVersionOption.setSelected(true);
                javaVersionOption.setText(source.customJavaVersionProperty().getValue());
                break;
            case AUTO:
                javaAutoDeterminedOption.setSelected(true);
                break;
            default:
                RadioChoiceList.Choice<@Nullable Pair<@Nullable JavaVersionType, @Nullable JavaRuntime>> choice = null;
                if (JavaManager.isInitialized()) {
                    try {
                        JavaRuntime java = resolveEffectiveSetting(setting).getJava(null, null);
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
        GameSettings.Effective effectiveSetting = this.instanceId != null ? repository.getEffectiveGameSettings(this.instanceId) : null;
        JavaVersionType effectiveJavaVersionType = effectiveSetting != null ? effectiveSetting.getInheritable(GameSettings::javaTypeProperty) : javaVersionType;
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
                        : resolveEffectiveSetting(setting).getJava(gameVersionNumber, version);
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
            Versions.modifyGameSettings(profile, Profiles.getSelectedInstance(profile));
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
        GameSettings.Instance localGameSettings = profile.getRepository().getInstanceGameSettingsOrCreate(instanceId);
        if (localGameSettings != null) {
            localGameSettings.iconProperty().setValue(VersionIconType.DEFAULT);
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
