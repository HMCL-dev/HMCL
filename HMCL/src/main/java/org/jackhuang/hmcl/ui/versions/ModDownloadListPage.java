package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModDownloadListPage extends Control {
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    /**
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MODPACK
     * @see org.jackhuang.hmcl.mod.curse.CurseModManager#SECTION_MOD
     */
    private final int section;
    private Profile profile;
    private String version;

    public ModDownloadListPage(int section) {
        this.section = section;
    }

    public void loadVersion(Profile profile, String version) {
        this.profile = profile;
        this.version = version;

        setLoading(false);
        setFailed(false);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public void search(String gameVersion, int category, int pageOffset, String searchFilter, int sort) {
        setLoading(true);
        Task.supplyAsync(() -> CurseModManager.searchPaginated(gameVersion, category, section, pageOffset, searchFilter, sort))
        .whenComplete(Schedulers.javafx(), (exception, result) -> {
            setLoading(false);
            if (exception == null) {

            } else {

            }
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadListPageSkin(this);
    }

    private static class ModDownloadListPageSkin extends SkinBase<ModDownloadListPage> {

        protected ModDownloadListPageSkin(ModDownloadListPage control) {
            super(control);

            VBox pane = new VBox();
            pane.getStyleClass().add("card-list");

            GridPane searchPane = new GridPane();
            searchPane.getStyleClass().add("card");

            ColumnConstraints column1 = new ColumnConstraints();
            column1.setPercentWidth(50);
            column1.setHgrow(Priority.ALWAYS);
            ColumnConstraints column2 = new ColumnConstraints();
            column2.setHgrow(Priority.ALWAYS);
            column2.setPercentWidth(50);
            searchPane.getColumnConstraints().setAll(column1, column2);

            searchPane.setHgap(16);
            searchPane.setVgap(10);

            {
                JFXTextField nameField = new JFXTextField();
                nameField.setPromptText(i18n("mods.name"));
                searchPane.add(nameField, 0, 0);

                JFXTextField gameVersionField = new JFXTextField();
                gameVersionField.setPromptText(i18n("world.game_version"));
                searchPane.add(gameVersionField, 1, 0);

                JFXTextField categoryField = new JFXTextField();
                categoryField.setPromptText(i18n("mods.category"));
                searchPane.add(categoryField, 0, 1);

                JFXTextField sortField = new JFXTextField();
                sortField.setPromptText(i18n("search.sort"));
                searchPane.add(sortField, 1, 1);

                VBox vbox = new VBox();
                vbox.setAlignment(Pos.CENTER_RIGHT);
                searchPane.add(vbox, 0, 2, 2, 1);

                JFXButton searchButton = new JFXButton();
                searchButton.setText(i18n("search"));
                searchButton.setOnAction(e -> {
                    getSkinnable().search(gameVersionField.getText(), categoryField.getText(), 0, nameField.getText(), sortField.getText())
                    .whenComplete();
                });
                searchPane.add(searchButton, 0, 2);
                vbox.getChildren().setAll(searchButton);
            }

            TransitionPane transitionPane = new TransitionPane();
            {

                SpinnerPane spinnerPane = new SpinnerPane();
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());

            }

            pane.getChildren().setAll(searchPane, transitionPane);

            getChildren().setAll(pane);
        }
    }
}
