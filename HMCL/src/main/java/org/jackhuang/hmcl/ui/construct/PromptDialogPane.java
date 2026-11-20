// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3CheckBox;
import org.glavo.m3fx.controls.M3ComboBox;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputValidator;
import org.glavo.m3fx.controls.M3TextInputVariant;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FutureCallback;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class PromptDialogPane extends LauncherDialog {
    private final CompletableFuture<List<Builder.Question<?>>> future = new CompletableFuture<>();

    private final Builder builder;
    /// Shows the rejection message below the form until the next submission.
    private final Label warningLabel = new Label();
    /// Prevents duplicate submissions until the pending callback completes.
    private final BooleanProperty submitting;

    public PromptDialogPane(Builder builder) {
        this.builder = builder;
        getDialogPane().setHeadline(builder.title);
        getDialogPane().setPrefWidth(560);

        GridPane body = new GridPane();
        body.setVgap(8);
        body.setHgap(16);
        body.getColumnConstraints().setAll(new ColumnConstraints(), FXUtils.getColumnHgrowing());
        warningLabel.setWrapText(true);
        warningLabel.getStyleClass().add("input-dialog-error");
        warningLabel.visibleProperty().bind(warningLabel.textProperty().isNotEmpty());
        warningLabel.managedProperty().bind(warningLabel.visibleProperty());
        setFormContent(new VBox(8, body, warningLabel));
        List<BooleanBinding> bindings = new ArrayList<>();
        long inputCount = builder.questions.stream().filter(question -> !(question instanceof Builder.HintQuestion)).count();
        M3TextInputVariant variant = inputCount == 1 ? M3TextInputVariant.FILLED : M3TextInputVariant.OUTLINED;
        int rowIndex = 0;
        for (Builder.Question<?> question : builder.questions) {
            if (question instanceof Builder.StringQuestion) {
                Builder.StringQuestion stringQuestion = (Builder.StringQuestion) question;
                M3TextField textField = FXUtils.newTextField(variant);
                textField.textProperty().addListener((a, b, newValue) -> stringQuestion.value = textField.getText());
                textField.setText(stringQuestion.value);
                var layout = FXUtils.newValidatedTextField(textField, stringQuestion.validators.toArray(M3TextInputValidator[]::new));
                if (stringQuestion.promptText != null) {
                    textField.setPromptText(stringQuestion.promptText);
                }
                bindings.add(textField.errorProperty().not());

                if (StringUtils.isNotBlank(question.question.get())) {
                    Label label = createQuestionLabel(question.question.get());
                    GridPane.setMargin(label, new Insets(0, 0, 20, 0));
                    body.addRow(rowIndex++, label, layout);
                } else {
                    GridPane.setColumnSpan(layout, 2);
                    body.addRow(rowIndex++, layout);
                }
                GridPane.setMargin(layout, new Insets(0, 0, 20, 0));
            } else if (question instanceof Builder.BooleanQuestion booleanQuestion) {
                M3CheckBox checkBox = new M3CheckBox(booleanQuestion.question.get());
                checkBox.setSelected(booleanQuestion.value);
                checkBox.selectedProperty().addListener((a, b, newValue) -> booleanQuestion.value = newValue);
                GridPane.setColumnSpan(checkBox, 2);
                body.addRow(rowIndex++, checkBox);
            } else if (question instanceof Builder.CandidatesQuestion candidates) {
                var comboBox = new M3ComboBox<Integer>();
                comboBox.getItems().setAll(IntStream.range(0, candidates.candidates.size()).boxed().toList());
                comboBox.setConverter(candidates.candidates::get);
                comboBox.valueProperty().addListener((obs, old, index) -> candidates.value = index);
                comboBox.setValue(0);
                if (StringUtils.isNotBlank(question.question.get())) {
                    body.addRow(rowIndex++, createQuestionLabel(question.question.get()), comboBox);
                } else {
                    GridPane.setColumnSpan(comboBox, 2);
                    body.addRow(rowIndex++, comboBox);
                }
            } else if (question instanceof Builder.HintQuestion) {
                HintPane pane = new HintPane();
                GridPane.setColumnSpan(pane, 2);
                pane.textProperty().bind(question.question);
                body.addRow(rowIndex++, pane);
            }
        }

        var valid = Bindings.createBooleanBinding(
                () -> bindings.stream().allMatch(BooleanBinding::get),
                bindings.toArray(new BooleanBinding[0])
        );
        var cancel = new M3Button(i18n("button.cancel"), M3ButtonVariant.TEXT);
        cancel.setCancelButton(true);
        var accept = new M3Button(i18n("button.ok"), M3ButtonVariant.TEXT);
        submitting = FXUtils.newButtonLoadingProperty(accept);
        accept.disableProperty().bind(submitting.or(valid.not()));
        accept.setOnAction(event -> {
            event.consume();
            onAccept();
        });
        getDialogPane().getActions().setAll(cancel, accept);
    }

    private static Label createQuestionLabel(String text) {
        Label label = new Label(text);
        GridPane.setValignment(label, VPos.CENTER);
        return label;
    }

    /// Closes the dialog on successful submission and displays errors for another attempt.
    private void onAccept() {
        submitting.set(true);
        warningLabel.setText("");

        builder.callback.call(builder.questions, new FutureCallback.ResultHandler() {
            @Override
            public void resolve() {
                future.complete(builder.questions);
                runInFX(() -> {
                    submitting.set(false);
                    closeDialog();
                });
            }

            @Override
            public void reject(String reason) {
                runInFX(() -> {
                    submitting.set(false);
                    warningLabel.setText(reason);
                });
            }
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
            public final StringProperty question = new SimpleStringProperty();
            protected T value;

            public Question(String question) {
                this.question.set(question);
            }

            public T getValue() {
                return value;
            }

            public String getQuestion() {
                return question.get();
            }

            public StringProperty questionProperty() {
                return question;
            }

            public void setQuestion(String question) {
                this.question.set(question);
            }
        }

        public static class HintQuestion extends Question<Void> {
            public HintQuestion(String hint) {
                super(hint);
            }
        }

        public static class StringQuestion extends Question<String> {
            protected final List<M3TextInputValidator> validators;
            protected String promptText;

            public StringQuestion(String question, String defaultValue, M3TextInputValidator... validators) {
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
