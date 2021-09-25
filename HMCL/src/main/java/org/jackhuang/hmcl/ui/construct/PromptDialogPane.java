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

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;

public class PromptDialogPane extends DialogPane {
    private final CompletableFuture<List<Builder.Question<?>>> future = new CompletableFuture<>();

    private final Builder builder;

    public PromptDialogPane(Builder builder) {
        this.builder = builder;
        setTitle(builder.title);

        GridPane body = new GridPane();
        body.setVgap(8);
        body.setHgap(16);
        body.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());
        setBody(body);
        List<BooleanBinding> bindings = new ArrayList<>();
        int rowIndex = 0;
        for (Builder.Question<?> question : builder.questions) {
            if (question instanceof Builder.StringQuestion) {
                Builder.StringQuestion stringQuestion = (Builder.StringQuestion) question;
                JFXTextField textField = new JFXTextField();
                textField.textProperty().addListener((a, b, newValue) -> stringQuestion.value = textField.getText());
                textField.setText(stringQuestion.value);
                textField.setValidators(((Builder.StringQuestion) question).validators.toArray(new ValidatorBase[0]));
                if (stringQuestion.promptText != null) {
                    textField.setPromptText(stringQuestion.promptText);
                }
                bindings.add(Bindings.createBooleanBinding(textField::validate, textField.textProperty()));

                if (StringUtils.isNotBlank(question.question)) {
                    body.addRow(rowIndex++, new Label(question.question), textField);
                } else {
                    GridPane.setColumnSpan(textField, 2);
                    body.addRow(rowIndex++, textField);
                }
                GridPane.setMargin(textField, new Insets(0, 0, 20, 0));
            } else if (question instanceof Builder.BooleanQuestion) {
                HBox hBox = new HBox();
                GridPane.setColumnSpan(hBox, 2);
                JFXCheckBox checkBox = new JFXCheckBox();
                hBox.getChildren().setAll(checkBox);
                HBox.setMargin(checkBox, new Insets(0, 0, 0, -10));
                checkBox.setSelected(((Builder.BooleanQuestion) question).value);
                checkBox.selectedProperty().addListener((a, b, newValue) -> ((Builder.BooleanQuestion) question).value = newValue);
                checkBox.setText(question.question);
                body.addRow(rowIndex++, hBox);
            } else if (question instanceof Builder.CandidatesQuestion) {
                JFXComboBox<String> comboBox = new JFXComboBox<>();
                comboBox.getItems().setAll(((Builder.CandidatesQuestion) question).candidates);
                comboBox.getSelectionModel().selectedIndexProperty().addListener((a, b, newValue) ->
                        ((Builder.CandidatesQuestion) question).value = newValue.intValue());
                comboBox.getSelectionModel().select(0);
                if (StringUtils.isNotBlank(question.question)) {
                    body.addRow(rowIndex++, new Label(question.question), comboBox);
                } else {
                    GridPane.setColumnSpan(comboBox, 2);
                    body.addRow(rowIndex++, comboBox);
                }
            } else if (question instanceof Builder.HintQuestion) {
                HintPane pane = new HintPane();
                GridPane.setColumnSpan(pane, 2);
                pane.setText(question.question);
                body.addRow(rowIndex++, pane);
            }
        }

        validProperty().bind(Bindings.createBooleanBinding(
                () -> bindings.stream().allMatch(BooleanBinding::get),
                bindings.toArray(new BooleanBinding[0])
        ));
    }

    @Override
    protected void onAccept() {
        setLoading();

        builder.callback.call(builder.questions, () -> {
            future.complete(builder.questions);
            runInFX(this::onSuccess);
        }, msg -> {
            runInFX(() -> onFailure(msg));
        });
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

        public static class HintQuestion extends Question<Void> {
            public HintQuestion(String hint) {
                super(hint);
            }
        }

        public static class StringQuestion extends Question<String> {
            protected final List<ValidatorBase> validators;
            protected String promptText;

            public StringQuestion(String question, String defaultValue, ValidatorBase... validators) {
                super(question);
                this.value = defaultValue;
                this.validators = Arrays.asList(validators);
            }

            public StringQuestion setPromptText(String promptText) {
                this.promptText = promptText;
                return this;
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
