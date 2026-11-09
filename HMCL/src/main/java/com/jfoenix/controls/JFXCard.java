// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.geometry.Insets;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Groups content and actions about one subject in a Material Design 3 surface.
/// The application stylesheet supplies elevated, filled, and outlined variants with 12-pixel corners.
/// Content uses StackPane sizing and alignment; the card's padding surrounds it. Only content is
/// clipped to the card's outline, leaving the outer shadow visible.
///
/// Cards are noninteractive by default. Enable [#interactiveProperty()] for background actions,
/// then install an [#onActionProperty()] handler. Descendant controls retain their own input;
/// decorative nodes can be made mouse-transparent to let background clicks pass through.
/// Unmodified Space and Enter activate the focused card on release. Losing focus, disabling
/// interaction, hiding the card or an ancestor, or detaching its scene cancels a pending keyboard activation.
/// Feedback is the uniform state layer provided by [RipplerContainer], behind the content;
/// opaque content can obscure it. No expanding ripple is created.
///
/// Use [#contentProperty()] instead of modifying the inherited child list or the internal
/// container returned by [#getContainer()]. Construction and changes must occur on the FX thread.
@DefaultProperty("content")
@NotNullByDefault
public class JFXCard extends RipplerContainer {
    /// Marks cards that accept whole-card activation.
    private static final PseudoClass INTERACTIVE = PseudoClass.getPseudoClass("interactive");

    /// Defines the card's stylesheet properties in addition to inherited feedback and layout properties.
    private static final StyleablePropertyFactory<JFXCard> FACTORY =
            new StyleablePropertyFactory<>(RipplerContainer.getClassCssMetaData());

    /// Selects the visual variant through CSS.
    private static final CssMetaData<JFXCard, CardType> CARD_TYPE = FACTORY.createEnumCssMetaData(
            CardType.class, "-jfx-card-type", card -> card.cardType, CardType.ELEVATED);

    /// Immutable metadata shared by all cards.
    private static final @Unmodifiable List<CssMetaData<? extends Styleable, ?>> STYLEABLES =
            List.copyOf(FACTORY.getCssMetaData());

    /// Hosts replaceable content without replacing the inherited interaction layer.
    private final StackPane contentPane;

    /// Clips content independently of the card's shadow and the caller's own clip.
    private final Region contentClip = new Region();

    /// Tracks ancestor visibility so hidden cards cannot retain pending keyboard activation.
    private final ObservableBooleanValue treeVisible = JFXNodeUtils.treeVisibleProperty(this);

    /// The key whose release may activate this card.
    private @Nullable KeyCode activationKey;

    /// Cancels pending keyboard activation when the observed window loses focus.
    private final InvalidationListener windowFocusListener = observable -> activationKey = null;

    /// Transfers focus observation when the scene changes windows.
    private final InvalidationListener windowListener = observable -> observeWindow();

    /// The scene currently supplying a window.
    private @Nullable Scene observedScene;

    /// The window whose focus changes cancel activation.
    private @Nullable Window observedWindow;

    /// Creates an empty, noninteractive elevated card.
    public JFXCard() {
        super(new StackPane());
        contentPane = (StackPane) getContainer();
        contentPane.getStyleClass().add("card-content");
        contentPane.setClip(contentClip);
        getStyleClass().add("jfx-card");
        focusTraversableProperty().bind(interactive);
        pressFeedbackEnabledProperty().bind(interactive);
        setAccessibleRole(AccessibleRole.PARENT);
        updateCardType();
        backgroundProperty().addListener(observable -> updateClip());
        updateClip();
        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (isInteractive() && !isDisabled() && event.getButton() == MouseButton.PRIMARY
                    && isBackgroundEvent(event)) {
                activationKey = null;
                requestFocus();
            }
        });
        addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
        addEventHandler(KeyEvent.KEY_RELEASED, this::keyReleased);
        focusedProperty().addListener(observable -> activationKey = null);
        disabledProperty().addListener(observable -> activationKey = null);
        treeVisible.addListener(observable -> activationKey = null);
        sceneProperty().addListener(observable -> observeScene());
    }

    /// Creates a noninteractive elevated card containing the supplied node, or an empty card for null.
    public JFXCard(@Nullable Node content) {
        this();
        setContent(content);
    }

    /// The caller-supplied content, or null for an empty card.
    private final ObjectProperty<@Nullable Node> content = new SimpleObjectProperty<>(this, "content") {
        /// Replaces the content while preserving the feedback layer and internal clip.
        @Override
        protected void invalidated() {
            @Nullable Node value = get();
            if (value == null) {
                contentPane.getChildren().clear();
            } else {
                value.setPickOnBounds(false);
                contentPane.getChildren().setAll(value);
            }
        }
    };

    /// Returns the replaceable content property, initially null.
    /// Assigned nodes have `pickOnBounds` set to false so empty layout space can activate the card.
    /// Their other input properties and clips are preserved. Nodes must be valid children of a pane.
    public final ObjectProperty<@Nullable Node> contentProperty() {
        return content;
    }

    /// Returns the current content, or null.
    public final @Nullable Node getContent() {
        return content.get();
    }

    /// Sets [#contentProperty()]; null removes the content.
    public final void setContent(@Nullable Node value) {
        content.set(value);
    }

    /// The CSS-configurable visual variant; null is rendered as elevated.
    private final StyleableObjectProperty<@Nullable CardType> cardType =
            new SimpleStyleableObjectProperty<>(CARD_TYPE, this, "cardType", CardType.ELEVATED) {
                /// Updates variant pseudo-classes without changing interaction or content.
                @Override
                protected void invalidated() {
                    updateCardType();
                }
            };

    /// Returns the visual variant property, styled by `-jfx-card-type` and initially elevated.
    /// A null value uses the elevated appearance.
    public final StyleableObjectProperty<@Nullable CardType> cardTypeProperty() {
        return cardType;
    }

    /// Returns the effective visual variant, treating null as [CardType#ELEVATED].
    public final CardType getCardType() {
        @Nullable CardType value = cardType.get();
        return value == null ? CardType.ELEVATED : value;
    }

    /// Sets [#cardTypeProperty()].
    public final void setCardType(@Nullable CardType value) {
        cardType.set(value);
    }

    /// Keeps exactly one visual variant active.
    private void updateCardType() {
        for (CardType type : CardType.values())
            pseudoClassStateChanged(type.pseudoClass, getCardType() == type);
    }

    /// Whether the card itself accepts activation, independently of its descendants.
    private final BooleanProperty interactive = new SimpleBooleanProperty(this, "interactive") {
        /// Updates styling and accessibility and cancels a pending key activation.
        @Override
        protected void invalidated() {
            activationKey = null;
            pseudoClassStateChanged(INTERACTIVE, get());
            setAccessibleRole(get() ? AccessibleRole.BUTTON : AccessibleRole.PARENT);
            resetFeedback();
        }
    };

    /// Returns whether background, keyboard, and accessibility activation are enabled; initially false.
    /// Focus traversal and inherited press feedback are bound to this property. The default stylesheet
    /// hides state feedback when false. Descendant controls remain usable. Installing an action
    /// handler alone does not enable interaction. Supply accessible text for interactive cards.
    public final BooleanProperty interactiveProperty() {
        return interactive;
    }

    /// Returns whether whole-card activation is enabled.
    public final boolean isInteractive() {
        return interactive.get();
    }

    /// Sets [#interactiveProperty()].
    public final void setInteractive(boolean value) {
        interactive.set(value);
    }

    /// Fires a card action only when interactive and enabled; does not activate descendants.
    @Override
    public void fire() {
        if (isInteractive())
            super.fire();
    }

    /// Arms unmodified Space or Enter presses directed at the focused card, ignoring key repeats.
    private void keyPressed(KeyEvent event) {
        if (event.getTarget() != this || !isFocused() || !isInteractive() || isDisabled())
            return;
        if ((event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER)
                && !event.isAltDown() && !event.isControlDown() && !event.isMetaDown() && !event.isShiftDown()) {
            if (activationKey == null)
                activationKey = event.getCode();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            activationKey = null;
        }
    }

    /// Activates once on the matching release; descendant key events never activate the card.
    private void keyReleased(KeyEvent event) {
        if (event.getTarget() == this && event.getCode() == activationKey) {
            activationKey = null;
            if (isFocused())
                fire();
            event.consume();
        }
    }

    /// Transfers scene observation and cancels pending keyboard activation.
    private void observeScene() {
        if (observedScene != null)
            observedScene.windowProperty().removeListener(windowListener);
        observedScene = getScene();
        if (observedScene != null)
            observedScene.windowProperty().addListener(windowListener);
        observeWindow();
    }

    /// Transfers window observation without retaining listeners on a detached window.
    private void observeWindow() {
        activationKey = null;
        if (observedWindow != null)
            observedWindow.focusedProperty().removeListener(windowFocusListener);
        observedWindow = observedScene == null ? null : observedScene.getWindow();
        if (observedWindow != null)
            observedWindow.focusedProperty().addListener(windowFocusListener);
    }

    /// Uses the first background fill's corners, matching the inherited state layer.
    private void updateClip() {
        @Nullable Background background = getBackground();
        CornerRadii radii = background == null || background.getFills().isEmpty()
                ? CornerRadii.EMPTY : background.getFills().get(0).getRadii();
        contentClip.setBackground(new Background(new BackgroundFill(Color.BLACK, radii, Insets.EMPTY)));
    }

    /// Positions the content clip in card coordinates, including padding and border insets.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        contentClip.resizeRelocate(-contentPane.getLayoutX(), -contentPane.getLayoutY(), getWidth(), getHeight());
    }

    /// Routes accessibility activation through the same enabled and interactive checks as other input.
    @Override
    public void executeAccessibleAction(AccessibleAction action, @Nullable Object... parameters) {
        if (action == AccessibleAction.FIRE)
            fire();
        else
            super.executeAccessibleAction(action, parameters);
    }

    /// Returns the card and inherited stylesheet metadata.
    public static @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }

    /// Returns the metadata used to style this card.
    @Override
    public @Unmodifiable List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    /// Selects the surface treatment without changing card behavior.
    @NotNullByDefault
    public enum CardType {
        /// Uses a low surface container color and a level-one shadow at rest.
        ELEVATED("elevated"),
        /// Uses the highest surface container color without a shadow at rest.
        FILLED("filled"),
        /// Uses the surface color and a one-pixel outline without a shadow at rest.
        OUTLINED("outlined");

        /// The pseudo-class selecting this variant's stylesheet rules.
        private final PseudoClass pseudoClass;

        /// Associates the variant with its CSS pseudo-class.
        CardType(String pseudoClass) {
            this.pseudoClass = PseudoClass.getPseudoClass(pseudoClass);
        }
    }
}
