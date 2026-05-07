//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.jfoenix.skins;

import com.jfoenix.adapters.skins.ButtonSkin;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.effects.JFXDepthManager;
import com.jfoenix.transitions.CachedTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.animation.Transition;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;

public class JFXButtonSkin extends ButtonSkin {
    private final StackPane buttonContainer = new StackPane();
    private final JFXRippler buttonRippler = new JFXRippler(new StackPane()) {
        protected Node getMask() {
            StackPane mask = new StackPane();
            mask.shapeProperty().bind(JFXButtonSkin.this.buttonContainer.shapeProperty());
            mask.backgroundProperty().bind(Bindings.createObjectBinding(() -> new Background(
                    new BackgroundFill(Color.WHITE,
                            JFXButtonSkin.this.buttonContainer.backgroundProperty().get() != null && !JFXButtonSkin.this.buttonContainer.getBackground().getFills().isEmpty()
                                    ? JFXButtonSkin.this.buttonContainer.getBackground().getFills().get(0).getRadii()
                                    : JFXButtonSkin.this.defaultRadii, JFXButtonSkin.this.buttonContainer.backgroundProperty().get() != null && !JFXButtonSkin.this.buttonContainer.getBackground().getFills().isEmpty() ? JFXButtonSkin.this.buttonContainer.getBackground().getFills().get(0).getInsets() : Insets.EMPTY)),
                    JFXButtonSkin.this.buttonContainer.backgroundProperty()));
            mask.resize(JFXButtonSkin.this.buttonContainer.getWidth() - JFXButtonSkin.this.buttonContainer.snappedRightInset() - JFXButtonSkin.this.buttonContainer.snappedLeftInset(), JFXButtonSkin.this.buttonContainer.getHeight() - JFXButtonSkin.this.buttonContainer.snappedBottomInset() - JFXButtonSkin.this.buttonContainer.snappedTopInset());
            return mask;
        }

        private void initListeners() {
            this.ripplerPane.setOnMousePressed((event) -> {
                if (JFXButtonSkin.this.releaseManualRippler != null) {
                    JFXButtonSkin.this.releaseManualRippler.run();
                }

                JFXButtonSkin.this.releaseManualRippler = null;
                this.createRipple(event.getX(), event.getY());
            });
        }
    };
    private Transition clickedAnimation;
    private final CornerRadii defaultRadii = new CornerRadii(3.0);
    private boolean invalid = true;
    private Runnable releaseManualRippler = null;

    public JFXButtonSkin(JFXButton button) {
        super(button);
        this.getSkinnable().armedProperty().addListener((o, oldVal, newVal) -> {
            if (newVal) {
                this.releaseManualRippler = this.buttonRippler.createManualRipple();
                if (this.clickedAnimation != null) {
                    this.clickedAnimation.setRate(1.0);
                    this.clickedAnimation.play();
                }
            } else {
                if (this.releaseManualRippler != null) {
                    this.releaseManualRippler.run();
                }

                if (this.clickedAnimation != null) {
                    this.clickedAnimation.setRate(-1.0);
                    this.clickedAnimation.play();
                }
            }

        });
        this.buttonContainer.getChildren().add(this.buttonRippler);
        button.buttonTypeProperty().addListener((o, oldVal, newVal) -> this.updateButtonType(newVal));
        button.setOnMousePressed((e) -> {
            if (this.clickedAnimation != null) {
                this.clickedAnimation.setRate(1.0F);
                this.clickedAnimation.play();
            }
        });
        button.setOnMouseReleased((e) -> {
            if (this.clickedAnimation != null) {
                this.clickedAnimation.setRate(-1.0F);
                this.clickedAnimation.play();
            }
        });

        ReadOnlyBooleanProperty focusVisibleProperty = FXUtils.focusVisibleProperty(button);
        if (focusVisibleProperty == null) {
            focusVisibleProperty = button.focusedProperty();
        }
        focusVisibleProperty.addListener((o, oldVal, newVal) -> {
            if (newVal) {
                if (!this.getSkinnable().isPressed()) {
                    this.buttonRippler.showOverlay();
                }
            } else {
                this.buttonRippler.hideOverlay();
            }
        });
        button.pressedProperty().addListener((o, oldVal, newVal) -> this.buttonRippler.hideOverlay());
        button.setPickOnBounds(false);
        this.buttonContainer.setPickOnBounds(false);
        this.buttonContainer.shapeProperty().bind(this.getSkinnable().shapeProperty());
        this.buttonContainer.borderProperty().bind(this.getSkinnable().borderProperty());
        this.buttonContainer.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
            if (button.getBackground() == null || this.isJavaDefaultBackground(button.getBackground()) || this.isJavaDefaultClickedBackground(button.getBackground())) {
                button.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, this.defaultRadii, null)));
            }

            try {
                return new Background(new BackgroundFill(this.getSkinnable().getBackground() != null ? this.getSkinnable().getBackground().getFills().get(0).getFill() : Color.TRANSPARENT, this.getSkinnable().getBackground() != null ? this.getSkinnable().getBackground().getFills().get(0).getRadii() : this.defaultRadii, Insets.EMPTY));
            } catch (Exception var3) {
                return this.getSkinnable().getBackground();
            }
        }, this.getSkinnable().backgroundProperty()));
        button.ripplerFillProperty().addListener((o, oldVal, newVal) -> this.buttonRippler.setRipplerFill(newVal));
        if (button.getBackground() == null || this.isJavaDefaultBackground(button.getBackground())) {
            button.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, this.defaultRadii, null)));
        }

        this.updateButtonType(button.getButtonType());
        this.updateChildren();
    }

    protected void updateChildren() {
        super.updateChildren();
        if (this.buttonContainer != null) {
            this.getChildren().add(0, this.buttonContainer);
        }

        for (int i = 1; i < this.getChildren().size(); ++i) {
            this.getChildren().get(i).setMouseTransparent(true);
        }
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        if (this.invalid) {
            if (((JFXButton) this.getSkinnable()).getRipplerFill() == null) {
                for (int i = this.getChildren().size() - 1; i >= 1; --i) {
                    if (this.getChildren().get(i) instanceof Shape shape) {
                        this.buttonRippler.setRipplerFill(shape.getFill());
                        shape.fillProperty().addListener((o, oldVal, newVal) -> this.buttonRippler.setRipplerFill(newVal));
                        break;
                    }

                    if (this.getChildren().get(i) instanceof Label label) {
                        this.buttonRippler.setRipplerFill(label.getTextFill());
                        label.textFillProperty().addListener((o, oldVal, newVal) -> this.buttonRippler.setRipplerFill(newVal));
                        break;
                    }
                }
            } else {
                this.buttonRippler.setRipplerFill(((JFXButton) this.getSkinnable()).getRipplerFill());
            }

            this.invalid = false;
        }

        double shift = 1.0F;
        this.buttonContainer.resizeRelocate(this.getSkinnable().getLayoutBounds().getMinX() - shift, this.getSkinnable().getLayoutBounds().getMinY() - shift, this.getSkinnable().getWidth() + (double) 2.0F * shift, this.getSkinnable().getHeight() + (double) 2.0F * shift);
        this.layoutLabelInArea(x, y, w, h);
    }

    private boolean isJavaDefaultBackground(Background background) {
        try {
            String firstFill = background.getFills().get(0).getFill().toString();
            return "0xffffffba".equals(firstFill) || "0xffffffbf".equals(firstFill) || "0xffffff12".equals(firstFill) || "0xffffffbd".equals(firstFill);
        } catch (Exception var3) {
            return false;
        }
    }

    private boolean isJavaDefaultClickedBackground(Background background) {
        try {
            return "0x039ed3ff".equals(background.getFills().get(0).getFill().toString());
        } catch (Exception var3) {
            return false;
        }
    }

    private void updateButtonType(JFXButton.ButtonType type) {
        switch (type) {
            case RAISED -> {
                JFXDepthManager.setDepth(this.buttonContainer, 2);
                this.clickedAnimation = new ButtonClickTransition();
            }
            case FLAT -> this.buttonContainer.setEffect(null);
        }
    }

    private class ButtonClickTransition extends CachedTransition {
        public ButtonClickTransition() {
            super(JFXButtonSkin.this.buttonContainer, new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).radiusProperty(), JFXDepthManager.getShadowAt(2).radiusProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).spreadProperty(), JFXDepthManager.getShadowAt(2).spreadProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).offsetXProperty(), JFXDepthManager.getShadowAt(2).offsetXProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).offsetYProperty(), JFXDepthManager.getShadowAt(2).offsetYProperty().get(), Interpolator.EASE_BOTH)),
                    new KeyFrame(Duration.millis(1000.0F),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).radiusProperty(), JFXDepthManager.getShadowAt(5).radiusProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).spreadProperty(), JFXDepthManager.getShadowAt(5).spreadProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).offsetXProperty(), JFXDepthManager.getShadowAt(5).offsetXProperty().get(), Interpolator.EASE_BOTH),
                            new KeyValue(((DropShadow) JFXButtonSkin.this.buttonContainer.getEffect()).offsetYProperty(), JFXDepthManager.getShadowAt(5).offsetYProperty().get(), Interpolator.EASE_BOTH))));
            this.setCycleDuration(Duration.seconds(0.2));
            this.setDelay(Duration.seconds(0.0F));
        }
    }
}
