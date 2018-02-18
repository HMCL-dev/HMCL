package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.yggdrasil.AuthlibInjectorServerInfo;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public class AuthlibInjectorServersPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", Main.i18n("account.injector.server"));

    @FXML private ScrollPane scrollPane;
    @FXML private StackPane addServerContainer;
    @FXML private Label lblServerIp;
    @FXML private Label lblServerName;
    @FXML private Label lblCreationWarning;
    @FXML private Label lblServerWarning;
    @FXML private VBox listPane;
    @FXML private JFXTextField txtServerIp;
    @FXML private JFXDialogLayout addServerPane;
    @FXML private JFXDialogLayout confirmServerPane;
    @FXML private JFXDialog dialog;
    @FXML private StackPane contentPane;
    @FXML private JFXSpinner spinner;
    @FXML private JFXProgressBar progressBar;
    @FXML private JFXButton btnAddNext;

    private TransitionHandler transitionHandler;

    {
        FXUtils.loadFXML(this, "/assets/fxml/authlib-injector-servers.fxml");
        FXUtils.smoothScrolling(scrollPane);
        transitionHandler = new TransitionHandler(addServerContainer);

        getChildren().remove(dialog);
        dialog.setDialogContainer(this);

        txtServerIp.textProperty().addListener((a, b, newValue) -> {
            btnAddNext.setDisable(!txtServerIp.validate());
        });

        loading();
    }

    private void removeServer(AuthlibInjectorServerItem item) {
        Settings.INSTANCE.removeAuthlibInjectorServerURL(item.getInfo().getServerIp());
        loading();
    }

    private void loading() {
        getChildren().remove(contentPane);
        spinner.setVisible(true);

        Task.ofResult("list", () -> Settings.INSTANCE.getAuthlibInjectorServerURLs().parallelStream()
                .map(serverURL -> new AuthlibInjectorServerItem(new AuthlibInjectorServerInfo(serverURL, Accounts.getAuthlibInjectorServerName(serverURL)), this::removeServer))
                .collect(Collectors.toList()))
                .subscribe(Task.of(Schedulers.javafx(), variables -> {
                    listPane.getChildren().setAll(variables.<Collection<? extends Node>>get("list"));
                    loadingCompleted();
                }));
    }

    private void loadingCompleted() {
        getChildren().add(contentPane);
        spinner.setVisible(false);

        if (Settings.INSTANCE.getAuthlibInjectorServerURLs().isEmpty())
            onAdd();
    }

    @FXML
    private void onAdd() {
        transitionHandler.setContent(addServerPane, ContainerAnimations.NONE.getAnimationProducer());
        txtServerIp.setText("");
        addServerPane.setDisable(false);
        progressBar.setVisible(false);
        dialog.show();
    }

    @FXML
    private void onAddCancel() {
        dialog.close();
    }

    @FXML
    private void onAddNext() {
        String serverIp = txtServerIp.getText();
        progressBar.setVisible(true);
        addServerPane.setDisable(true);

        Task.ofResult("serverName", () -> Objects.requireNonNull(Accounts.getAuthlibInjectorServerName(serverIp)))
                .finalized(Schedulers.javafx(), (variables, isDependentsSucceeded) -> {
                    progressBar.setVisible(false);
                    addServerPane.setDisable(false);

                    if (isDependentsSucceeded) {
                        lblServerName.setText(variables.get("serverName"));
                        lblServerIp.setText(txtServerIp.getText());

                        lblServerWarning.setVisible("http".equals(NetworkUtils.toURL(serverIp).getProtocol()));

                        transitionHandler.setContent(confirmServerPane, ContainerAnimations.SWIPE_LEFT.getAnimationProducer());
                    } else
                        lblCreationWarning.setText(variables.<Exception>get("lastException").getLocalizedMessage());
                }).start();


    }

    @FXML
    private void onAddPrev() {
        transitionHandler.setContent(addServerPane, ContainerAnimations.SWIPE_RIGHT.getAnimationProducer());
    }

    @FXML
    private void onAddFinish() {
        String ip = txtServerIp.getText();
        if (!ip.endsWith("/"))
            ip += "/";
        Settings.INSTANCE.addAuthlibInjectorServerURL(ip);
        loading();
        dialog.close();
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
