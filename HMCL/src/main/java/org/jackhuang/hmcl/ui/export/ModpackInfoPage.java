/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.export;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.mod.ModpackExportInfo;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.export.ModpackTypeSelectionPage.MODPACK_TYPE;
import static org.jackhuang.hmcl.ui.export.ModpackTypeSelectionPage.MODPACK_TYPE_MODRINTH;
import static org.jackhuang.hmcl.ui.export.ModpackTypeSelectionPage.MODPACK_TYPE_SERVER;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackInfoPage extends Control implements WizardPage {
    private final WizardController controller;
    private final HMCLGameRepository gameRepository;
    private final ModpackExportInfo.Options options;
    private final String versionName;
    private final boolean canIncludeLauncher;

    private final ModpackExportInfo exportInfo = new ModpackExportInfo();

    private final SimpleStringProperty name = new SimpleStringProperty("");
    private final SimpleStringProperty author = new SimpleStringProperty("");
    private final SimpleStringProperty version = new SimpleStringProperty("1.0");
    private final SimpleStringProperty description = new SimpleStringProperty("");
    private final SimpleStringProperty url = new SimpleStringProperty("");
    private final SimpleBooleanProperty forceUpdate = new SimpleBooleanProperty();
    private final SimpleBooleanProperty packWithLauncher = new SimpleBooleanProperty();
    private final SimpleStringProperty fileApi = new SimpleStringProperty("");
    private final SimpleIntegerProperty minMemory = new SimpleIntegerProperty(0);
    private final SimpleStringProperty authlibInjectorServer = new SimpleStringProperty();
    private final SimpleStringProperty launchArguments = new SimpleStringProperty("");
    private final SimpleStringProperty javaArguments = new SimpleStringProperty("");
    private final SimpleStringProperty mcbbsThreadId = new SimpleStringProperty("");
    private final SimpleBooleanProperty noCreateRemoteFiles = new SimpleBooleanProperty();
    private final SimpleBooleanProperty skipCurseForgeRemoteFiles = new SimpleBooleanProperty();

    public ModpackInfoPage(WizardController controller, HMCLGameRepository gameRepository, String version) {
        this.controller = controller;
        this.gameRepository = gameRepository;
        this.options = controller.getSettings().get(MODPACK_INFO_OPTION);
        this.versionName = version;

        if (this.options == null)
            throw new IllegalArgumentException("Settings.MODPACK_INFO_OPTION is required");

        name.set(version);
        author.set(Optional.ofNullable(Accounts.getSelectedAccount()).map(Account::getUsername).orElse(""));

        VersionSetting versionSetting = gameRepository.getVersionSetting(versionName);
        minMemory.set(Optional.ofNullable(versionSetting.getMinMemory()).orElse(0));
        launchArguments.set(versionSetting.getMinecraftArgs());
        javaArguments.set(versionSetting.getJavaArgs());

        canIncludeLauncher = JarUtils.thisJarPath() != null;
    }

    private void onNext() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("modpack.wizard.step.initialization.save"));
        if (!packWithLauncher.get() && controller.getSettings().get(MODPACK_TYPE) == MODPACK_TYPE_MODRINTH) {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.mrpack"));
            fileChooser.setInitialFileName(name.get() + (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS ? "" : ".mrpack"));
        } else {
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
            fileChooser.setInitialFileName(name.get() + ".zip");
        }
        Path file = FileUtils.toPath(fileChooser.showSaveDialog(Controllers.getStage()));
        if (file == null) {
            controller.onEnd();
            return;
        }

        exportInfo.setName(name.get());
        exportInfo.setFileApi(fileApi.get());
        exportInfo.setVersion(version.get());
        exportInfo.setAuthor(author.get());
        exportInfo.setDescription(description.get());
        exportInfo.setUrl(url.get());
        exportInfo.setForceUpdate(forceUpdate.get());
        exportInfo.setPackWithLauncher(packWithLauncher.get());
        exportInfo.setMinMemory(minMemory.get());
        exportInfo.setLaunchArguments(launchArguments.get());
        exportInfo.setJavaArguments(javaArguments.get());
        exportInfo.setAuthlibInjectorServer(authlibInjectorServer.get());
        exportInfo.setNoCreateRemoteFiles(noCreateRemoteFiles.get());
        exportInfo.setSkipCurseForgeRemoteFiles(skipCurseForgeRemoteFiles.get());

        if (StringUtils.isNotBlank(mcbbsThreadId.get())) {
            exportInfo.setOrigins(Collections.singletonList(new McbbsModpackManifest.Origin(
                    "mcbbs", Integer.parseInt(mcbbsThreadId.get())
            )));
        }

        controller.getSettings().put(MODPACK_INFO, exportInfo);
        controller.getSettings().put(MODPACK_FILE, file);
        controller.onNext();
    }

    @Override
    public void cleanup(SettingsMap settings) {
        controller.getSettings().remove(MODPACK_INFO);
    }

    @Override
    public String getTitle() {
        return i18n("modpack.wizard.step.1.title");
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModpackInfoPageSkin(this);
    }

    public static final SettingsMap.Key<ModpackExportInfo> MODPACK_INFO = new SettingsMap.Key<>("modpack.info");
    public static final SettingsMap.Key<Path> MODPACK_FILE = new SettingsMap.Key<>("modpack.file");
    public static final SettingsMap.Key<ModpackExportInfo.Options> MODPACK_INFO_OPTION = new SettingsMap.Key<>("modpack.info.option");

    public static class ModpackInfoPageSkin extends SkinBase<ModpackInfoPage> {
        private ObservableList<Node> originList;

        private final List<JFXTextField> validatingFields = new ArrayList<>();

        public ModpackInfoPageSkin(ModpackInfoPage skinnable) {
            super(skinnable);

            Insets componentListMargin = new Insets(16, 0, 16, 0);

            ScrollPane scroll = new ScrollPane();
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(true);
            getChildren().setAll(scroll);


            {
                BorderPane borderPane = new BorderPane();
                borderPane.setStyle("-fx-padding: 16;");
                scroll.setContent(borderPane);

                if (skinnable.controller.getSettings().get(MODPACK_TYPE) == MODPACK_TYPE_SERVER) {
                    Hyperlink hyperlink = new Hyperlink(i18n("modpack.wizard.step.initialization.server"));
                    hyperlink.setOnAction(e -> FXUtils.openLink(Metadata.DOCS_URL + "/modpack/serverpack.html"));
                    borderPane.setTop(hyperlink);
                }
                if (skinnable.controller.getSettings().get(MODPACK_TYPE) == MODPACK_TYPE_MODRINTH) {
                    HintPane pane = new HintPane(MessageDialogPane.MessageType.INFO);
                    pane.setText(i18n("modpack.wizard.step.initialization.modrinth.info"));
                    borderPane.setTop(pane);
                } else {
                    HintPane pane = new HintPane(MessageDialogPane.MessageType.INFO);
                    pane.setText(i18n("modpack.wizard.step.initialization.warning"));
                    borderPane.setTop(pane);
                }

                {
                    ComponentList list = new ComponentList();
                    BorderPane.setMargin(list, componentListMargin);
                    borderPane.setCenter(list);

                    var instanceNamePane = new LineTextPane();
                    {
                        instanceNamePane.setTitle(i18n("modpack.wizard.step.initialization.exported_version"));
                        instanceNamePane.setText(skinnable.versionName);

                        list.getContent().add(instanceNamePane);
                    }


                    list.getContent().addAll(
                            createTextFieldLinePane(i18n("modpack.name"), skinnable.name, new RequiredValidator()),
                            createTextFieldLinePane(i18n("archive.author"), skinnable.author, new RequiredValidator()),
                            createTextFieldLinePane(i18n("archive.version"), skinnable.version, new RequiredValidator())
                    );

                    if (skinnable.options.isRequireFileApi()) {
                        list.getContent().add(createTextFieldLinePane(
                                i18n("modpack.file_api"), skinnable.fileApi,
                                skinnable.options.isValidateFileApi() ? new RequiredValidator() : null,
                                new URLValidator(true)
                        ));
                    }

                    if (skinnable.options.isRequireLaunchArguments()) {
                        list.getContent().add(createTextFieldLinePane(
                                i18n("settings.advanced.minecraft_arguments"), skinnable.launchArguments
                        ));
                    }

                    if (skinnable.options.isRequireJavaArguments()) {
                        list.getContent().add(createTextFieldLinePane(
                                i18n("settings.advanced.jvm_args"), skinnable.javaArguments
                        ));
                    }

                    if (skinnable.options.isRequireUrl()) {
                        list.getContent().add(createTextFieldLinePane(
                                i18n("modpack.origin.url"), skinnable.url
                        ));
                    }

                    if (skinnable.options.isRequireOrigins()) {
                        list.getContent().add(createTextFieldLinePane(
                                i18n("modpack.origin.mcbbs"), skinnable.mcbbsThreadId,
                                new NumberValidator(i18n("input.number"), true)
                        ));
                    }

                    if (skinnable.options.isRequireMinMemory()) {
                        VBox pane = new VBox();

                        Label title = new Label(i18n("settings.memory"));
                        VBox.setMargin(title, new Insets(0, 0, 8, 0));

                        HBox lowerBoundPane = new HBox(8);
                        lowerBoundPane.setAlignment(Pos.CENTER);
                        VBox.setMargin(lowerBoundPane, new Insets(0, 0, 0, 16));

                        {
                            Label label = new Label(i18n("settings.memory.lower_bound"));

                            JFXSlider slider = new JFXSlider(0, 1, 0);
                            HBox.setMargin(slider, new Insets(0, 0, 0, 8));
                            HBox.setHgrow(slider, Priority.ALWAYS);
                            slider.setValueFactory(self -> Bindings.createStringBinding(() -> (int) (self.getValue() * 100) + "%", self.valueProperty()));
                            AtomicBoolean changedByTextField = new AtomicBoolean(false);
                            FXUtils.onChangeAndOperate(skinnable.minMemory, minMemory -> {
                                changedByTextField.set(true);
                                slider.setValue(minMemory.intValue() * 1.0 / MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize()));
                                changedByTextField.set(false);
                            });
                            slider.valueProperty().addListener((value, oldVal, newVal) -> {
                                if (changedByTextField.get()) return;
                                skinnable.minMemory.set((int) (value.getValue().doubleValue() * MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize())));
                            });

                            JFXTextField txtMinMemory = new JFXTextField();
                            FXUtils.bindInt(txtMinMemory, skinnable.minMemory);
                            txtMinMemory.getValidators().add(new NumberValidator(i18n("input.number"), false));
                            FXUtils.setLimitWidth(txtMinMemory, 60);
                            validatingFields.add(txtMinMemory);

                            lowerBoundPane.getChildren().setAll(label, slider, txtMinMemory, new Label("MiB"));
                        }

                        pane.getChildren().setAll(title, lowerBoundPane);
                        list.getContent().add(pane);
                    }

                    {
                        VBox pane = new VBox(8);
                        JFXTextArea area = new JFXTextArea();
                        area.textProperty().bindBidirectional(skinnable.description);
                        area.setMinHeight(400);
                        pane.getChildren().setAll(new Label(i18n("modpack.desc")), area);
                        list.getContent().add(pane);
                    }

                    if (skinnable.options.isRequireAuthlibInjectorServer()) {
                        var serversSelectButton = new LineSelectButton<AuthlibInjectorServer>();
                        serversSelectButton.setTitle(i18n("account.injector.server"));
                        serversSelectButton.setConverter(AuthlibInjectorServer::getName);
                        serversSelectButton.setDescriptionConverter(AuthlibInjectorServer::getUrl);
                        serversSelectButton.itemsProperty().set(config().getAuthlibInjectorServers());

                        skinnable.authlibInjectorServer.bind(Bindings.createStringBinding(() -> {
                            AuthlibInjectorServer selected = serversSelectButton.getValue();
                            return selected != null ? selected.getUrl() : null;
                        }, serversSelectButton.valueProperty()));

                        list.getContent().add(serversSelectButton);
                    }

                    if (skinnable.options.isRequireForceUpdate()) {
                        var requireForceUpdateButton = new LineToggleButton();
                        requireForceUpdateButton.setTitle(i18n("modpack.wizard.step.initialization.force_update"));
                        requireForceUpdateButton.selectedProperty().bindBidirectional(skinnable.forceUpdate);

                        list.getContent().add(requireForceUpdateButton);
                    }

                    {
                        var canIncludeLauncherButton = new LineToggleButton();
                        canIncludeLauncherButton.setTitle(i18n("modpack.wizard.step.initialization.include_launcher"));
                        canIncludeLauncherButton.setDisable(!skinnable.canIncludeLauncher);
                        canIncludeLauncherButton.selectedProperty().bindBidirectional(skinnable.packWithLauncher);

                        list.getContent().add(canIncludeLauncherButton);
                    }

                    if (skinnable.options.isRequireNoCreateRemoteFiles()) {
                        var requireNoCreateRemoteFilesButton = new LineToggleButton();
                        requireNoCreateRemoteFilesButton.setTitle(i18n("modpack.wizard.step.initialization.no_create_remote_files"));
                        requireNoCreateRemoteFilesButton.selectedProperty().bindBidirectional(skinnable.noCreateRemoteFiles);

                        list.getContent().add(requireNoCreateRemoteFilesButton);
                    }

                    if (skinnable.options.isRequireSkipCurseForgeRemoteFiles()) {
                        var skipCurseForgeRemoteFilesButton = new LineToggleButton();
                        skipCurseForgeRemoteFilesButton.setTitle(i18n("modpack.wizard.step.initialization.skip_curseforge_remote_files"));
                        skipCurseForgeRemoteFilesButton.selectedProperty().bindBidirectional(skinnable.skipCurseForgeRemoteFiles);

                        list.getContent().add(skipCurseForgeRemoteFilesButton);
                    }
                }

                {
                    HBox hbox = new HBox();
                    hbox.setAlignment(Pos.CENTER_RIGHT);
                    borderPane.setBottom(hbox);

                    JFXButton nextButton = FXUtils.newRaisedButton(i18n("wizard.next"));
                    nextButton.setOnAction(e -> skinnable.onNext());
                    nextButton.setPrefWidth(100);
                    nextButton.setPrefHeight(40);
                    nextButton.disableProperty().bind(
                            // Disable nextButton if any text of JFXTextFields in validatingFields does not fulfill
                            // our requirement.
                            Bindings.createBooleanBinding(() -> validatingFields.stream()
                                            .map(field -> !field.validate())
                                            .reduce(false, (left, right) -> left || right),
                                    validatingFields.stream().map(JFXTextField::textProperty).toArray(StringProperty[]::new)));
                    hbox.getChildren().add(nextButton);
                }
            }

            FXUtils.smoothScrolling(scroll);
        }

        private LinePane createTextFieldLinePane(String title, StringProperty property, ValidatorBase... validators) {
            LinePane linePane = new LinePane();
            JFXTextField textField = new JFXTextField();
            textField.setMinWidth(500);

            linePane.setTitle(title);
            linePane.setRight(textField);
            textField.textProperty().bindBidirectional(property);

            boolean needValidation = false;
            if (validators != null) {
                for (ValidatorBase validator : validators) {
                    if (validator != null) {
                        needValidation = true;
                        textField.getValidators().add(validator);
                    }
                }
            }
            if (needValidation) {
                FXUtils.setValidateWhileTextChanged(textField, true);
                validatingFields.add(textField);
            }

            return linePane;
        }
    }
}
