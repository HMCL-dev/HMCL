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
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.jfoenix.controls.*;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.OAuthServer;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.HMCLAccounts;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.FXUtils.stringConverter;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class FeedbackPage extends VBox implements PageAware {
    private final ObservableList<FeedbackResponse> feedbacks = FXCollections.observableArrayList();
    private final SpinnerPane spinnerPane = new SpinnerPane();

    public FeedbackPage() {
        setSpacing(10);
        setPadding(new Insets(10));

        {
            HBox loginPane = new HBox(16);
            loginPane.setAlignment(Pos.CENTER_LEFT);
            loginPane.getStyleClass().add("card");

            TwoLineListItem accountInfo = new TwoLineListItem();
            HBox.setHgrow(accountInfo, Priority.ALWAYS);
            accountInfo.titleProperty().bind(BindingMapping.of(HMCLAccounts.accountProperty())
                    .map(account -> account == null ? i18n("account.not_logged_in") : account.getNickname()));
            accountInfo.subtitleProperty().bind(BindingMapping.of(HMCLAccounts.accountProperty())
                    .map(account -> account == null ? i18n("account.not_logged_in") : account.getEmail()));

            JFXButton logButton = new JFXButton();
            logButton.textProperty().bind(BindingMapping.of(HMCLAccounts.accountProperty())
                    .map(account -> account == null ? i18n("account.login") : i18n("account.logout")));
            logButton.setOnAction(e -> log());

            loginPane.getChildren().setAll(accountInfo, logButton);
            getChildren().add(loginPane);
        }

        {
            HBox searchPane = new HBox(8);
            searchPane.getStyleClass().add("card");
            getChildren().add(searchPane);

            JFXTextField searchField = new JFXTextField();
            searchField.setOnAction(e -> search(searchField.getText(), "time", true));
            HBox.setHgrow(searchField, Priority.ALWAYS);
            searchField.setPromptText(i18n("search"));

            JFXButton searchButton = new JFXButton();
            searchButton.getStyleClass().add("toggle-icon4");
            searchButton.setGraphic(SVG.magnify(Theme.blackFillBinding(), -1, -1));
            searchButton.setOnAction(e -> search(searchField.getText(), "time", true));

            JFXButton addButton = new JFXButton();
            addButton.getStyleClass().add("toggle-icon4");
            addButton.setGraphic(SVG.plus(Theme.blackFillBinding(), -1, -1));
            addButton.setOnAction(e -> addFeedback());

            searchPane.getChildren().setAll(searchField, searchButton, addButton);
        }

        {
            spinnerPane.getStyleClass().add("card");
            VBox.setVgrow(spinnerPane, Priority.ALWAYS);
            JFXListView<FeedbackResponse> listView = new JFXListView<>();
            spinnerPane.setContent(listView);
            Bindings.bindContent(listView.getItems(), feedbacks);
            MutableObject<Object> lastCell = new MutableObject<>();
            listView.setCellFactory(x -> new MDListCell<FeedbackResponse>(listView, lastCell) {
                private final TwoLineListItem content = new TwoLineListItem();
                private final JFXButton likeButton = new JFXButton();
                private final JFXButton unlikeButton = new JFXButton();
                private final HBox container;

                {
                    container = new HBox(8);
                    container.setPickOnBounds(false);
                    container.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(content, Priority.ALWAYS);
                    content.setMouseTransparent(false);
                    setSelectable();

                    likeButton.getStyleClass().add("toggle-icon4");
                    likeButton.setGraphic(FXUtils.limitingSize(SVG.thumbUpOutline(Theme.blackFillBinding(), 24, 24), 24, 24));

                    unlikeButton.getStyleClass().add("toggle-icon4");
                    unlikeButton.setGraphic(FXUtils.limitingSize(SVG.thumbDownOutline(Theme.blackFillBinding(), 24, 24), 24, 24));

                    container.getChildren().setAll(content, likeButton, unlikeButton);

                    StackPane.setMargin(container, new Insets(10, 16, 10, 16));
                    getContainer().getChildren().setAll(container);
                }

                @Override
                protected void updateControl(FeedbackResponse feedback, boolean empty) {
                    if (empty) return;
                    content.setTitle(feedback.getTitle());
                    content.setSubtitle(feedback.getAuthor());
                    content.getTags().setAll(
                            "#" + feedback.getId(),
                            i18n("feedback.state." + feedback.getState().name().toLowerCase(Locale.US)),
                            i18n("feedback.type." + feedback.getType().name().toLowerCase(Locale.US)));
                    content.setOnMouseClicked(e -> {
                        getFeedback(feedback.getId())
                                .thenAcceptAsync(Schedulers.javafx(), f -> {
                                    Controllers.dialog(new ViewFeedbackDialog(f));
                                })
                                .start();
                    });
                }
            });

            getChildren().add(spinnerPane);
        }
    }

    @Override
    public void onPageShown() {
        search("", "time", false);
    }

    private void search(String keyword, String order, boolean showAll) {
        HMCLAccounts.HMCLAccount account = HMCLAccounts.getAccount();
        Task.supplyAsync(() -> {
            Map<String, String> query = mapOf(
                    pair("keyword", keyword),
                    pair("order", order)
            );
            if (showAll) {
                query.put("showAll", "1");
            }
            HttpRequest req = HttpRequest.GET(NetworkUtils.withQuery("https://hmcl.huangyuhui.net/api/feedback", query));
            if (account != null) {
                req.authorization("Bearer", HMCLAccounts.getAccount().getIdToken())
                        .header("Authorization-Provider", HMCLAccounts.getAccount().getProvider());
            }
            return req.<List<FeedbackResponse>>getJson(new TypeToken<List<FeedbackResponse>>(){}.getType());
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            spinnerPane.hideSpinner();
            if (exception != null) {
                if (exception instanceof ResponseCodeException) {
                    int responseCode = ((ResponseCodeException) exception).getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        spinnerPane.setFailedReason(i18n("feedback.failed.permission"));
                        return;
                    } else if (responseCode == 429) {
                        spinnerPane.setFailedReason(i18n("feedback.failed.too_frequently"));
                        return;
                    }
                }
                spinnerPane.setFailedReason(i18n("feedback.failed"));
            } else {
                feedbacks.setAll(result);
            }
        }).start();
    }

    private Task<FeedbackResponse> getFeedback(int id) {
        return Task.supplyAsync(() -> HttpRequest.GET("https://hmcl.huangyuhui.net/api/feedback/" + id).getJson(FeedbackResponse.class));
    }

    private void log() {
        if (HMCLAccounts.getAccount() == null) {
            // login
            Controllers.dialog(new LoginDialog());
        } else {
            // logout
            HMCLAccounts.setAccount(null);
        }
    }

    private void addFeedback() {
        if (HMCLAccounts.getAccount() == null) {
            Controllers.dialog(i18n("feedback.add.login"));
            return;
        }

        Controllers.dialog(new AddFeedbackDialog());
    }

    private static final class LoginDialog extends JFXDialogLayout {
        private final SpinnerPane spinnerPane = new SpinnerPane();
        private final Label errorLabel = new Label();
        private final BooleanProperty logging = new SimpleBooleanProperty();

        public LoginDialog() {
            setHeading(new Label(i18n("feedback.login")));

            VBox vbox = new VBox(8);
            setBody(vbox);
            HintPane hintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            hintPane.textProperty().bind(BindingMapping.of(logging).map(logging -> i18n("account.hmcl.hint")));
            hintPane.setOnMouseClicked(e -> {
                if (logging.get() && OAuthServer.lastlyOpenedURL != null) {
                    FXUtils.copyText(OAuthServer.lastlyOpenedURL);
                }
            });
            vbox.getChildren().setAll(hintPane);

            JFXButton loginButton = new JFXButton();
            spinnerPane.setContent(loginButton);
            loginButton.setText(i18n("account.login"));
            loginButton.setOnAction(e -> login());

            JFXButton cancelButton = new JFXButton();
            cancelButton.setText(i18n("button.cancel"));
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            onEscPressed(this, cancelButton::fire);

            setActions(errorLabel, spinnerPane, cancelButton);
        }

        private void login() {
            spinnerPane.showSpinner();
            errorLabel.setText("");
            logging.set(true);

            HMCLAccounts.login().whenComplete(Schedulers.javafx(), (result, exception) -> {
                logging.set(false);
                if (exception != null) {
                    if (exception instanceof IOException) {
                        errorLabel.setText(i18n("account.failed.connect_authentication_server"));
                    } else if (exception instanceof JsonParseException) {
                        errorLabel.setText(i18n("account.failed.server_response_malformed"));
                    } else {
                        errorLabel.setText(Accounts.localizeErrorMessage(exception));
                    }
                } else {
                    fireEvent(new DialogCloseEvent());
                }
            }).start();
        }
    }

    private static class AddFeedbackDialog extends DialogPane {

        JFXTextField titleField = new JFXTextField();
        JFXComboBox<FeedbackType> comboBox = new JFXComboBox<>();
        JFXTextArea contentArea = new JFXTextArea();

        public AddFeedbackDialog() {
            setTitle(i18n("feedback.add"));

            GridPane body = new GridPane();
            body.setVgap(8);
            body.setHgap(8);

            HintPane searchHintPane = new HintPane(MessageDialogPane.MessageType.WARNING);
            GridPane.setColumnSpan(searchHintPane, 2);
            searchHintPane.setText(i18n("feedback.add.hint.search_before_add"));
            body.addRow(0, searchHintPane);

            HintPane titleHintPane = new HintPane(MessageDialogPane.MessageType.INFO);
            GridPane.setColumnSpan(titleHintPane, 2);
            titleHintPane.setText(i18n("feedback.add.hint.title"));
            body.addRow(1, titleHintPane);

            titleField.setValidators(new RequiredValidator());
            body.addRow(2, new Label(i18n("feedback.title")), titleField);

            comboBox.setMaxWidth(-1);
            comboBox.getItems().setAll(FeedbackType.values());
            comboBox.getSelectionModel().select(0);
            comboBox.setConverter(stringConverter(e -> i18n("feedback.type." + e.name().toLowerCase())));
            body.addRow(3, new Label(i18n("feedback.type")), comboBox);

            Label contentLabel = new Label(i18n("feedback.content"));
            GridPane.setColumnSpan(contentLabel, 2);
            body.addRow(4, contentLabel);

            contentArea.setValidators(new RequiredValidator());
            contentArea.setPromptText(i18n("feedback.add.hint.content"));
            GridPane.setColumnSpan(contentArea, 2);
            body.addRow(5, contentArea);

            validProperty().bind(Bindings.createBooleanBinding(() -> {
                return titleField.validate() && contentArea.validate();
            }, titleField.textProperty(), contentArea.textProperty()));

            setBody(body);
        }

        @Override
        protected void onAccept() {
            setLoading();

            addFeedback(titleField.getText(), comboBox.getValue(), contentArea.getText())
                    .whenComplete(Schedulers.javafx(), exception -> {
                        if (exception != null) {
                            onFailure(exception.getLocalizedMessage());
                        } else {
                            onSuccess();
                        }
                    })
                    .start();
        }

        private Task<?> addFeedback(String title, FeedbackType feedbackType, String content) {
            return Task.runAsync(() -> {
                HttpRequest.POST("https://hmcl.huangyuhui.net/api/feedback")
                        .json(mapOf(
                                pair("title", title),
                                pair("content", content),
                                pair("type", feedbackType.name().toLowerCase(Locale.ROOT)),
                                pair("launcher_version", Metadata.VERSION)
                        ))
                        .authorization("Bearer", HMCLAccounts.getAccount().getIdToken())
                        .header("Authorization-Provider", HMCLAccounts.getAccount().getProvider())
                        .getString();
            });
        }
    }

    private static class ViewFeedbackDialog extends JFXDialogLayout {

        public ViewFeedbackDialog(FeedbackResponse feedback) {
            BorderPane heading = new BorderPane();
            TwoLineListItem left = new TwoLineListItem();
            heading.setLeft(left);
            left.setTitle(feedback.getTitle());
            left.setSubtitle(feedback.getAuthor());
            left.getTags().add("#" + feedback.getId());
            left.getTags().add(i18n("feedback.state." + feedback.getState().name().toLowerCase(Locale.US)));
            left.getTags().add(feedback.getLauncherVersion());
            left.getTags().add(i18n("feedback.type." + feedback.getType().name().toLowerCase()));

            setHeading(heading);

            Label content = new Label(feedback.getContent());
            content.setWrapText(true);

            TwoLineListItem response = new TwoLineListItem();
            response.getStyleClass().setAll("two-line-item-second-large");
            response.setTitle(i18n("feedback.response"));
            response.setSubtitle(StringUtils.isBlank(feedback.getReason())
                    ? i18n("feedback.response.empty")
                    : feedback.getReason());

            VBox body = new VBox(content, response);
            body.setSpacing(8);
            setBody(body);

            JFXButton okButton = new JFXButton();
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            setActions(okButton);
        }
    }

    private static class FeedbackResponse {
        private final int id;
        private final String title;
        private final String content;
        private final String author;
        @SerializedName("launcher_version")
        private final String launcherVersion;
        private final FeedbackType type;
        private final FeedbackState state;
        private final String reason;

        public FeedbackResponse(int id, String title, String content, String author, String launcherVersion, FeedbackType type, FeedbackState state, String reason) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.author = author;
            this.launcherVersion = launcherVersion;
            this.type = type;
            this.state = state;
            this.reason = reason;
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

        public String getAuthor() {
            return author;
        }

        public String getLauncherVersion() {
            return launcherVersion;
        }

        public FeedbackType getType() {
            return type;
        }

        public FeedbackState getState() {
            return state;
        }

        public String getReason() {
            return reason;
        }
    }

    private enum FeedbackType {
        FEATURE,
        BUG
    }

    private enum FeedbackState {
        OPEN,
        REJECTED,
        ACCEPTED
    }
}
