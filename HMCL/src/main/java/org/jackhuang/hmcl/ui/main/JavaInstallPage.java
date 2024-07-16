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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.java.HMCLJavaRepository;
import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.tree.ArchiveFileTreeSupplier;

import java.util.concurrent.CancellationException;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class JavaInstallPage extends StackPane implements DecoratorPage {

    private static final Pattern NAME_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]+");

    private static void addInfo(ComponentList list, String name, String value) {
        BorderPane pane = new BorderPane();

        pane.setLeft(new Label(name));

        Label valueLabel = new Label(value);
        BorderPane.setAlignment(valueLabel, Pos.CENTER_RIGHT);
        pane.setCenter(valueLabel);

        list.getContent().add(pane);
    }

    private final ArchiveFileTreeSupplier<?, ?> fileSupplier;

    private final HMCLJavaRepository repository;
    private final JavaInfo info;

    private final BooleanProperty valid = new SimpleBooleanProperty(true);
    private final ReadOnlyObjectWrapper<State> state;

    private final Label warningLabel = new Label();
    private final JFXTextField nameField = new JFXTextField();
    private final JFXButton installButton;

    public JavaInstallPage(HMCLJavaRepository repository, JavaInfo info, String defaultName, ArchiveFileTreeSupplier<?, ?> fileSupplier) {
        this.repository = repository;
        this.info = info;
        this.fileSupplier = fileSupplier;
        this.state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("java.install")));

        this.nameField.setText(defaultName);

        FXUtils.onChangeAndOperate(nameField.textProperty(), text -> {
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
            addInfo(componentList, i18n("java.info.version"), info.getVersion());
            addInfo(componentList, i18n("java.info.architecture"), info.getPlatform().getArchitecture().getDisplayName());

            String vendor = JavaInfo.normalizeVendor(info.getVendor());
            if (vendor != null)
                addInfo(componentList, i18n("java.info.vendor"), vendor);

            BorderPane installPane = new BorderPane();
            {
                this.installButton = FXUtils.newRaisedButton(i18n("button.install"));
                installButton.setOnAction(e -> onInstall());
                installButton.disableProperty().bind(valid.not());
                installPane.setRight(installButton);

                componentList.getContent().add(installPane);
            }
        }

        borderPane.getChildren().setAll(componentList);
        this.getChildren().setAll(borderPane);
    }

    private void onInstall() {
        String name = nameField.getText();
        if (repository.isInstalled(info.getPlatform(), name)) {
            warningLabel.setText(i18n("java.install.failed.exists"));
            valid.set(false);
        } else {
            Controllers.taskDialog(JavaManager.installJava(info.getPlatform(), name, null, fileSupplier)
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception != null) {
                                    Throwable resolvedException = resolveException(exception);
                                    LOG.warning("Failed to install java", exception);
                                    if (!(resolvedException instanceof CancellationException)) {
                                        Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                                    }
                                }
                            }),
                    i18n("java.install"), TaskCancellationAction.NORMAL);
        }
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
