package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.game.NativesDirectoryType;
import org.jackhuang.hmcl.game.Renderer;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class AdvancedVersionSettingPage extends StackPane implements DecoratorPage {

    private final ObjectProperty<State> stateProperty;

    private final Profile profile;
    private final String versionId;
    private final VersionSetting versionSetting;

    private final JFXTextField txtJVMArgs;
    private final JFXTextField txtGameArgs;
    private final JFXTextField txtEnvironmentVariables;
    private final JFXTextField txtMetaspace;
    private final JFXTextField txtWrapper;
    private final JFXTextField txtPreLaunchCommand;
    private final JFXTextField txtPostExitCommand;
    private final LineToggleButton noJVMArgsPane;
    private final LineToggleButton noOptimizingJVMArgsPane;
    private final LineToggleButton noGameCheckPane;
    private final LineToggleButton noJVMCheckPane;
    private final LineToggleButton noNativesPatchPane;
    private final LineToggleButton useNativeGLFWPane;
    private final LineToggleButton useNativeOpenALPane;
    private final ComponentSublist nativesDirSublist;
    private final MultiFileItem<NativesDirectoryType> nativesDirItem;
    private final MultiFileItem.FileOption<NativesDirectoryType> nativesDirCustomOption;
    private final LineSelectButton<Renderer> rendererPane;

    public AdvancedVersionSettingPage(Profile profile, @Nullable String versionId, VersionSetting versionSetting) {
        this.profile = profile;
        this.versionId = versionId;
        this.versionSetting = versionSetting;
        this.stateProperty = new SimpleObjectProperty<>(State.fromTitle(
                versionId == null ? i18n("settings.advanced") : i18n("settings.advanced.title", versionId)
        ));

        this.getStyleClass().add("gray-background");

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        getChildren().setAll(scrollPane);

        VBox rootPane = new VBox();
        rootPane.setFocusTraversable(true);
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");

        ComponentList customCommandsPane = new ComponentList();
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
            txtJVMArgs.getStyleClass().add("fit-width");
            pane.addRow(0, new Label(i18n("settings.advanced.jvm_args")), txtJVMArgs);

            HintPane hintPane = new HintPane();
            hintPane.setText(i18n("settings.advanced.jvm_args.prompt"));
            GridPane.setColumnSpan(hintPane, 2);
            pane.addRow(4, hintPane);

            txtMetaspace = new JFXTextField();
            txtMetaspace.setPromptText(i18n("settings.advanced.java_permanent_generation_space.prompt"));
            txtMetaspace.getStyleClass().add("fit-width");
            FXUtils.setValidateWhileTextChanged(txtMetaspace, true);
            txtMetaspace.setValidators(new NumberValidator(i18n("input.number"), true));
            pane.addRow(1, new Label(i18n("settings.advanced.java_permanent_generation_space")), txtMetaspace);

            txtEnvironmentVariables = new JFXTextField();
            txtEnvironmentVariables.getStyleClass().add("fit-width");
            pane.addRow(2, new Label(i18n("settings.advanced.environment_variables")), txtEnvironmentVariables);

            jvmPane.getContent().setAll(pane);
        }

        ComponentList workaroundPane = new ComponentList();

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

            rendererPane = new LineSelectButton<>();
            rendererPane.setTitle(i18n("settings.advanced.renderer"));
            rendererPane.setConverter(e -> i18n("settings.advanced.renderer." + e.name().toLowerCase(Locale.ROOT)));
            rendererPane.setItems(Renderer.values());

            noJVMArgsPane = new LineToggleButton();
            noJVMArgsPane.setTitle(i18n("settings.advanced.no_jvm_args"));

            noOptimizingJVMArgsPane = new LineToggleButton();
            noOptimizingJVMArgsPane.setTitle(i18n("settings.advanced.no_optimizing_jvm_args"));
            noOptimizingJVMArgsPane.disableProperty().bind(noJVMArgsPane.selectedProperty());

            noGameCheckPane = new LineToggleButton();
            noGameCheckPane.setTitle(i18n("settings.advanced.dont_check_game_completeness"));

            noJVMCheckPane = new LineToggleButton();
            noJVMCheckPane.setTitle(i18n("settings.advanced.dont_check_jvm_validity"));

            noNativesPatchPane = new LineToggleButton();
            noNativesPatchPane.setTitle(i18n("settings.advanced.dont_patch_natives"));

            useNativeGLFWPane = new LineToggleButton();
            useNativeGLFWPane.setTitle(i18n("settings.advanced.use_native_glfw"));

            useNativeOpenALPane = new LineToggleButton();
            useNativeOpenALPane.setTitle(i18n("settings.advanced.use_native_openal"));

            workaroundPane.getContent().setAll(
                    nativesDirSublist, rendererPane, noJVMArgsPane, noOptimizingJVMArgsPane, noGameCheckPane,
                    noJVMCheckPane, noNativesPatchPane
            );

            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                workaroundPane.getContent().addAll(useNativeGLFWPane, useNativeOpenALPane);
            } else {
                ComponentSublist unsupportedOptionsSublist = new ComponentSublist();
                unsupportedOptionsSublist.setTitle(i18n("settings.advanced.unsupported_system_options"));
                unsupportedOptionsSublist.getContent().addAll(useNativeGLFWPane, useNativeOpenALPane);
                workaroundPane.getContent().add(unsupportedOptionsSublist);
            }
        }

        rootPane.getChildren().addAll(
                ComponentList.createComponentListTitle(i18n("settings.advanced.custom_commands")), customCommandsPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.jvm")), jvmPane,
                ComponentList.createComponentListTitle(i18n("settings.advanced.workaround")), workaroundWarning, workaroundPane
        );

        bindProperties();
    }

    void bindProperties() {
        nativesDirCustomOption.bindBidirectional(versionSetting.nativesDirProperty());
        FXUtils.bindString(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.bindString(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.bindString(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.bindString(txtEnvironmentVariables, versionSetting.environmentVariablesProperty());
        FXUtils.bindString(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.bindString(txtPreLaunchCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.bindString(txtPostExitCommand, versionSetting.postExitCommandProperty());
        rendererPane.valueProperty().bindBidirectional(versionSetting.rendererProperty());
        noGameCheckPane.selectedProperty().bindBidirectional(versionSetting.notCheckGameProperty());
        noJVMCheckPane.selectedProperty().bindBidirectional(versionSetting.notCheckJVMProperty());
        noJVMArgsPane.selectedProperty().bindBidirectional(versionSetting.noJVMArgsProperty());
        noOptimizingJVMArgsPane.selectedProperty().bindBidirectional(versionSetting.noOptimizingJVMArgsProperty());
        noNativesPatchPane.selectedProperty().bindBidirectional(versionSetting.notPatchNativesProperty());
        useNativeGLFWPane.selectedProperty().bindBidirectional(versionSetting.useNativeGLFWProperty());
        useNativeOpenALPane.selectedProperty().bindBidirectional(versionSetting.useNativeOpenALProperty());

        nativesDirItem.selectedDataProperty().bindBidirectional(versionSetting.nativesDirTypeProperty());
        nativesDirSublist.subtitleProperty().bind(Bindings.createStringBinding(() -> {
            if (versionSetting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
                String nativesDirName = "natives-" + Platform.SYSTEM_PLATFORM;
                if (versionId == null) {
                    return String.join(FileSystems.getDefault().getSeparator(),
                            FileUtils.getAbsolutePath(profile.getRepository().getBaseDirectory().resolve("versions")),
                            i18n("settings.advanced.natives_directory.default.version_id"),
                            nativesDirName
                    );
                } else {
                    return profile.getRepository().getVersionRoot(versionId)
                            .toAbsolutePath().normalize()
                            .resolve(nativesDirName)
                            .toString();
                }
            } else if (versionSetting.getNativesDirType() == NativesDirectoryType.CUSTOM) {
                return versionSetting.getNativesDir();
            } else {
                return null;
            }
        }, versionSetting.nativesDirProperty(), versionSetting.nativesDirTypeProperty()));
    }

    void unbindProperties() {
        nativesDirCustomOption.valueProperty().unbindBidirectional(versionSetting.nativesDirProperty());
        FXUtils.unbind(txtJVMArgs, versionSetting.javaArgsProperty());
        FXUtils.unbind(txtGameArgs, versionSetting.minecraftArgsProperty());
        FXUtils.unbind(txtMetaspace, versionSetting.permSizeProperty());
        FXUtils.unbind(txtEnvironmentVariables, versionSetting.environmentVariablesProperty());
        FXUtils.unbind(txtWrapper, versionSetting.wrapperProperty());
        FXUtils.unbind(txtPreLaunchCommand, versionSetting.preLaunchCommandProperty());
        FXUtils.unbind(txtPostExitCommand, versionSetting.postExitCommandProperty());
        rendererPane.valueProperty().unbindBidirectional(versionSetting.rendererProperty());
        noGameCheckPane.selectedProperty().unbindBidirectional(versionSetting.notCheckGameProperty());
        noJVMCheckPane.selectedProperty().unbindBidirectional(versionSetting.notCheckJVMProperty());
        noJVMArgsPane.selectedProperty().unbindBidirectional(versionSetting.noJVMArgsProperty());
        noOptimizingJVMArgsPane.selectedProperty().unbindBidirectional(versionSetting.noOptimizingJVMArgsProperty());
        noNativesPatchPane.selectedProperty().unbindBidirectional(versionSetting.notPatchNativesProperty());
        useNativeGLFWPane.selectedProperty().unbindBidirectional(versionSetting.useNativeGLFWProperty());
        useNativeOpenALPane.selectedProperty().unbindBidirectional(versionSetting.useNativeOpenALProperty());

        nativesDirItem.selectedDataProperty().unbindBidirectional(versionSetting.nativesDirTypeProperty());
        nativesDirSublist.subtitleProperty().unbind();
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return stateProperty;
    }
}
