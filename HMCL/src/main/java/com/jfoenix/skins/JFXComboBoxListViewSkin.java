//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.adapters.skins.ComboBoxListViewSkin;
import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

public class JFXComboBoxListViewSkin<T> extends ComboBoxListViewSkin<T> {
    protected final ObjectProperty<Paint> promptTextFill;
    private boolean invalid = true;
    private final StackPane customPane;
    private final StackPane line = new StackPane();
    private final StackPane focusedLine = new StackPane();
    private final Text promptText = new Text();
    private final double initScale = 0.05;
    private final Scale scale;
    private final Timeline linesAnimation;
    private ParallelTransition transition;
    private CachedTransition promptTextUpTransition;
    private CachedTransition promptTextDownTransition;
    private CachedTransition promptTextColorTransition;
    private final Scale promptTextScale;
    private Paint oldPromptTextFill;
    private final BooleanBinding usePromptText;

    public JFXComboBoxListViewSkin(JFXComboBox<T> comboBox) {
        super(comboBox);
        this.scale = new Scale(this.initScale, 1.0F);
        this.linesAnimation = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(this.scale.xProperty(), this.initScale, Interpolator.EASE_BOTH), new KeyValue(this.focusedLine.opacityProperty(), 0, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(1.0F), new KeyValue(this.focusedLine.opacityProperty(), 1, Interpolator.EASE_BOTH)), new KeyFrame(Duration.millis(160.0F), new KeyValue(this.scale.xProperty(), 1, Interpolator.EASE_BOTH)));
        this.promptTextScale = new Scale(1.0F, 1.0F, 0.0F, 0.0F);
        this.promptTextFill = new SimpleObjectProperty(Color.valueOf("#B2B2B2"));
        this.usePromptText = Bindings.createBooleanBinding(this::usePromptText, ((JFXComboBox) this.getSkinnable()).valueProperty(), this.getSkinnable().promptTextProperty());
        this.getArrowButton().setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        this.promptText.textProperty().bind(comboBox.promptTextProperty());
        this.promptText.fillProperty().bind(this.promptTextFill);
        this.promptText.getStyleClass().addAll("text", "prompt-text");
        this.promptText.getTransforms().add(this.promptTextScale);
        if (!comboBox.isLabelFloat()) {
            this.promptText.visibleProperty().bind(this.usePromptText);
        }

        this.customPane = new StackPane();
        this.customPane.setMouseTransparent(true);
        this.customPane.getStyleClass().add("combo-box-button-container");
        this.customPane.backgroundProperty().bindBidirectional(this.getSkinnable().backgroundProperty());
        this.customPane.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
        this.customPane.getChildren().add(this.promptText);
        this.getChildren().add(0, this.customPane);
        StackPane.setAlignment(this.promptText, Pos.CENTER_LEFT);
        this.line.getStyleClass().add("input-line");
        this.focusedLine.getStyleClass().add("input-focused-line");
        this.getChildren().add(this.line);
        this.getChildren().add(this.focusedLine);
        this.line.setPrefHeight(1.0F);
        this.line.setTranslateY(1.0F);
        this.line.setManaged(false);
        this.line.setBackground(new Background(new BackgroundFill(((JFXComboBox) this.getSkinnable()).getUnFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));
        if (this.getSkinnable().isDisabled()) {
            this.line.setBorder(new Border(new BorderStroke(((JFXComboBox) this.getSkinnable()).getUnFocusColor(), BorderStrokeStyle.DASHED, CornerRadii.EMPTY, new BorderWidths(1.0F))));
            this.line.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
        }

        this.focusedLine.setPrefHeight(2.0F);
        this.focusedLine.setTranslateY(0.0F);
        this.focusedLine.setBackground(new Background(new BackgroundFill(((JFXComboBox) this.getSkinnable()).getFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));
        this.focusedLine.setOpacity(0.0F);
        this.focusedLine.getTransforms().add(this.scale);
        this.focusedLine.setManaged(false);
        if (comboBox.isEditable()) {
            comboBox.getEditor().setStyle("-fx-background-color:TRANSPARENT;-fx-padding: 4 0.666667em 4 0.666667em");
            comboBox.getEditor().promptTextProperty().unbind();
            comboBox.getEditor().setPromptText(null);
            comboBox.getEditor().textProperty().addListener((o, oldVal, newVal) -> {
                this.usePromptText.invalidate();
                comboBox.setValue(this.getConverter().fromString(newVal));
            });
        }

        comboBox.labelFloatProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                this.promptText.visibleProperty().unbind();
                JFXUtilities.runInFX(() -> this.createFloatingAnimation());
            } else {
                this.promptText.visibleProperty().bind(this.usePromptText);
            }

            this.createFocusTransition();
        });
        comboBox.focusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                this.focusedLine.setBackground(new Background(new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)));
                if (((JFXComboBox) this.getSkinnable()).isLabelFloat()) {
                    this.promptTextColorTransition = new CachedTransition(this.customPane, new Timeline(new KeyFrame(Duration.millis(1300.0F), new KeyValue(this.promptTextFill, newVal, Interpolator.EASE_BOTH)))) {
                        {
                            this.setDelay(Duration.millis(0.0F));
                            this.setCycleDuration(Duration.millis(160.0F));
                        }

                        protected void starting() {
                            super.starting();
                            JFXComboBoxListViewSkin.this.oldPromptTextFill = JFXComboBoxListViewSkin.this.promptTextFill.get();
                        }
                    };
                    this.transition = null;
                }
            }

        });
        comboBox.unFocusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                this.line.setBackground(new Background(new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)));
            }

        });
        comboBox.disabledProperty().addListener((o, oldVal, newVal) -> {
            this.line.setBorder(newVal ? new Border(new BorderStroke(((JFXComboBox) this.getSkinnable()).getUnFocusColor(), BorderStrokeStyle.DASHED, CornerRadii.EMPTY, new BorderWidths(this.line.getHeight()))) : Border.EMPTY);
            this.line.setBackground(new Background(new BackgroundFill(newVal ? Color.TRANSPARENT : ((JFXComboBox) this.getSkinnable()).getUnFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)));
        });
        comboBox.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                this.focus();
            } else {
                this.unFocus();
            }

        });
        comboBox.valueProperty().addListener((o, oldVal, newVal) -> {
            if (((JFXComboBox) this.getSkinnable()).isLabelFloat()) {
                this.animateFloatingLabel(newVal != null && !newVal.toString().isEmpty());
            }

        });
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        this.customPane.resizeRelocate(x, y, w, h);
        if (this.invalid) {
            this.invalid = false;
            if (!this.getSkinnable().isEditable()) {
                Text javaPromptText = (Text) super.getDisplayNode().lookup(".text");
                if (javaPromptText != null) {
                    this.promptTextFill.set(javaPromptText.getFill());
                }
            }

            this.createFloatingAnimation();
            if (((ComboBoxBase) this.getSkinnable()).getValue() != null) {
                this.animateFloatingLabel(true);
            }
        }

        this.focusedLine.resizeRelocate(x, this.getSkinnable().getHeight(), w, this.focusedLine.prefHeight(-1.0F));
        this.line.resizeRelocate(x, this.getSkinnable().getHeight(), w, this.line.prefHeight(-1.0F));
        this.scale.setPivotX(w / (double) 2.0F);
    }

    private void createFloatingAnimation() {
        this.promptTextUpTransition = new CachedTransition(this.customPane, new Timeline(new KeyFrame(Duration.millis(1300.0F), new KeyValue(this.promptText.translateYProperty(), -this.customPane.getHeight() + 6.05, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.xProperty(), 0.85, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.yProperty(), 0.85, Interpolator.EASE_BOTH)))) {
            {
                this.setDelay(Duration.millis(0.0F));
                this.setCycleDuration(Duration.millis(240.0F));
            }
        };
        this.promptTextColorTransition = new CachedTransition(this.customPane, new Timeline(new KeyFrame(Duration.millis(1300.0F), new KeyValue(this.promptTextFill, ((JFXComboBox) this.getSkinnable()).getFocusColor(), Interpolator.EASE_BOTH)))) {
            {
                this.setDelay(Duration.millis(0.0F));
                this.setCycleDuration(Duration.millis(160.0F));
            }

            protected void starting() {
                super.starting();
                JFXComboBoxListViewSkin.this.oldPromptTextFill = JFXComboBoxListViewSkin.this.promptTextFill.get();
            }
        };
        this.promptTextDownTransition = new CachedTransition(this.customPane, new Timeline(new KeyFrame(Duration.millis(1300.0F), new KeyValue(this.promptText.translateYProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.xProperty(), 1, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.yProperty(), 1, Interpolator.EASE_BOTH)))) {
            {
                this.setDelay(Duration.millis(0.0F));
                this.setCycleDuration(Duration.millis(240.0F));
            }
        };
        this.promptTextDownTransition.setOnFinished((finish) -> {
            this.promptText.setTranslateY(0.0F);
            this.promptTextScale.setX(1.0F);
            this.promptTextScale.setY(1.0F);
        });
    }

    private void focus() {
        if (this.transition == null) {
            this.createFocusTransition();
        }

        this.transition.play();
    }

    private void animateFloatingLabel(boolean up) {
        if (this.promptText == null) {
            Platform.runLater(() -> this.animateFloatingLabel(up));
        } else {
            if (this.transition != null) {
                this.transition.stop();
                this.transition.getChildren().remove(this.promptTextUpTransition);
                this.transition.getChildren().remove(this.promptTextColorTransition);
                this.transition = null;
            }

            if (up && this.promptText.getTranslateY() == (double) 0.0F) {
                this.promptTextDownTransition.stop();
                this.promptTextUpTransition.play();
                if (this.getSkinnable().isFocused()) {
                    this.promptTextColorTransition.play();
                }
            } else if (!up) {
                this.promptTextUpTransition.stop();
                if (this.getSkinnable().isFocused()) {
                    this.promptTextFill.set(this.oldPromptTextFill);
                }

                this.promptTextDownTransition.play();
            }
        }

    }

    private void createFocusTransition() {
        this.transition = new ParallelTransition();
        if (((JFXComboBox) this.getSkinnable()).isLabelFloat()) {
            this.transition.getChildren().add(this.promptTextUpTransition);
            this.transition.getChildren().add(this.promptTextColorTransition);
        }

        this.transition.getChildren().add(this.linesAnimation);
    }

    private void unFocus() {
        if (this.transition != null) {
            this.transition.stop();
        }

        this.scale.setX(this.initScale);
        this.focusedLine.setOpacity(0.0F);
        if (((JFXComboBox) this.getSkinnable()).isLabelFloat() && this.oldPromptTextFill != null) {
            this.promptTextFill.set(this.oldPromptTextFill);
            if (this.usePromptText()) {
                this.promptTextDownTransition.play();
            }
        }

    }

    private boolean usePromptText() {
        Object txt = ((JFXComboBox) this.getSkinnable()).getValue();
        String promptTxt = this.getSkinnable().getPromptText();
        return (txt == null || txt.toString().isEmpty()) && promptTxt != null && !promptTxt.isEmpty() && !this.promptTextFill.get().equals(Color.TRANSPARENT);
    }
}
