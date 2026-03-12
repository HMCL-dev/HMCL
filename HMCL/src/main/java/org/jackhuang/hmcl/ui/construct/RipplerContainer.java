/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXRippler;
import javafx.animation.Transition;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;

import java.util.ArrayList;
import java.util.List;

public class RipplerContainer extends StackPane {
    private static final String DEFAULT_STYLE_CLASS = "rippler-container";
    private static final CornerRadii DEFAULT_RADII = new CornerRadii(3);
    private static final Color DEFAULT_RIPPLER_FILL = Color.rgb(0, 200, 255);

    private final Node container;

    private final StackPane buttonContainer = new StackPane();
    private final JFXRippler buttonRippler = new JFXRippler(new StackPane()) {
        private static final Background DEFAULT_MASK_BACKGROUND = new Background(new BackgroundFill(Color.WHITE, DEFAULT_RADII, Insets.EMPTY));

        @Override
        protected Node getMask() {
            StackPane mask = new StackPane();
            mask.shapeProperty().bind(buttonContainer.shapeProperty());
            mask.setBackground(DEFAULT_MASK_BACKGROUND);
            mask.resize(
                    buttonContainer.getWidth() - buttonContainer.snappedRightInset() - buttonContainer.snappedLeftInset(),
                    buttonContainer.getHeight() - buttonContainer.snappedBottomInset() - buttonContainer.snappedTopInset()
            );
            return mask;
        }
    };

    private Transition coverAnimation;

    public RipplerContainer(Node container) {
        this.container = container;

        getStyleClass().add(DEFAULT_STYLE_CLASS);
        buttonRippler.setPosition(JFXRippler.RipplerPos.BACK);
        buttonContainer.getChildren().add(buttonRippler);
        focusedProperty().addListener((a, b, newValue) -> {
            if (newValue) {
                if (!isPressed())
                    buttonRippler.showOverlay();
            } else {
                buttonRippler.hideOverlay();
            }
        });
        pressedProperty().addListener(o -> buttonRippler.hideOverlay());
        setPickOnBounds(false);

        buttonContainer.setPickOnBounds(false);

        updateChildren();

        var shape = new Rectangle();
        shape.widthProperty().bind(widthProperty());
        shape.heightProperty().bind(heightProperty());
        setShape(shape);

        EventHandler<MouseEvent> mouseEventHandler;
        if (AnimationUtils.isAnimationEnabled()) {
            mouseEventHandler = event -> {
                if (coverAnimation != null) {
                    coverAnimation.stop();
                    coverAnimation = null;
                }

                if (event.getEventType() == MouseEvent.MOUSE_ENTERED) {
                    coverAnimation = new Transition() {
                        {
                            setCycleDuration(Motion.SHORT4);
                            setInterpolator(Motion.EASE_IN);
                        }

                        @Override
                        protected void interpolate(double frac) {
                            interpolateBackground(frac);
                        }
                    };
                } else {
                    coverAnimation = new Transition() {
                        {
                            setCycleDuration(Motion.SHORT4);
                            setInterpolator(Motion.EASE_OUT);
                        }

                        @Override
                        protected void interpolate(double frac) {
                            interpolateBackground(1 - frac);
                        }
                    };
                }

                coverAnimation.play();
            };
        } else {
            mouseEventHandler = event ->
                    interpolateBackground(event.getEventType() == MouseEvent.MOUSE_ENTERED ? 1 : 0);
        }

        addEventHandler(MouseEvent.MOUSE_ENTERED, mouseEventHandler);
        addEventHandler(MouseEvent.MOUSE_EXITED, mouseEventHandler);
    }

    private void interpolateBackground(double frac) {
        if (frac < 0.01) {
            setBackground(null);
        } else {
            Color onSurface = Themes.getColorScheme().getOnSurface();
            setBackground(new Background(new BackgroundFill(
                    Color.color(onSurface.getRed(), onSurface.getGreen(), onSurface.getBlue(), frac * 0.04),
                    CornerRadii.EMPTY, Insets.EMPTY)));
        }
    }

    protected void updateChildren() {
        Node container = getContainer();
        if (buttonRippler.getPosition() == JFXRippler.RipplerPos.BACK) {
            getChildren().setAll(buttonContainer, container);
            container.setPickOnBounds(false);
        } else {
            getChildren().setAll(container, buttonContainer);
            buttonContainer.setPickOnBounds(false);
        }
    }

    public void setPosition(JFXRippler.RipplerPos pos) {
        buttonRippler.setPosition(pos);
        updateChildren();
    }

    public JFXRippler getRippler() {
        return buttonRippler;
    }

    public Node getContainer() {
        return container;
    }

    private final StyleableObjectProperty<Paint> ripplerFill = new StyleableObjectProperty<>(DEFAULT_RIPPLER_FILL) {
        @Override
        public Object getBean() {
            return RipplerContainer.this;
        }

        @Override
        public String getName() {
            return "ripplerFill";
        }

        @Override
        public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
            return StyleableProperties.RIPPLER_FILL;
        }

        @Override
        protected void invalidated() {
            buttonRippler.setRipplerFill(get());
        }
    };

    public StyleableObjectProperty<Paint> ripplerFillProperty() {
        return ripplerFill;
    }

    public Paint getRipplerFill() {
        return ripplerFillProperty().get();
    }

    public void setRipplerFill(Paint ripplerFill) {
        ripplerFillProperty().set(ripplerFill);
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    private final static class StyleableProperties {
        private static final CssMetaData<RipplerContainer, Paint> RIPPLER_FILL = new CssMetaData<>("-jfx-rippler-fill", PaintConverter.getInstance(), DEFAULT_RIPPLER_FILL) {
            @Override
            public boolean isSettable(RipplerContainer styleable) {
                return styleable.ripplerFill == null || !styleable.ripplerFill.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(RipplerContainer styleable) {
                return styleable.ripplerFillProperty();
            }
        };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var styleables = new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(RIPPLER_FILL);
            STYLEABLES = List.copyOf(styleables);
        }
    }
}
