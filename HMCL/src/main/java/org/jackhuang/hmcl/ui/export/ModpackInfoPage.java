// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.export;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3SelectSettingItem;
import org.glavo.m3fx.controls.M3SwitchSettingItem;
import org.glavo.m3fx.controls.M3TextArea;
import org.glavo.m3fx.controls.M3FormRow;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputValidator;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackManifest;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.jackhuang.hmcl.setting.SettingsManager.getAuthlibInjectorServers;
import static org.jackhuang.hmcl.ui.export.ModpackTypeSelectionPage.*;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackInfoPage extends Control implements WizardPage {
    private final WizardController controller;
    private final HMCLGameInstance gameInstance;
    private final ModpackExportInfo.Options options;
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

    public ModpackInfoPage(WizardController controller, HMCLGameInstance gameInstance) {
        this.controller = controller;
        this.gameInstance = gameInstance;
        this.options = controller.getSettings().get(MODPACK_INFO_OPTION);

        if (this.options == null)
            throw new IllegalArgumentException("Settings.MODPACK_INFO_OPTION is required");

        name.set(gameInstance.getId().toString());
        author.set(Optional.ofNullable(Accounts.getSelectedAccount()).map(Account::getProfileName).orElse(""));

        GameSettings.Effective versionSetting = gameInstance.getEffectiveSettings();
        minMemory.set(Optional.ofNullable(versionSetting.getInheritable(GameSettings::minMemoryProperty)).orElse(0));
        launchArguments.set(versionSetting.getInheritable(GameSettings::gameArgumentsProperty));
        javaArguments.set(versionSetting.getInheritable(GameSettings::jvmOptionsProperty));

        canIncludeLauncher = JarUtils.thisJarPath() != null;
    }

    private void onNext() {
        exportInfo.setName(name.get())
                .setFileApi(fileApi.get())
                .setVersion(version.get())
                .setAuthor(author.get())
                .setDescription(description.get())
                .setUrl(url.get())
                .setForceUpdate(forceUpdate.get())
                .setPackWithLauncher(packWithLauncher.get())
                .setMinMemory(minMemory.get())
                .setLaunchArguments(launchArguments.get())
                .setJavaArguments(javaArguments.get())
                .setAuthlibInjectorServer(authlibInjectorServer.get())
                .setNoCreateRemoteFiles(noCreateRemoteFiles.get())
                .setSkipCurseForgeRemoteFiles(skipCurseForgeRemoteFiles.get());

        if (StringUtils.isNotBlank(mcbbsThreadId.get())) {
            exportInfo.setOrigins(Collections.singletonList(new McbbsModpackManifest.Origin(
                    "mcbbs", Integer.parseInt(mcbbsThreadId.get())
            )));
        }

        controller.getSettings().put(MODPACK_INFO, exportInfo);
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
    public static final SettingsMap.Key<ModpackExportInfo.Options> MODPACK_INFO_OPTION = new SettingsMap.Key<>("modpack.info.option");

    public static class ModpackInfoPageSkin extends SkinBase<ModpackInfoPage> {
        private final List<M3TextField> validatingFields = new ArrayList<>();

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

                if (MODPACK_TYPE_SERVER.equals(skinnable.controller.getSettings().get(MODPACK_TYPE))) {
                    Hyperlink hyperlink = new Hyperlink(i18n("modpack.wizard.step.initialization.server"));
                    hyperlink.setOnAction(e -> FXUtils.openLink(Metadata.DOCS_URL + "/modpack/serverpack.html"));
                    borderPane.setTop(hyperlink);
                }
                if (MODPACK_TYPE_MODRINTH.equals(skinnable.controller.getSettings().get(MODPACK_TYPE))) {
                    HintPane pane = new HintPane(MessageType.INFO);
                    pane.setText(i18n("modpack.wizard.step.initialization.modrinth.info"));
                    borderPane.setTop(pane);
                } else {
                    HintPane pane = new HintPane(MessageType.INFO);
                    pane.setText(i18n("modpack.wizard.step.initialization.warning"));
                    borderPane.setTop(pane);
                }

                {
                    ComponentList list = new ComponentList();
                    BorderPane.setMargin(list, componentListMargin);
                    borderPane.setCenter(list);

                    var instanceName = new M3FormRow(i18n("modpack.wizard.step.initialization.exported_version"),
                            FXUtils.newCopyableLabel(skinnable.gameInstance.getId().toString()));
                    list.getContent().add(instanceName);

                    list.getContent().addAll(
                            createTextFieldRow(i18n("modpack.name"), skinnable.name, new RequiredValidator()),
                            createTextFieldRow(i18n("archive.version"), skinnable.version, new RequiredValidator())
                    );

                    if (skinnable.options.isRequireAuthor()) {
                        list.getContent().add(
                                createTextFieldRow(i18n("archive.author"), skinnable.author, new RequiredValidator())
                        );
                    }

                    if (skinnable.options.isRequireFileApi()) {
                        list.getContent().add(createTextFieldRow(
                                i18n("modpack.file_api"), skinnable.fileApi,
                                (input, text) -> skinnable.options.isValidateFileApi() && StringUtils.isBlank(text)
                                        ? i18n("input.not_empty") : null,
                                new URLValidator(true)
                        ));
                    }

                    if (skinnable.options.isRequireLaunchArguments()) {
                        list.getContent().add(createTextFieldRow(
                                i18n("settings.advanced.minecraft_arguments"), skinnable.launchArguments
                        ));
                    }

                    if (skinnable.options.isRequireJavaArguments()) {
                        list.getContent().add(createTextFieldRow(
                                i18n("settings.advanced.jvm_args"), skinnable.javaArguments
                        ));
                    }

                    if (skinnable.options.isRequireUrl()) {
                        list.getContent().add(createTextFieldRow(
                                i18n("modpack.origin.url"), skinnable.url
                        ));
                    }

                    if (skinnable.options.isRequireOrigins()) {
                        list.getContent().add(createTextFieldRow(
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

                            var slider = FXUtils.newPercentageSlider();
                            HBox.setMargin(slider, new Insets(0, 0, 0, 8));
                            HBox.setHgrow(slider, Priority.ALWAYS);
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

                            M3TextField txtMinMemory = FXUtils.newTextField();
                            FXUtils.bindInt(txtMinMemory, skinnable.minMemory);
                            var memoryLayout = FXUtils.newValidatedTextField(txtMinMemory, new NumberValidator(i18n("input.number"), false));
                            FXUtils.setLimitWidth(memoryLayout, 88);
                            validatingFields.add(txtMinMemory);

                            lowerBoundPane.getChildren().setAll(label, slider, memoryLayout, new Label("MiB"));
                        }

                        pane.getChildren().setAll(title, lowerBoundPane);
                        list.getContent().add(pane);
                    }

                    {
                        VBox pane = new VBox(8);
                        M3TextArea area = new M3TextArea();
                        area.setVariant(M3TextInputVariant.OUTLINED);
                        area.setWrapText(false);
                        FXUtils.installTextContextMenu(area);
                        area.textProperty().bindBidirectional(skinnable.description);
                        area.setMinHeight(400);
                        pane.getChildren().setAll(new Label(i18n("modpack.desc")), area);
                        list.getContent().add(pane);
                    }

                    if (skinnable.options.isRequireAuthlibInjectorServer()) {
                        var serversSelectButton = new M3SelectSettingItem<AuthlibInjectorServer>();
                        serversSelectButton.setHeadlineText(i18n("account.injector.server"));
                        serversSelectButton.setConverter(AuthlibInjectorServer::getName);
                        serversSelectButton.setDescriptionConverter(AuthlibInjectorServer::getUrl);
                        serversSelectButton.itemsProperty().set(getAuthlibInjectorServers());

                        skinnable.authlibInjectorServer.bind(Bindings.createStringBinding(() -> {
                            AuthlibInjectorServer selected = serversSelectButton.getValue();
                            return selected != null ? selected.getUrl() : null;
                        }, serversSelectButton.valueProperty()));

                        list.getContent().add(serversSelectButton);
                    }

                    if (skinnable.options.isRequireForceUpdate()) {
                        var requireForceUpdateButton = new M3SwitchSettingItem();
                        requireForceUpdateButton.setHeadlineText(i18n("modpack.wizard.step.initialization.force_update"));
                        requireForceUpdateButton.selectedProperty().bindBidirectional(skinnable.forceUpdate);

                        list.getContent().add(requireForceUpdateButton);
                    }

                    {
                        var canIncludeLauncherButton = new M3SwitchSettingItem();
                        canIncludeLauncherButton.setHeadlineText(i18n("modpack.wizard.step.initialization.include_launcher"));
                        canIncludeLauncherButton.setDisable(!skinnable.canIncludeLauncher);
                        canIncludeLauncherButton.selectedProperty().bindBidirectional(skinnable.packWithLauncher);

                        list.getContent().add(canIncludeLauncherButton);
                    }

                    if (skinnable.options.isRequireNoCreateRemoteFiles()) {
                        var requireNoCreateRemoteFilesButton = new M3SwitchSettingItem();
                        requireNoCreateRemoteFilesButton.setHeadlineText(i18n("modpack.wizard.step.initialization.no_create_remote_files"));
                        requireNoCreateRemoteFilesButton.selectedProperty().bindBidirectional(skinnable.noCreateRemoteFiles);

                        list.getContent().add(requireNoCreateRemoteFilesButton);
                    }

                    if (skinnable.options.isRequireSkipCurseForgeRemoteFiles()) {
                        var skipCurseForgeRemoteFilesButton = new M3SwitchSettingItem();
                        skipCurseForgeRemoteFilesButton.setHeadlineText(i18n("modpack.wizard.step.initialization.skip_curseforge_remote_files"));
                        skipCurseForgeRemoteFilesButton.selectedProperty().bindBidirectional(skinnable.skipCurseForgeRemoteFiles);

                        list.getContent().add(skipCurseForgeRemoteFilesButton);
                    }
                }

                {
                    HBox hbox = new HBox();
                    hbox.setAlignment(Pos.CENTER_RIGHT);
                    borderPane.setBottom(hbox);

                    M3Button nextButton = new M3Button(I18n.i18n("wizard.next"), M3ButtonVariant.FILLED);
                    nextButton.setOnAction(e -> skinnable.onNext());
                    nextButton.setPrefWidth(100);
                    nextButton.setPrefHeight(40);
                    nextButton.disableProperty().bind(
                            Bindings.createBooleanBinding(() -> validatingFields.stream().anyMatch(M3TextField::isError),
                                    validatingFields.stream().map(M3TextField::errorProperty).toArray(Observable[]::new)));
                    hbox.getChildren().add(nextButton);
                }
            }

            ScrollUtils.installSmoothScrolling(scroll);
        }

        /// Creates a bound text editor and registers its optional validation.
        private M3FormRow createTextFieldRow(String title, StringProperty property, M3TextInputValidator... validators) {
            M3FormRow row = new M3FormRow();
            M3TextField textField = FXUtils.newTextField();
            textField.textProperty().bindBidirectional(property);
            row.setLabelText(title);
            if (validators.length == 0) {
                textField.setPrefWidth(500);
                row.setContent(textField);
            } else {
                var layout = FXUtils.newValidatedTextField(textField, validators);
                layout.setPrefWidth(500);
                row.setContent(layout);
                validatingFields.add(textField);
            }
            return row;
        }
    }
}
