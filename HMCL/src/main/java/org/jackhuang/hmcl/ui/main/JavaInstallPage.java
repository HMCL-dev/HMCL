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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.java.HMCLJavaRepository;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardSinglePage;
import org.jackhuang.hmcl.util.tree.ArchiveFileTreeSupplier;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class JavaInstallPage extends WizardSinglePage {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]+");

    private final ArchiveFileTreeSupplier<?, ?> treeSupplier;

    private final JavaInfo info;
    private final File file;
    private final StringProperty nameProperty = new SimpleStringProperty();

    public JavaInstallPage(Runnable onFinish, JavaInfo info, String defaultName, File file, ArchiveFileTreeSupplier<?, ?> treeSupplier) {
        super(onFinish);
        this.info = info;
        this.file = file;
        this.treeSupplier = treeSupplier;
        this.nameProperty.set(defaultName);
    }

    @Override
    protected SkinBase<?> createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    protected Object finish() {
        return JavaManager.installJava(info.getPlatform(), nameProperty.get(), null, treeSupplier);
    }

    @Override
    public String getTitle() {
        return i18n("java.install");
    }

    private static final class Skin extends SkinBase<JavaInstallPage> {

        private static void addInfo(ComponentList list, String name, String value) {
            BorderPane pane = new BorderPane();

            pane.setLeft(new Label(name));

            Label valueLabel = new Label(value);
            BorderPane.setAlignment(valueLabel, Pos.CENTER_RIGHT);
            pane.setCenter(valueLabel);

            list.getContent().add(pane);
        }

        private final JFXTextField nameField;

        private final Set<String> usedNames = new HashSet<>();

        Skin(JavaInstallPage control) {
            super(control);

            VBox borderPane = new VBox();
            borderPane.setAlignment(Pos.CENTER);
            FXUtils.setLimitWidth(borderPane, 500);

            ComponentList componentList = new ComponentList();
            {
                BorderPane namePane = new BorderPane();
                {
                    Label label = new Label(i18n("java.install.name"));
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                    namePane.setLeft(label);

                    nameField = new JFXTextField();
                    nameField.textProperty().bindBidirectional(control.nameProperty);
                    FXUtils.setLimitWidth(nameField, 200);
                    BorderPane.setAlignment(nameField, Pos.CENTER_RIGHT);
                    BorderPane.setMargin(nameField, new Insets(0, 0, 12, 0));
                    namePane.setRight(nameField);
                    nameField.setValidators(
                            new RequiredValidator(),
                            new Validator(i18n("java.install.warning.invalid_character"),
                                    text -> !text.startsWith(HMCLJavaRepository.MOJANG_JAVA_PREFIX) && NAME_PATTERN.matcher(text).matches()),
                            new Validator(i18n("java.install.failed.exists"), text -> !usedNames.contains(text))
                    );
                    nameField.textProperty().addListener(o -> nameField.validate());
                    nameField.validate();

                    componentList.getContent().add(namePane);
                }

                addInfo(componentList, i18n("java.install.archive"), control.file.getAbsolutePath());
                addInfo(componentList, i18n("java.info.version"), control.info.getVersion());
                addInfo(componentList, i18n("java.info.architecture"), control.info.getPlatform().getArchitecture().getDisplayName());

                String vendor = JavaInfo.normalizeVendor(control.info.getVendor());
                if (vendor != null)
                    addInfo(componentList, i18n("java.info.vendor"), vendor);

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
    }
}
