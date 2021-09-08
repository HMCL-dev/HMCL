/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.jfoenix.controls.*;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class FeedbackPage extends VBox {
    private final ObjectProperty<HMCLAccount> account = new SimpleObjectProperty<>();
    private final ObservableList<FeedbackResponse> feedbacks = FXCollections.observableArrayList();
    private final SpinnerPane spinnerPane = new SpinnerPane();

    public FeedbackPage() {
        setSpacing(10);
        setPadding(new Insets(10));

        {
            HBox loginPane = new HBox(16);
            loginPane.getStyleClass().add("card");

            TwoLineListItem accountInfo = new TwoLineListItem();
            accountInfo.titleProperty().bind(BindingMapping.of(account).map(account -> account == null ? i18n("account.not_logged_in") : account.getNickname()));
            accountInfo.subtitleProperty().bind(BindingMapping.of(account).map(account -> account == null ? i18n("account.not_logged_in") : account.getEmail()));

            JFXButton logButton = new JFXButton();
            logButton.textProperty().bind(BindingMapping.of(account).map(account -> account == null ? i18n("account.login") : i18n("account.logout")));
            logButton.setOnAction(e -> log());

            loginPane.getChildren().setAll(accountInfo, logButton);
            getChildren().add(loginPane);
        }

        {
            HBox searchPane = new HBox(8);
            searchPane.getStyleClass().add("card");
            getChildren().add(searchPane);

            JFXTextField searchField = new JFXTextField();
            searchField.setOnAction(e -> search(searchField.getText()));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchField.setPromptText(i18n("search"));

            JFXButton searchButton = new JFXButton();
            searchButton.getStyleClass().add("toggle-icon4");
            searchButton.setGraphic(SVG.magnify(Theme.blackFillBinding(), -1, -1));
            searchButton.setOnAction(e -> addFeedback());

            searchPane.getChildren().setAll(searchField, searchButton);
        }

        {
            spinnerPane.getStyleClass().add("card");
            VBox.setVgrow(spinnerPane, Priority.ALWAYS);
            JFXListView<FeedbackResponse> listView = new JFXListView<>();
            spinnerPane.setContent(listView);
            Bindings.bindContent(listView.getItems(), feedbacks);
            getChildren().add(spinnerPane);
        }
    }

    private void search(String keyword) {
        Task.supplyAsync(() -> {
            return HttpRequest.GET("https://hmcl.huangyuhui.net/api/feedback", pair("s", keyword)).<List<FeedbackResponse>>getJson(new TypeToken<List<FeedbackResponse>>(){}.getType());
        }).whenComplete(Schedulers.defaultScheduler(), (result, exception) -> {
            spinnerPane.hideSpinner();
            if (exception != null) {
                spinnerPane.setFailedReason(i18n("feedback.failed"));
            } else {
                feedbacks.setAll(result);
            }
        }).start();
    }

    private void log() {
        if (account.get() == null) {
            // login
            Controllers.dialog(new LoginDialog());
        } else {
            // logout
            account.set(null);
        }
    }

    private void addFeedback() {
        if (account.get() == null) {
            Controllers.dialog(i18n("feedback.add.login"));
            return;
        }

        Controllers.dialog(new AddFeedbackDialog());
    }

    private static class HMCLAccount {
        private final String nickname;
        private final String email;
        private final String accessToken;

        public HMCLAccount(String nickname, String email, String accessToken) {
            this.nickname = nickname;
            this.email = email;
            this.accessToken = accessToken;
        }

        public String getNickname() {
            return nickname;
        }

        public String getEmail() {
            return email;
        }

        public String getAccessToken() {
            return accessToken;
        }
    }

    private static class HMCLLoginResponse {
        private final int err;
        private final String nickname;
        private final String email;
        private final String accessToken;

        public HMCLLoginResponse(int err, String nickname, String email, String accessToken) {
            this.err = err;
            this.nickname = nickname;
            this.email = email;
            this.accessToken = accessToken;
        }

        public int getErr() {
            return err;
        }

        public String getNickname() {
            return nickname;
        }

        public String getEmail() {
            return email;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public static final int ERR_OK = 0;
        public static final int ERR_WRONG = 400;
    }

    private class LoginDialog extends JFXDialogLayout {
        private final SpinnerPane spinnerPane = new SpinnerPane();
        private final Label errorLabel = new Label();

        public LoginDialog() {
            setHeading(new Label(i18n("feedback.login")));

            GridPane body = new GridPane();
            ColumnConstraints fieldColumn = new ColumnConstraints();
            fieldColumn.setFillWidth(true);
            body.getColumnConstraints().setAll(new ColumnConstraints(), fieldColumn);
            body.setVgap(8);
            body.setHgap(8);
            setBody(body);

            JFXTextField usernameField = new JFXTextField();
            usernameField.setValidators(new RequiredValidator());
            body.addRow(0, new Label(i18n("account.username")), usernameField);

            JFXPasswordField passwordField = new JFXPasswordField();
            passwordField.setValidators(new RequiredValidator());
            body.addRow(1, new Label(i18n("account.password")), passwordField);

            JFXButton registerButton = new JFXButton();
            registerButton.setText(i18n("account.register"));
            registerButton.setOnAction(e -> FXUtils.openLink("https://hmcl.huangyuhui.net/user/login"));

            JFXButton loginButton = new JFXButton();
            spinnerPane.setContent(loginButton);
            loginButton.setText(i18n("account.login"));
            loginButton.setOnAction(e -> login(usernameField.getText(), passwordField.getText()));

            JFXButton cancelButton = new JFXButton();
            cancelButton.setText(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            onEscPressed(this, cancelButton::fire);

            setActions(errorLabel, registerButton, spinnerPane, cancelButton);
        }

        private void login(String username, String password) {
            spinnerPane.showSpinner();
            errorLabel.setText("");
            Task.supplyAsync(() -> {
                return HttpRequest.POST("https://hmcl.huangyuhui.net/api/user/login")
                        .json(mapOf(
                                pair("username", username),
                                pair("password", password)
                        )).getJson(HMCLLoginResponse.class);
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception != null) {
                    if (exception instanceof IOException) {
                        if (exception instanceof ResponseCodeException && ((ResponseCodeException) exception).getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                            errorLabel.setText(i18n("account.failed.invalid_password"));
                        } else {
                            errorLabel.setText(i18n("account.failed.connect_authentication_server"));
                        }
                    } else if (exception instanceof JsonParseException) {
                        errorLabel.setText(i18n("account.failed.server_response_malformed"));
                    } else {
                        errorLabel.setText(exception.getClass().getName() + ": " + exception.getLocalizedMessage());
                    }
                } else {
                    if (result.err == HMCLLoginResponse.ERR_OK) {
                        account.setValue(new HMCLAccount(result.getNickname(), result.getEmail(), result.getAccessToken()));
                        fireEvent(new DialogCloseEvent());
                    } else if (result.err == HMCLLoginResponse.ERR_WRONG) {
                        errorLabel.setText(i18n("account.failed.invalid_password"));
                    } else {
                        errorLabel.setText(i18n("account.failed", result.err));
                    }
                }
            }).start();
        }
    }

    private static class AddFeedbackDialog extends JFXDialogLayout {

        public AddFeedbackDialog() {
            setHeading(new Label(i18n("feedback.add")));

            GridPane body = new GridPane();
            body.setVgap(8);
            body.setHgap(8);

            HintPane searchHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            GridPane.setColumnSpan(searchHintPane, 2);
            searchHintPane.setText(i18n("feedback.add.hint.search_before_add"));
            body.addRow(0, searchHintPane);

            HintPane titleHintPane = new HintPane(MessageDialogPane.MessageType.INFORMATION);
            GridPane.setColumnSpan(titleHintPane, 2);
            titleHintPane.setText(i18n("feedback.add.hint.title"));
            body.addRow(1, titleHintPane);

            JFXTextField titleField = new JFXTextField();
            titleField.setValidators(new RequiredValidator());
            body.addRow(2, new Label(i18n("feedback.title")), titleField);

            JFXComboBox<FeedbackType> comboBox = new JFXComboBox<>();
            comboBox.setMaxWidth(-1);
            comboBox.getItems().setAll(FeedbackType.values());
            comboBox.getSelectionModel().select(0);
            comboBox.setConverter(stringConverter(e -> i18n("feedback.type." + e.name().toLowerCase())));
            body.addRow(3, new Label(i18n("feedback.type")), comboBox);

            Label contentLabel = new Label(i18n("feedback.content"));
            GridPane.setColumnSpan(contentLabel, 2);
            body.addRow(4, contentLabel);

            JFXTextArea contentArea = new JFXTextArea();
            contentArea.setValidators(new RequiredValidator());
            contentArea.setPromptText(i18n("feedback.add.hint.content"));
            GridPane.setColumnSpan(contentArea, 2);
            body.addRow(5, contentArea);

            setBody(body);

            JFXButton okButton = new JFXButton();
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> addFeedback(titleField.getText(), comboBox.getSelectionModel().getSelectedItem(), contentArea.getText()));

            JFXButton cancelButton = new JFXButton();
            cancelButton.setText(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            onEscPressed(this, cancelButton::fire);

            setActions(okButton, cancelButton);
        }

        private void addFeedback(String title, FeedbackType feedbackType, String content) {
            // TODO
        }
    }

    private static class FeedbackResponse {
        private final int id;
        private final String title;
        private final String content;
        private final String launcherVersion;
        private final String gameVersion;
        private final FeedbackType type;

        public FeedbackResponse(int id, String title, String content, String launcherVersion, String gameVersion, FeedbackType type) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.launcherVersion = launcherVersion;
            this.gameVersion = gameVersion;
            this.type = type;
        }

        public int getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public String getLauncherVersion() {
            return launcherVersion;
        }

        public String getGameVersion() {
            return gameVersion;
        }

        public FeedbackType getType() {
            return type;
        }
    }

    private enum FeedbackType {
        FEATURE_REQUEST,
        BUG_REPORT
    }
}
