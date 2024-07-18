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
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.java.HMCLJavaRepository;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.wizard.WizardSinglePage;
import org.jackhuang.hmcl.util.tree.ArchiveFileTreeSupplier;

import java.io.File;
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
        return JavaManager.installJava(info.getPlatform(), nameProperty.getName(), null, treeSupplier);
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

        private final BooleanProperty valid = new SimpleBooleanProperty(true);

        private final Label warningLabel = new Label();
        private final JFXTextField nameField;
        private final JFXButton installButton;

        Skin(JavaInstallPage control) {
            super(control);

            FXUtils.onChangeAndOperate(control.nameProperty, text -> {
                if (text == null || text.isEmpty()) {
                    warningLabel.setText("");
                    valid.set(false);
                    return;
                }

                if (text.startsWith(HMCLJavaRepository.MOJANG_JAVA_PREFIX) || !NAME_PATTERN.matcher(text).matches()) {
                    warningLabel.setText(i18n("java.install.warning.invalid_character"));
                    valid.set(false);
                    return;
                }

                warningLabel.setText("");
                valid.set(true);
            });

            VBox borderPane = new VBox();
            borderPane.setAlignment(Pos.CENTER);
            FXUtils.setLimitWidth(borderPane, 500);

            ComponentList componentList = new ComponentList();
            {
                addInfo(componentList, i18n("java.install.archive"), control.file.getAbsolutePath());
                addInfo(componentList, i18n("java.info.version"), control.info.getVersion());
                addInfo(componentList, i18n("java.info.architecture"), control.info.getPlatform().getArchitecture().getDisplayName());

                String vendor = JavaInfo.normalizeVendor(control.info.getVendor());
                if (vendor != null)
                    addInfo(componentList, i18n("java.info.vendor"), vendor);

                BorderPane namePane = new BorderPane();
                {
                    Label label = new Label(i18n("java.install.name"));
                    BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                    namePane.setLeft(label);

                    nameField = new JFXTextField();
                    nameField.textProperty().bindBidirectional(control.nameProperty);
                    nameField.setMaxWidth(200);
                    BorderPane.setAlignment(namePane, Pos.CENTER_RIGHT);
                    namePane.setRight(nameField);

                    componentList.getContent().add(namePane);
                }

                BorderPane installPane = new BorderPane();
                {
                    this.installButton = FXUtils.newRaisedButton(i18n("button.install"));
                    installButton.setOnAction(e -> {
                        if (JavaManager.REPOSITORY.isInstalled(control.info.getPlatform(), nameField.getText())) {
                            warningLabel.setText(i18n("java.install.failed.exists"));
                            valid.set(false);
                        } else
                            control.onFinish.run();
                    });
                    installButton.disableProperty().bind(valid.not());
                    installPane.setRight(installButton);

                    componentList.getContent().add(installPane);
                }
            }

            borderPane.getChildren().setAll(componentList);
            this.getChildren().setAll(borderPane);
        }
    }
}
