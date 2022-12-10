package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class ModpackPage extends SpinnerPane implements WizardPage {
    protected final WizardController controller;

    protected final Label lblName;
    protected final Label lblVersion;
    protected final Label lblAuthor;
    protected final Label lblModpackLocation;
    protected final JFXTextField txtModpackName;
    protected final JFXButton btnInstall;

    protected ModpackPage(WizardController controller) {
        this.controller = controller;

        this.getStyleClass().add("large-spinner-pane");

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(borderPane, 500);

        ComponentList componentList = new ComponentList();
        {
            BorderPane locationPane = new BorderPane();
            {
                locationPane.setLeft(new Label(i18n("modpack.task.install.will")));

                lblModpackLocation = new Label();
                BorderPane.setAlignment(lblModpackLocation, Pos.CENTER_RIGHT);
                locationPane.setCenter(lblModpackLocation);
            }

            BorderPane archiveNamePane = new BorderPane();
            {
                Label label = new Label(i18n("archive.name"));
                BorderPane.setAlignment(label, Pos.CENTER_LEFT);
                archiveNamePane.setLeft(label);

                txtModpackName = new JFXTextField();
                BorderPane.setMargin(txtModpackName, new Insets(0, 0, 8, 32));
                BorderPane.setAlignment(txtModpackName, Pos.CENTER_RIGHT);
                archiveNamePane.setCenter(txtModpackName);
            }

            BorderPane modpackNamePane = new BorderPane();
            {
                modpackNamePane.setLeft(new Label(i18n("modpack.name")));

                lblName = new Label();
                BorderPane.setAlignment(lblName, Pos.CENTER_RIGHT);
                modpackNamePane.setCenter(lblName);
            }

            BorderPane versionPane = new BorderPane();
            {
                versionPane.setLeft(new Label(i18n("archive.version")));

                lblVersion = new Label();
                BorderPane.setAlignment(lblVersion, Pos.CENTER_RIGHT);
                versionPane.setCenter(lblVersion);
            }

            BorderPane authorPane = new BorderPane();
            {
                authorPane.setLeft(new Label(i18n("archive.author")));

                lblAuthor = new Label();
                BorderPane.setAlignment(lblAuthor, Pos.CENTER_RIGHT);
                authorPane.setCenter(lblAuthor);
            }

            BorderPane descriptionPane = new BorderPane();
            {
                JFXButton btnDescription = new JFXButton(i18n("modpack.description"));
                btnDescription.getStyleClass().add("jfx-button-border");
                btnDescription.setOnAction(e -> onDescribe());
                descriptionPane.setLeft(btnDescription);

                btnInstall = new JFXButton(i18n("button.install"));
                btnInstall.getStyleClass().add("jfx-button-raised");
                btnInstall.setButtonType(JFXButton.ButtonType.RAISED);
                btnInstall.setOnAction(e -> onInstall());
                btnInstall.disableProperty().bind(createBooleanBinding(() -> !txtModpackName.validate(), txtModpackName.textProperty()));
                descriptionPane.setRight(btnInstall);
            }

            componentList.getContent().setAll(
                    locationPane, archiveNamePane, modpackNamePane, versionPane, authorPane, descriptionPane);
        }

        borderPane.getChildren().setAll(componentList);
        this.setContent(borderPane);
    }

    protected abstract void onInstall();

    protected abstract void onDescribe();

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
