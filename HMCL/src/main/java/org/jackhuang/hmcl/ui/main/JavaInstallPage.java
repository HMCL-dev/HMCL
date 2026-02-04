/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.java.JavaRemoteVersion;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaDistribution;
import org.jackhuang.hmcl.download.java.disco.DiscoJavaRemoteVersion;
import org.jackhuang.hmcl.java.HMCLJavaRepository;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.WizardSinglePage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class JavaInstallPage extends WizardSinglePage {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]+");

    private final Path file;

    private final JavaInfo info;
    private final JavaRemoteVersion remoteVersion;
    private final Map<String, Object> update;
    private final StringProperty nameProperty = new SimpleStringProperty();

    public JavaInstallPage(Runnable onFinish, JavaInfo info, JavaRemoteVersion remoteVersion, Map<String, Object> update, String defaultName, Path file) {
        super(onFinish);
        this.info = info;
        this.remoteVersion = remoteVersion;
        this.update = update;
        this.file = file;
        this.nameProperty.set(defaultName);
    }

    @Override
    protected SkinBase<?> createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    protected Object finish() {
        Task<JavaRuntime> installTask = JavaManager.getInstallJavaTask(info.getPlatform(), nameProperty.get(), update, file);
        return remoteVersion == null ? installTask : installTask.whenComplete(exception -> {
            try {
                Files.delete(file);
            } catch (IOException e) {
                LOG.warning("Failed to delete file: " + file, e);
            }
        });
    }

    @Override
    public String getTitle() {
        return i18n("java.install");
    }

    private static final class Skin extends SkinBase<JavaInstallPage> {

        private final ComponentList componentList = new ComponentList();

        private final JFXTextField nameField;

        private final Set<String> usedNames = new HashSet<>();

        Skin(JavaInstallPage control) {
            super(control);

            VBox borderPane = new VBox();
            borderPane.setAlignment(Pos.CENTER);
            FXUtils.setLimitWidth(borderPane, 500);


            {
                var namePane = new LinePane();
                {
                    namePane.setTitle(i18n("java.install.name"));

                    nameField = new JFXTextField();
                    nameField.textProperty().bindBidirectional(control.nameProperty);
                    FXUtils.setLimitWidth(nameField, 200);
                    namePane.setRight(nameField);
                    nameField.setValidators(
                            new RequiredValidator(),
                            new Validator(i18n("java.install.warning.invalid_character"),
                                    text -> !text.startsWith(HMCLJavaRepository.MOJANG_JAVA_PREFIX) && NAME_PATTERN.matcher(text).matches()),
                            new Validator(i18n("java.install.failed.exists"), text -> !usedNames.contains(text))
                    );
                    String defaultName = control.nameProperty.get();
                    if (JavaManager.REPOSITORY.isInstalled(control.info.getPlatform(), defaultName)) {
                        usedNames.add(defaultName);
                    }
                    nameField.textProperty().addListener(o -> nameField.validate());
                    nameField.validate();

                    componentList.getContent().add(namePane);
                }

                String vendor = JavaInfo.normalizeVendor(control.info.getVendor());
                if (vendor != null)
                    addInfo(i18n("java.info.vendor"), vendor);

                if (control.remoteVersion instanceof DiscoJavaRemoteVersion) {
                    String distributionName = ((DiscoJavaRemoteVersion) control.remoteVersion).getDistribution();
                    DiscoJavaDistribution distribution = DiscoJavaDistribution.of(distributionName);
                    addInfo(i18n("java.info.disco.distribution"), distribution != null ? distribution.getDisplayName() : distributionName);
                } else
                    addInfo(i18n("java.install.archive"), control.file.toAbsolutePath().toString());

                addInfo(i18n("java.info.version"), control.info.getVersion());
                addInfo(i18n("java.info.architecture"), control.info.getPlatform().getArchitecture().getDisplayName());

                BorderPane installPane = new BorderPane();
                {
                    JFXButton installButton = FXUtils.newRaisedButton(i18n("button.install"));
                    installButton.setOnAction(e -> {
                        String name = control.nameProperty.get();
                        if (JavaManager.REPOSITORY.isInstalled(control.info.getPlatform(), name)) {
                            Controllers.dialog(i18n("java.install.failed.exists"), null, MessageDialogPane.MessageType.WARNING);
                            usedNames.add(name);
                            nameField.validate();
                        } else
                            control.onFinish.run();
                    });
                    installButton.disableProperty().bind(nameField.activeValidatorProperty().isNotNull());
                    installPane.setRight(installButton);

                    componentList.getContent().add(installPane);
                }
            }

            borderPane.getChildren().setAll(componentList);
            this.getChildren().setAll(borderPane);
        }

        private void addInfo(String name, String value) {
            LineTextPane pane = new LineTextPane();
            pane.setTitle(name);
            pane.setText(value);
            this.componentList.getContent().add(pane);
        }
    }
}
