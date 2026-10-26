// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXRippler;
import javafx.animation.FadeTransition;
import javafx.beans.binding.Bindings;
import javafx.css.*;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Wraps content with a rectangular hover highlight and a ripple layer that can appear in front of or behind it.
/// The hover highlight follows the launcher theme without replacing this pane's background.
@NotNullByDefault
public class RipplerContainer extends StackPane {
    /// The CSS class assigned to this pane.
    private static final String DEFAULT_STYLE_CLASS = "rippler-container";

    /// The corner radii used by the ripple clipping masks.
    private static final CornerRadii DEFAULT_RADII = new CornerRadii(3);

    /// The ripple paint used before CSS or a caller supplies a value.
    private static final Color DEFAULT_RIPPLER_FILL = Color.rgb(0, 200, 255);

    /// The content supplied by the caller.
    private final Node container;

    /// An unmanaged, mouse-transparent highlight underneath the content and ripple layer.
    private final Region hoverBackground = new Region();

    /// The ripple layer, including an empty control that receives background mouse presses.
    private final JFXRippler buttonRippler = new JFXRippler(new Region()) {
        /// An opaque background defining the default rounded clipping area.
        private static final Background DEFAULT_MASK_BACKGROUND = new Background(new BackgroundFill(Color.WHITE, DEFAULT_RADII, Insets.EMPTY));

        /// Creates an independent clip sized to this ripple layer.
        @Override
        protected Node getMask() {
            // The ripple generator and its overlay require separate clip nodes.
            Region mask = new Region();
            mask.setBackground(DEFAULT_MASK_BACKGROUND);
            mask.resize(getWidth(), getHeight());
            return mask;
        }
    };

    /// The hover transition, allocated on the first animated hover change and reused thereafter.
    private @Nullable FadeTransition coverAnimation;

    /// Creates a wrapper with its ripple layer behind the supplied content.
    /// The content's `pickOnBounds` property is set to `false` so uncovered areas can
    /// receive ripple input.
    ///
    /// @param container the content to wrap
    /// @throws NullPointerException if `container` is `null`
    public RipplerContainer(Node container) {
        this.container = Objects.requireNonNull(container);

        getStyleClass().add(DEFAULT_STYLE_CLASS);
        buttonRippler.setPosition(JFXRippler.RipplerPos.BACK);
        buttonRippler.positionProperty().addListener(observable -> updateChildren());
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

        hoverBackground.setManaged(false);
        hoverBackground.setMouseTransparent(true);
        hoverBackground.setOpacity(0);
        hoverBackground.backgroundProperty().bind(Bindings.createObjectBinding(() -> {
            Color onSurface = Themes.getColorScheme().getOnSurface();
            return new Background(new BackgroundFill(
                    Color.color(onSurface.getRed(), onSurface.getGreen(), onSurface.getBlue(), 0.04),
                    CornerRadii.EMPTY, Insets.EMPTY));
        }, Themes.colorSchemeProperty()));

        updateChildren();

        var shape = new Rectangle();
        shape.widthProperty().bind(widthProperty());
        shape.heightProperty().bind(heightProperty());
        setShape(shape);

        addEventHandler(MouseEvent.MOUSE_ENTERED, event -> updateHover(true));
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> updateHover(false));
    }

    /// Moves the highlight toward its hover or idle opacity from its currently displayed opacity.
    private void updateHover(boolean hovered) {
        double target = hovered ? 1 : 0;
        if (!AnimationUtils.isAnimationEnabled()) {
            hoverBackground.setOpacity(target);
            return;
        }

        // Capture the displayed value before stopping so interrupted fades remain continuous.
        double current = hoverBackground.getOpacity();
        if (coverAnimation == null) {
            FadeTransition animation = new FadeTransition(Motion.SHORT4, hoverBackground);
            // The motion curves approximate their endpoints; leave no residual idle highlight.
            animation.setOnFinished(event -> hoverBackground.setOpacity(animation.getToValue()));
            coverAnimation = animation;
        } else {
            coverAnimation.stop();
        }
        coverAnimation.setFromValue(current);
        coverAnimation.setToValue(target);
        coverAnimation.setInterpolator(hovered ? Motion.EASE_IN : Motion.EASE_OUT);
        coverAnimation.playFromStart();
    }

    /// Lays out the managed children and stretches the highlight across the entire pane.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        hoverBackground.resizeRelocate(0, 0, getWidth(), getHeight());
    }

    /// Arranges the content and ripple layer in the selected order, above the hover highlight.
    /// When the ripple layer is behind the content, disables bounds-based picking on the content.
    protected void updateChildren() {
        Node container = getContainer();
        if (buttonRippler.getPosition() == JFXRippler.RipplerPos.BACK) {
            getChildren().setAll(hoverBackground, buttonRippler, container);
            container.setPickOnBounds(false);
        } else {
            getChildren().setAll(hoverBackground, container, buttonRippler);
        }
    }

    /// Places the ripple layer in front of or behind the content.
    /// Setting the current position again leaves the child list unchanged.
    ///
    /// @param pos the position of the ripple layer relative to the content
    /// @throws NullPointerException if `pos` is `null`
    public void setPosition(JFXRippler.RipplerPos pos) {
        buttonRippler.setPosition(Objects.requireNonNull(pos));
    }

    /// Returns the ripple layer. Changes to its position also update this pane's child order.
    public JFXRippler getRippler() {
        return buttonRippler;
    }

    /// Returns the content supplied to the constructor.
    public Node getContainer() {
        return container;
    }

    /// The CSS-configurable ripple paint, forwarded to the ripple layer when invalidated.
    private final StyleableObjectProperty<@Nullable Paint> ripplerFill = new StyleableObjectProperty<>(DEFAULT_RIPPLER_FILL) {
        /// Returns the owning wrapper.
        @Override
        public Object getBean() {
            return RipplerContainer.this;
        }

        /// Returns the JavaFX property name.
        @Override
        public String getName() {
            return "ripplerFill";
        }

        /// Returns the CSS metadata for the ripple paint.
        @Override
        public CssMetaData<? extends Styleable, @Nullable Paint> getCssMetaData() {
            return StyleableProperties.RIPPLER_FILL;
        }

        /// Applies the current paint to the ripple layer.
        @Override
        protected void invalidated() {
            buttonRippler.setRipplerFill(get());
        }
    };

    /// Returns the property controlling the ripple paint; `null` specifies no paint.
    public StyleableObjectProperty<@Nullable Paint> ripplerFillProperty() {
        return ripplerFill;
    }

    /// Returns the configured ripple paint, or `null` if no paint is configured.
    public @Nullable Paint getRipplerFill() {
        return ripplerFillProperty().get();
    }

    /// Sets the paint used for subsequently created ripples.
    /// A [Color] supplies its RGB components at the ripple layer's fixed opacities and
    /// also updates the overlay. Other paints are used unchanged for ripples and hide the overlay.
    ///
    /// @param ripplerFill the ripple paint, or `null` for no paint
    public void setRipplerFill(@Nullable Paint ripplerFill) {
        ripplerFillProperty().set(ripplerFill);
    }

    /// Removes the pending ripple immediately, if present.
    /// Ripples already fading out are unaffected; the hover highlight is unchanged.
    public void releaseRippleImmediately() {
        buttonRippler.releaseRippleImmediately();
    }

    /// Returns this wrapper's CSS metadata, including inherited region properties.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    /// Returns the immutable CSS metadata shared by wrapper instances.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /// Holds the shared CSS metadata without allocating a list for each wrapper.
    private static final class StyleableProperties {
        /// Maps the ripple paint to the JFoenix-compatible CSS property.
        private static final CssMetaData<RipplerContainer, @Nullable Paint> RIPPLER_FILL = new CssMetaData<>("-jfx-rippler-fill", PaintConverter.getInstance(), DEFAULT_RIPPLER_FILL) {
            /// Reports whether CSS may assign the unbound ripple paint property.
            @Override
            public boolean isSettable(RipplerContainer styleable) {
                return styleable.ripplerFill == null || !styleable.ripplerFill.isBound();
            }

            /// Returns the ripple paint property of the supplied wrapper.
            @Override
            public StyleableProperty<@Nullable Paint> getStyleableProperty(RipplerContainer styleable) {
                return styleable.ripplerFillProperty();
            }
        };

        /// The inherited metadata followed by the ripple paint metadata.
        private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var styleables = new ArrayList<>(StackPane.getClassCssMetaData());
            styleables.add(RIPPLER_FILL);
            STYLEABLES = List.copyOf(styleables);
        }
    }
}
