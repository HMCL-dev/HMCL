package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.OptionalFilesSelectionPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class ModpackPage extends ScrollPane implements WizardPage {
    protected final WizardController controller;

    protected final SpinnerPane spinner;
    protected final Label lblName;
    protected final Label lblVersion;
    protected final Label lblAuthor;
    protected final JFXTextField txtModpackName;
    protected final JFXButton btnInstall;
    protected final JFXButton btnDescription;
    protected final OptionalFilesSelectionPane optionalFiles;
    protected final BooleanProperty waitingForOptionalFiles = new SimpleBooleanProperty(false);

    protected ModpackPage(WizardController controller) {
        this.controller = controller;

        this.getStyleClass().add("large-spinner-pane");

        setFitToHeight(true);
        setFitToWidth(true);
        spinner = new SpinnerPane();

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(borderPane, 500);

        ComponentList componentList = new ComponentList();
        {
            BorderPane archiveNamePane = new BorderPane();
            {
                Label label = new Label(i18n("archive.file.name"));
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

            optionalFiles = new OptionalFilesSelectionPane();

            BorderPane descriptionPane = new BorderPane();
            {
                btnDescription = new JFXButton(i18n("modpack.description"));
                btnDescription.getStyleClass().add("jfx-button-border");
                btnDescription.setOnAction(e -> onDescribe());
                descriptionPane.setLeft(btnDescription);

                btnInstall = FXUtils.newRaisedButton(i18n("button.install"));
                btnInstall.setOnAction(e -> onInstall());
                btnInstall.disableProperty().bind(createBooleanBinding(() -> !txtModpackName.validate() || waitingForOptionalFiles.get(),
                                txtModpackName.textProperty(), waitingForOptionalFiles));
                descriptionPane.setRight(btnInstall);
            }

            componentList.getContent().setAll(
                    archiveNamePane, modpackNamePane, versionPane, authorPane, descriptionPane, optionalFiles);
        }
        borderPane.getChildren().setAll(componentList);
        spinner.setContent(borderPane);
        this.setContent(spinner);
    }

    protected void showSpinner() {
        spinner.showSpinner();
    }

    protected void hideSpinner() {
        spinner.hideSpinner();
    }

    protected abstract void onInstall();

    protected abstract void onDescribe();

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
