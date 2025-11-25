//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package com.jfoenix.skins;

import com.jfoenix.adapters.ExceptionHelper;
import com.jfoenix.adapters.ReflectionHelper;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.effects.JFXDepthManager;

import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableProperty;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.skin.ColorPickerSkin;
import javafx.scene.control.skin.ComboBoxBaseSkin;
import javafx.scene.control.skin.ComboBoxPopupControl;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public class JFXColorPickerSkin extends ColorPickerSkin {
    private static final VarHandle arrowButtonField = ReflectionHelper.getHandle(ComboBoxBaseSkin.class, "arrowButton", StackPane.class);

    private final Label displayNode;
    private final Pane pickerColorBox;
    private final StackPane pickerColorClip;
    private JFXColorPalette popupContent;
    private final JFXColorPicker colorPicker;
    private final List<Color> listedColors;
    StyleableBooleanProperty colorLabelVisible;

    public JFXColorPickerSkin(JFXColorPicker colorPicker, List<Color> listedColors) {
        super(colorPicker);
        this.colorLabelVisible = new SimpleStyleableBooleanProperty(JFXColorPickerSkin.StyleableProperties.COLOR_LABEL_VISIBLE, this, "colorLabelVisible", true);
        this.colorPicker = colorPicker;
        this.listedColors = listedColors;
        this.displayNode = new Label();
        this.displayNode.getStyleClass().add("color-picker-label");
        this.displayNode.setManaged(false);
        this.displayNode.setMouseTransparent(true);
        this.pickerColorBox = new Pane();
        this.pickerColorBox.getStyleClass().add("picker-color");
        this.pickerColorBox.setBackground(new Background(
                new BackgroundFill(Color.valueOf("#fafafa"),
                        new CornerRadii(3.0),
                        Insets.EMPTY)));
        this.pickerColorClip = new StackPane();
        this.pickerColorClip.backgroundProperty().bind(Bindings.createObjectBinding(() -> new Background(new BackgroundFill(Color.WHITE, this.pickerColorBox.backgroundProperty().get() != null ? this.pickerColorBox.getBackground().getFills().get(0).getRadii() : new CornerRadii(3.0F), this.pickerColorBox.backgroundProperty().get() != null ? this.pickerColorBox.getBackground().getFills().get(0).getInsets() : Insets.EMPTY)), this.pickerColorBox.backgroundProperty()));
        this.pickerColorBox.setClip(this.pickerColorClip);
        JFXButton button = new JFXButton("");
        button.ripplerFillProperty().bind(this.displayNode.textFillProperty());
        button.minWidthProperty().bind(this.pickerColorBox.widthProperty());
        button.minHeightProperty().bind(this.pickerColorBox.heightProperty());
        button.addEventHandler(Event.ANY, (event) -> this.getSkinnable().fireEvent(event));
        this.pickerColorBox.getChildren().add(button);
        this.updateColor();
        this.getChildren().add(this.pickerColorBox);
        if (arrowButtonField != null) {
            this.getChildren().remove((StackPane) arrowButtonField.get(this));
        }

        JFXDepthManager.setDepth(this.getSkinnable(), 1);
        this.getPopupContent();
        super.getPopupContent();
        this.registerChangeListener(colorPicker.valueProperty(), ignored -> this.updateColor());
        this.registerChangeListener(colorPicker.showingProperty(), ignored -> {
            if (this.getSkinnable().isShowing()) {
                this.show();
            } else if (!this.popupContent.isCustomColorDialogShowing()) {
                this.hide();
            }

        });
        this.colorLabelVisible.addListener((invalidate) -> {
            if (this.colorLabelVisible.get()) {
                this.displayNode.setText(colorDisplayName((Color) ((ComboBoxBase<?>) this.getSkinnable()).getValue()));
            } else {
                this.displayNode.setText("");
            }
        });
    }

    protected PopupControl getPopup2() {
        return ReflectionHelper.invoke(ComboBoxPopupControl.class, this, "getPopup");
    }

    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (!this.colorLabelVisible.get()) {
            return super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset);
        } else {
            String displayNodeText = this.displayNode.getText();
            double width = 0.0;
            this.displayNode.setText("#00000000");
            width = Math.max(width, super.computePrefWidth(height, topInset, rightInset, bottomInset, leftInset));
            this.displayNode.setText(displayNodeText);
            return width;
        }
    }

    static String colorDisplayName(Color c) {
        return c != null ? formatHexString(c) : null;
    }

    static String tooltipString(Color c) {
        return c != null ? formatHexString(c) : null;
    }

    static String formatHexString(Color c) {
        return c != null
                ? "#%02X%02X%02X".formatted(Math.round(c.getRed() * 255.0), Math.round(c.getGreen() * 255.0), Math.round(c.getBlue() * 255.0F))
                : null;
    }

    protected Node getPopupContent() {
        if (this.popupContent == null) {
            this.popupContent = new JFXColorPalette(this.colorPicker, this.listedColors);
            this.popupContent.customColorLink.textProperty().bind(this.colorPicker.customColorTextProperty());
            this.popupContent.customColorLabel.textProperty().bind(this.colorPicker.recentColorsTextProperty());
            this.popupContent.setPopupControl(this.getPopup2());
        }

        return this.popupContent;
    }

    public void show() {
        super.show();
        ColorPicker colorPicker = (ColorPicker) this.getSkinnable();
        this.popupContent.updateSelection(colorPicker.getValue());
    }

    public Node getDisplayNode() {
        return this.displayNode;
    }

    private void updateColor() {
        ColorPicker colorPicker = (ColorPicker) this.getSkinnable();
        Circle colorCircle = new Circle();
        colorCircle.setFill(colorPicker.getValue());
        colorCircle.setLayoutX(this.pickerColorBox.getWidth() / 4.0);
        colorCircle.setLayoutY(this.pickerColorBox.getHeight() / 2.0);
        this.pickerColorBox.getChildren().add(colorCircle);
        Timeline animateColor = new Timeline(
                new KeyFrame(Duration.millis(240.0),
                        new KeyValue(colorCircle.radiusProperty(), 200, Interpolator.EASE_BOTH)));
        animateColor.setOnFinished((finish) -> {
            this.pickerColorBox.setBackground(
                    new Background(
                            new BackgroundFill(colorCircle.getFill(),
                                    this.pickerColorBox.getBackground().getFills().get(0).getRadii(),
                                    this.pickerColorBox.getBackground().getFills().get(0).getInsets())));
            this.pickerColorBox.getChildren().remove(colorCircle);
        });
        animateColor.play();
        this.displayNode.setTextFill(colorPicker.getValue().grayscale().getRed() < 0.5
                ? Color.valueOf("rgba(255, 255, 255, 0.87)")
                : Color.valueOf("rgba(0, 0, 0, 0.87)"));
        if (this.colorLabelVisible.get()) {
            this.displayNode.setText(colorDisplayName(colorPicker.getValue()));
        } else {
            this.displayNode.setText("");
        }

    }

    public void syncWithAutoUpdate() {
        if (!this.getPopup2().isShowing() && this.getSkinnable().isShowing()) {
            this.getSkinnable().hide();
        }
    }

    protected void layoutChildren(double x, double y, double w, double h) {
        this.pickerColorBox.resizeRelocate(x - 1.0, y - 1.0, w + 2.0, h + 2.0);
        this.pickerColorClip.resize(w + 2.0, h + 2.0);
        super.layoutChildren(x, y, w, h);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return JFXColorPickerSkin.StyleableProperties.STYLEABLES;
    }

    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    private static final class StyleableProperties {
        private static final CssMetaData<ColorPicker, Boolean> COLOR_LABEL_VISIBLE;
        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            COLOR_LABEL_VISIBLE = new CssMetaData<>("-fx-color-label-visible", StyleConverter.getBooleanConverter(), Boolean.TRUE) {
                public boolean isSettable(ColorPicker n) {
                    JFXColorPickerSkin skin = (JFXColorPickerSkin) n.getSkin();
                    return skin.colorLabelVisible == null || !skin.colorLabelVisible.isBound();
                }

                public StyleableProperty<Boolean> getStyleableProperty(ColorPicker n) {
                    JFXColorPickerSkin skin = (JFXColorPickerSkin) n.getSkin();
                    return skin.colorLabelVisible;
                }
            };
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(SkinBase.getClassCssMetaData());
            styleables.add(COLOR_LABEL_VISIBLE);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
