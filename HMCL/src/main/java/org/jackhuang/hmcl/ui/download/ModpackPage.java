package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.LineTextPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class ModpackPage extends SpinnerPane implements WizardPage {
    public static final SettingsMap.Key<Profile> PROFILE = new SettingsMap.Key<>("PROFILE");

    protected final WizardController controller;

    protected final StringProperty nameProperty;
    protected final StringProperty versionProperty;
    protected final StringProperty authorProperty;
    protected final JFXTextField txtModpackName;
    protected final JFXButton btnInstall;
    protected final JFXButton btnDescription;
    protected final JFXButton btnOptionalFiles;

    protected ModpackPage(WizardController controller) {
        this.controller = controller;

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(borderPane, 500);

        ComponentList componentList = new ComponentList();
        {
            var archiveNamePane = new LinePane();
            {
                archiveNamePane.setTitle(i18n("archive.file.name"));

                txtModpackName = new JFXTextField();
                txtModpackName.setPrefWidth(300);
                BorderPane.setAlignment(txtModpackName, Pos.CENTER_RIGHT);
                archiveNamePane.setRight(txtModpackName);
            }

            var modpackNamePane = new LineTextPane();
            {
                modpackNamePane.setTitle(i18n("modpack.name"));
                nameProperty = modpackNamePane.textProperty();
            }

            var versionPane = new LineTextPane();
            {
                versionPane.setTitle(i18n("archive.version"));
                versionProperty = versionPane.textProperty();
            }

            var authorPane = new LineTextPane();
            {
                authorPane.setTitle(i18n("archive.author"));
                authorProperty = authorPane.textProperty();
            }

            var descriptionPane = new BorderPane();
            {
                btnDescription = FXUtils.newBorderButton(i18n("modpack.description"));
                btnDescription.setOnAction(e -> onDescribe());
                descriptionPane.setLeft(btnDescription);

                var installHBox = new HBox(8);
                btnOptionalFiles = FXUtils.newRaisedButton(i18n("modpack.optional_files"));
                installHBox.getChildren().add(btnOptionalFiles);

                btnInstall = FXUtils.newRaisedButton(i18n("button.install"));
                btnInstall.setOnAction(e -> onInstall());
                installHBox.getChildren().add(btnInstall);

                descriptionPane.setRight(installHBox);
            }

            componentList.getContent().setAll(
                    archiveNamePane, modpackNamePane, versionPane, authorPane, descriptionPane);
        }
        borderPane.getChildren().setAll(componentList);
        setContent(borderPane);
    }

    public void showSpinner() {
        super.showSpinner();
    }

    public void hideSpinner() {
        super.hideSpinner();
    }

    protected abstract void onInstall();

    protected abstract void onDescribe();

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
