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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PromptDialogPane extends StackPane {
    private final CompletableFuture<List<Builder.Question<?>>> future = new CompletableFuture<>();

    @FXML
    private JFXButton acceptButton;
    @FXML
    private JFXButton cancelButton;
    @FXML
    private VBox vbox;
    @FXML
    private Label title;
    @FXML
    private Label lblCreationWarning;
    @FXML
    private SpinnerPane acceptPane;

    public PromptDialogPane(Builder builder) {
        FXUtils.loadFXML(this, "/assets/fxml/input-dialog.fxml");
        this.title.setText(builder.title);

        List<BooleanBinding> bindings = new ArrayList<>();
        for (Builder.Question<?> question : builder.questions) {
            if (question instanceof Builder.StringQuestion) {
                JFXTextField textField = new JFXTextField();
                textField.textProperty().addListener((a, b, newValue) -> ((Builder.StringQuestion) question).value = textField.getText());
                textField.setText(((Builder.StringQuestion) question).value);
                textField.setValidators(((Builder.StringQuestion) question).validators.toArray(new ValidatorBase[0]));
                bindings.add(Bindings.createBooleanBinding(textField::validate, textField.textProperty()));

                if (StringUtils.isNotBlank(question.question)) {
                    vbox.getChildren().add(new Label(question.question));
                }
                VBox.setMargin(textField, new Insets(0, 0, 20, 0));
                vbox.getChildren().add(textField);
            } else if (question instanceof Builder.BooleanQuestion) {
                HBox hBox = new HBox();
                JFXCheckBox checkBox = new JFXCheckBox();
                hBox.getChildren().setAll(checkBox);
                HBox.setMargin(checkBox, new Insets(0, 0, 0, -10));
                checkBox.setSelected(((Builder.BooleanQuestion) question).value);
                checkBox.selectedProperty().addListener((a, b, newValue) -> ((Builder.BooleanQuestion) question).value = newValue);
                checkBox.setText(question.question);
                vbox.getChildren().add(hBox);
            } else if (question instanceof Builder.CandidatesQuestion) {
                HBox hBox = new HBox();
                JFXComboBox<String> comboBox = new JFXComboBox<>();
                hBox.getChildren().setAll(comboBox);
                comboBox.getItems().setAll(((Builder.CandidatesQuestion) question).candidates);
                comboBox.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) ->
                        ((Builder.CandidatesQuestion) question).value = newValue.intValue());
                comboBox.getSelectionModel().select(0);
                if (StringUtils.isNotBlank(question.question)) {
                    vbox.getChildren().add(new Label(question.question));
                }
                vbox.getChildren().add(hBox);
            }
        }

        cancelButton.setOnMouseClicked(e -> fireEvent(new DialogCloseEvent()));
        acceptButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> bindings.stream().map(BooleanBinding::get).anyMatch(x -> !x),
                bindings.toArray(new BooleanBinding[0])
        ));

        acceptButton.setOnMouseClicked(e -> {
            acceptPane.showSpinner();

            builder.callback.call(builder.questions, () -> {
                acceptPane.hideSpinner();
                future.complete(builder.questions);
                fireEvent(new DialogCloseEvent());
            }, msg -> {
                acceptPane.hideSpinner();
                lblCreationWarning.setText(msg);
            });
        });

        onEscPressed(this, cancelButton::fire);
    }

    public CompletableFuture<List<Builder.Question<?>>> getCompletableFuture() {
        return future;
    }

    public static class Builder {
        private final List<Question<?>> questions = new ArrayList<>();
        private final String title;
        private final FutureCallback<List<Question<?>>> callback;

        public Builder(String title, FutureCallback<List<Question<?>>> callback) {
            this.title = title;
            this.callback = callback;
        }

        public <T> Builder addQuestion(Question<T> question) {
            questions.add(question);
            return this;
        }

        public static class Question<T> {
            public final String question;
            protected T value;

            public Question(String question) {
                this.question = question;
            }

            public T getValue() {
                return value;
            }
        }

        public static class StringQuestion extends Question<String> {
            protected final List<ValidatorBase> validators;

            public StringQuestion(String question, String defaultValue, ValidatorBase... validators) {
                super(question);
                this.value = defaultValue;
                this.validators = Arrays.asList(validators);
            }
        }

        public static class CandidatesQuestion extends Question<Integer> {
            protected final List<String> candidates;

            public CandidatesQuestion(String question, String... candidates) {
                super(question);
                this.value = null;
                if (candidates == null || candidates.length == 0) {
                    throw new IllegalArgumentException("At least one candidate required");
                }
                this.candidates = new ArrayList<>(Arrays.asList(candidates));
            }
        }

        public static class BooleanQuestion extends Question<Boolean> {

            public BooleanQuestion(String question, boolean defaultValue) {
                super(question);
                this.value = defaultValue;
            }
        }
    }
}
