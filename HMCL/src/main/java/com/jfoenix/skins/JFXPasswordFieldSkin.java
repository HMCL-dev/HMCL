//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.adapters.skins.TextFieldSkin;
import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.transitions.CachedTransition;
import com.jfoenix.validation.base.ValidatorBase;
import javafx.animation.*;
import javafx.animation.Animation.Status;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

public class JFXPasswordFieldSkin extends TextFieldSkin {
    private boolean invalid = true;
    private StackPane line = new StackPane();
    private StackPane focusedLine = new StackPane();
    private Label errorLabel = new Label();
    private StackPane errorIcon = new StackPane();
    private HBox errorContainer = new HBox();
    private StackPane promptContainer = new StackPane();
    private Text promptText;
    private Pane textPane;
    private ParallelTransition transition;
    private CachedTransition promptTextUpTransition;
    private CachedTransition promptTextDownTransition;
    private CachedTransition promptTextColorTransition;
    private double initScale = 0.05;
    private final Scale promptTextScale = new Scale((double)1.0F, (double)1.0F, (double)0.0F, (double)0.0F);
    private final Scale scale;
    private Timeline linesAnimation;
    private Paint oldPromptTextFill;
    private BooleanBinding usePromptText;
    private final Rectangle errorContainerClip;
    private final Scale errorClipScale;
    private Timeline errorHideTransition;
    private Timeline errorShowTransition;
    private Timeline scale1;
    private Timeline scaleLess1;

    public JFXPasswordFieldSkin(JFXPasswordField field) {
        super(field);
        this.scale = new Scale(this.initScale, (double)1.0F);
        this.linesAnimation = new Timeline(new KeyFrame[]{new KeyFrame(Duration.ZERO, new KeyValue[]{new KeyValue(this.scale.xProperty(), this.initScale, Interpolator.EASE_BOTH), new KeyValue(this.focusedLine.opacityProperty(), 0, Interpolator.EASE_BOTH)}), new KeyFrame(Duration.millis((double)1.0F), new KeyValue[]{new KeyValue(this.focusedLine.opacityProperty(), 1, Interpolator.EASE_BOTH)}), new KeyFrame(Duration.millis((double)160.0F), new KeyValue[]{new KeyValue(this.scale.xProperty(), 1, Interpolator.EASE_BOTH)})});
        this.usePromptText = Bindings.createBooleanBinding(this::usePromptText, new Observable[]{((TextField)this.getSkinnable()).textProperty(), ((TextField)this.getSkinnable()).promptTextProperty()});
        this.errorContainerClip = new Rectangle();
        this.errorClipScale = new Scale((double)1.0F, (double)0.0F, (double)0.0F, (double)0.0F);
        this.errorHideTransition = new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)80.0F), new KeyValue[]{new KeyValue(this.errorContainer.opacityProperty(), 0, Interpolator.LINEAR)})});
        this.errorShowTransition = new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)80.0F), new KeyValue[]{new KeyValue(this.errorContainer.opacityProperty(), 1, Interpolator.EASE_OUT)})});
        this.scale1 = new Timeline();
        this.scaleLess1 = new Timeline();
        field.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)}));
        field.setPadding(new Insets((double)4.0F, (double)0.0F, (double)4.0F, (double)0.0F));
        this.errorLabel.getStyleClass().add("error-label");
        this.line.getStyleClass().add("input-line");
        this.focusedLine.getStyleClass().add("input-focused-line");
        this.line.setPrefHeight((double)1.0F);
        this.line.setTranslateY((double)1.0F);
        this.line.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(((JFXPasswordField)this.getSkinnable()).getUnFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)}));
        if (((TextField)this.getSkinnable()).isDisabled()) {
            this.line.setBorder(new Border(new BorderStroke[]{new BorderStroke(((JFXPasswordField)this.getSkinnable()).getUnFocusColor(), BorderStrokeStyle.DASHED, CornerRadii.EMPTY, new BorderWidths((double)1.0F))}));
            this.line.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)}));
        }

        this.focusedLine.setPrefHeight((double)2.0F);
        this.focusedLine.setTranslateY((double)0.0F);
        this.focusedLine.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(((JFXPasswordField)this.getSkinnable()).getFocusColor(), CornerRadii.EMPTY, Insets.EMPTY)}));
        this.focusedLine.setOpacity((double)0.0F);
        this.focusedLine.getTransforms().add(this.scale);
        this.errorContainer.getChildren().setAll(new Node[]{new StackPane(new Node[]{this.errorLabel}), this.errorIcon});
        this.errorContainer.setAlignment(Pos.CENTER_LEFT);
        this.errorContainer.setSpacing((double)8.0F);
        this.errorContainer.setPadding(new Insets((double)4.0F, (double)0.0F, (double)0.0F, (double)0.0F));
        this.errorContainer.setVisible(false);
        this.errorContainer.setOpacity((double)0.0F);
        this.errorContainer.setManaged(false);
        StackPane.setAlignment(this.errorLabel, Pos.TOP_LEFT);
        HBox.setHgrow(this.errorLabel.getParent(), Priority.ALWAYS);
        this.errorContainerClip.getTransforms().add(this.errorClipScale);
        this.errorContainer.setClip(field.isDisableAnimation() ? null : this.errorContainerClip);
        this.getChildren().addAll(new Node[]{this.line, this.focusedLine, this.promptContainer, this.errorContainer});
        field.labelFloatProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                JFXUtilities.runInFX(this::createFloatingLabel);
            } else {
                this.promptText.visibleProperty().bind(this.usePromptText);
            }

            this.createFocusTransition();
        });
        field.activeValidatorProperty().addListener((o, oldVal, newVal) -> {
            if (this.textPane != null) {
                if (!((JFXPasswordField)this.getSkinnable()).isDisableAnimation()) {
                    if (newVal != null) {
                        this.errorHideTransition.setOnFinished((finish) -> {
                            this.showError(newVal);
                            double w = ((TextField)this.getSkinnable()).getWidth();
                            double errorContainerHeight = this.computeErrorHeight(this.computeErrorWidth(w));
                            if (this.errorLabel.isWrapText()) {
                                if (errorContainerHeight < this.errorContainer.getHeight()) {
                                    this.scaleLess1.getKeyFrames().setAll(new KeyFrame[]{this.createSmallerScaleFrame(errorContainerHeight)});
                                    this.scaleLess1.setOnFinished((event) -> {
                                        this.updateErrorContainerSize(w, errorContainerHeight);
                                        this.errorClipScale.setY((double)1.0F);
                                    });
                                    SequentialTransition transition = new SequentialTransition(new Animation[]{this.scaleLess1, this.errorShowTransition});
                                    transition.play();
                                } else {
                                    this.errorClipScale.setY(oldVal == null ? (double)0.0F : this.errorContainer.getHeight() / errorContainerHeight);
                                    this.updateErrorContainerSize(w, errorContainerHeight);
                                    this.scale1.getKeyFrames().setAll(new KeyFrame[]{this.createScaleToOneFrames()});
                                    ParallelTransition parallelTransition = new ParallelTransition();
                                    parallelTransition.getChildren().addAll(new Animation[]{this.scale1, this.errorShowTransition});
                                    parallelTransition.play();
                                }
                            } else {
                                this.errorClipScale.setY((double)1.0F);
                                this.updateErrorContainerSize(w, errorContainerHeight);
                                ParallelTransition parallelTransition = new ParallelTransition(new Animation[]{this.errorShowTransition});
                                parallelTransition.play();
                            }

                        });
                        this.errorHideTransition.play();
                    } else {
                        this.errorHideTransition.setOnFinished((EventHandler)null);
                        if (this.errorLabel.isWrapText()) {
                            this.scaleLess1.getKeyFrames().setAll(new KeyFrame[]{new KeyFrame(Duration.millis((double)100.0F), new KeyValue[]{new KeyValue(this.errorClipScale.yProperty(), 0, Interpolator.EASE_BOTH)})});
                            this.scaleLess1.setOnFinished((event) -> {
                                this.hideError();
                                this.errorClipScale.setY((double)0.0F);
                            });
                            SequentialTransition transition = new SequentialTransition(new Animation[]{this.scaleLess1});
                            transition.play();
                        } else {
                            this.errorClipScale.setY((double)0.0F);
                        }

                        this.errorHideTransition.play();
                    }
                } else if (newVal != null) {
                    JFXUtilities.runInFXAndWait(() -> this.showError(newVal));
                } else {
                    JFXUtilities.runInFXAndWait(this::hideError);
                }
            }

        });
        field.focusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                this.focusedLine.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)}));
                if (((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
                    this.promptTextColorTransition = new CachedTransition(this.textPane, new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)1300.0F), new KeyValue[]{new KeyValue(this.promptTextFillProperty2(), newVal, Interpolator.EASE_BOTH)})})) {
                        {
                            this.setDelay(Duration.millis((double)0.0F));
                            this.setCycleDuration(Duration.millis((double)160.0F));
                        }

                        protected void starting() {
                            super.starting();
                            JFXPasswordFieldSkin.this.oldPromptTextFill = JFXPasswordFieldSkin.this.getPromptTextFill2();
                        }
                    };
                    this.resetFocusTransition();
                }
            }

        });
        field.unFocusColorProperty().addListener((o, oldVal, newVal) -> {
            if (newVal != null) {
                this.line.setBackground(new Background(new BackgroundFill[]{new BackgroundFill(newVal, CornerRadii.EMPTY, Insets.EMPTY)}));
            }

        });
        field.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                this.focus();
            } else {
                this.unFocus();
            }

        });
        field.textProperty().addListener((o, oldVal, newVal) -> {
            if (!((TextField)this.getSkinnable()).isFocused() && ((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
                if (newVal != null && !newVal.isEmpty()) {
                    this.animateFloatingLabel(true);
                } else {
                    this.animateFloatingLabel(false);
                }
            }

        });
        field.disabledProperty().addListener((o, oldVal, newVal) -> {
            this.line.setBorder(newVal ? new Border(new BorderStroke[]{new BorderStroke(((JFXPasswordField)this.getSkinnable()).getUnFocusColor(), BorderStrokeStyle.DASHED, CornerRadii.EMPTY, new BorderWidths(this.line.getHeight()))}) : Border.EMPTY);
            this.line.setBackground(new Background(new BackgroundFill[]{new BackgroundFill((Paint)(newVal ? Color.TRANSPARENT : ((JFXPasswordField)this.getSkinnable()).getUnFocusColor()), CornerRadii.EMPTY, Insets.EMPTY)}));
        });
        this.promptTextFillProperty2().addListener((o, oldVal, newVal) -> {
            if (Color.TRANSPARENT.equals(newVal) && ((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
                this.setPromptTextFill2(oldVal);
            }

        });
        this.registerChangeListener2(field.disableAnimationProperty(), "DISABLE_ANIMATION", () -> this.errorContainer.setClip(((JFXPasswordField)this.getSkinnable()).isDisableAnimation() ? null : this.errorContainerClip));
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        super.layoutChildren(x, y, w, h);
        if ((this.transition == null || this.transition.getStatus() == Status.STOPPED) && ((TextField)this.getSkinnable()).isFocused() && ((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
            this.setPromptTextFill2(((JFXPasswordField)this.getSkinnable()).getFocusColor());
        }

        if (this.invalid) {
            this.invalid = false;
            this.textPane = (Pane)this.getChildren().get(0);
            this.createFloatingLabel();
            ValidatorBase activeValidator = ((JFXPasswordField)this.getSkinnable()).getActiveValidator();
            if (activeValidator != null) {
                this.showError(activeValidator);
                double errorContainerWidth = w - this.errorIcon.prefWidth((double)-1.0F);
                this.errorContainer.setOpacity((double)1.0F);
                this.errorContainer.resize(w, this.computeErrorHeight(errorContainerWidth));
                this.errorContainerClip.setWidth(w);
                this.errorContainerClip.setHeight(this.errorContainer.getHeight());
                this.errorClipScale.setY((double)1.0F);
            }

            super.layoutChildren(x, y, w, h);
            this.createFocusTransition();
            if (((TextField)this.getSkinnable()).isFocused()) {
                this.focus();
            }
        }

        double height = ((TextField)this.getSkinnable()).getHeight();
        double focusedLineHeight = this.focusedLine.prefHeight((double)-1.0F);
        this.focusedLine.resizeRelocate(x, height, w, focusedLineHeight);
        this.line.resizeRelocate(x, height, w, this.line.prefHeight((double)-1.0F));
        this.errorContainer.relocate(x, height + focusedLineHeight);
        if (((JFXPasswordField)this.getSkinnable()).isDisableAnimation()) {
            this.errorContainer.resize(w, this.computeErrorHeight(this.computeErrorWidth(w)));
        }

        this.scale.setPivotX(w / (double)2.0F);
    }

    private double computeErrorWidth(double w) {
        return w - this.errorIcon.prefWidth((double)-1.0F);
    }

    private double computeErrorHeight(double errorContainerWidth) {
        return this.errorLabel.prefHeight(errorContainerWidth) + this.errorContainer.snappedBottomInset() + this.errorContainer.snappedTopInset();
    }

    private void updateErrorContainerSize(double w, double errorContainerHeight) {
        this.errorContainerClip.setWidth(w);
        this.errorContainerClip.setHeight(errorContainerHeight);
        this.errorContainer.resize(w, errorContainerHeight);
    }

    private KeyFrame createSmallerScaleFrame(double errorContainerHeight) {
        return new KeyFrame(Duration.millis((double)100.0F), new KeyValue[]{new KeyValue(this.errorClipScale.yProperty(), errorContainerHeight / this.errorContainer.getHeight(), Interpolator.EASE_BOTH)});
    }

    private KeyFrame createScaleToOneFrames() {
        return new KeyFrame(Duration.millis((double)100.0F), new KeyValue[]{new KeyValue(this.errorClipScale.yProperty(), 1, Interpolator.EASE_BOTH)});
    }

    private void createFloatingLabel() {
        if (((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
            if (this.promptText == null) {
                boolean triggerFloatLabel = false;
                if (this.textPane.getChildren().get(0) instanceof Text) {
                    this.promptText = (Text)this.textPane.getChildren().get(0);
                } else {
                    this.createPromptNode();
                    ReflectionHelper.setFieldContent(TextFieldSkin.class.getSuperclass(), this, "promptNode", this.promptText);
                    triggerFloatLabel = true;
                }

                this.promptText.getTransforms().add(this.promptTextScale);
                this.promptContainer.getChildren().add(this.promptText);
                if (triggerFloatLabel) {
                    this.promptText.setTranslateY(-this.textPane.getHeight());
                    this.promptTextScale.setX(0.85);
                    this.promptTextScale.setY(0.85);
                }
            }

            this.promptTextUpTransition = new CachedTransition(this.textPane, new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)1300.0F), new KeyValue[]{new KeyValue(this.promptText.translateYProperty(), -this.textPane.getHeight(), Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.xProperty(), 0.85, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.yProperty(), 0.85, Interpolator.EASE_BOTH)})})) {
                {
                    this.setDelay(Duration.millis((double)0.0F));
                    this.setCycleDuration(Duration.millis((double)240.0F));
                }
            };
            this.promptTextColorTransition = new CachedTransition(this.textPane, new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)1300.0F), new KeyValue[]{new KeyValue(this.promptTextFillProperty2(), ((JFXPasswordField)this.getSkinnable()).getFocusColor(), Interpolator.EASE_BOTH)})})) {
                {
                    this.setDelay(Duration.millis((double)0.0F));
                    this.setCycleDuration(Duration.millis((double)160.0F));
                }

                protected void starting() {
                    super.starting();
                    JFXPasswordFieldSkin.this.oldPromptTextFill = JFXPasswordFieldSkin.this.getPromptTextFill2();
                }
            };
            this.promptTextDownTransition = new CachedTransition(this.textPane, new Timeline(new KeyFrame[]{new KeyFrame(Duration.millis((double)1300.0F), new KeyValue[]{new KeyValue(this.promptText.translateYProperty(), 0, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.xProperty(), 1, Interpolator.EASE_BOTH), new KeyValue(this.promptTextScale.yProperty(), 1, Interpolator.EASE_BOTH)})})) {
                {
                    this.setDelay(Duration.millis((double)0.0F));
                    this.setCycleDuration(Duration.millis((double)240.0F));
                }
            };
            this.promptTextDownTransition.setOnFinished((finish) -> {
                this.promptText.setTranslateY((double)0.0F);
                this.promptTextScale.setX((double)1.0F);
                this.promptTextScale.setY((double)1.0F);
            });
            this.promptText.visibleProperty().unbind();
            this.promptText.visibleProperty().set(true);
        }

    }

    private void createPromptNode() {
        this.promptText = new Text();
        this.promptText.setManaged(false);
        this.promptText.getStyleClass().add("text");
        this.promptText.visibleProperty().bind(this.usePromptText);
        this.promptText.fontProperty().bind(((TextField)this.getSkinnable()).fontProperty());
        this.promptText.textProperty().bind(((TextField)this.getSkinnable()).promptTextProperty());
        this.promptText.fillProperty().bind(this.promptTextFillProperty2());
        this.promptText.setLayoutX((double)1.0F);
    }

    private void focus() {
        if (this.textPane == null) {
            Platform.runLater(this::focus);
        } else {
            if (this.transition == null) {
                this.createFocusTransition();
            }

            this.transition.play();
        }

    }

    private void createFocusTransition() {
        this.transition = new ParallelTransition();
        if (((JFXPasswordField)this.getSkinnable()).isLabelFloat()) {
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
        this.focusedLine.setOpacity((double)0.0F);
        if (((JFXPasswordField)this.getSkinnable()).isLabelFloat() && this.oldPromptTextFill != null) {
            this.setPromptTextFill2(this.oldPromptTextFill);
            if (this.usePromptText()) {
                this.promptTextDownTransition.play();
            }
        }

    }

    private void animateFloatingLabel(boolean up) {
        if (this.promptText == null) {
            Platform.runLater(() -> this.animateFloatingLabel(up));
        } else {
            this.resetFocusTransition();
            if (up && this.promptText.getTranslateY() == (double)0.0F) {
                this.promptTextDownTransition.stop();
                this.promptTextUpTransition.play();
            } else if (!up) {
                this.promptTextUpTransition.stop();
                this.promptTextDownTransition.play();
            }
        }

    }

    private void resetFocusTransition() {
        if (this.transition != null) {
            this.transition.stop();
            this.transition.getChildren().remove(this.promptTextUpTransition);
            this.transition = null;
        }

    }

    private boolean usePromptText() {
        String txt = ((TextField)this.getSkinnable()).getText();
        String promptTxt = ((TextField)this.getSkinnable()).getPromptText();
        return (txt == null || txt.isEmpty()) && promptTxt != null && !promptTxt.isEmpty() && !this.getPromptTextFill2().equals(Color.TRANSPARENT);
    }

    private void showError(ValidatorBase validator) {
        this.errorLabel.setText(validator.getMessage());
        Node icon = validator.getIcon();
        this.errorIcon.getChildren().clear();
        if (icon != null) {
            this.errorIcon.getChildren().setAll(new Node[]{icon});
            StackPane.setAlignment(icon, Pos.CENTER_RIGHT);
        }

        this.errorContainer.setVisible(true);
    }

    private void hideError() {
        this.errorLabel.setText((String)null);
        this.errorIcon.getChildren().clear();
        this.errorContainer.setVisible(false);
    }

    /** {@inheritDoc} */
    @Override protected String maskText(String txt) {
        if (getSkinnable() instanceof PasswordField) {
            int n = txt.length();
            StringBuilder passwordBuilder = new StringBuilder(n);
            for (int i = 0; i < n; i++) {
                passwordBuilder.append("*");
            }

            return passwordBuilder.toString();
        } else {
            return txt;
        }
    }
}
