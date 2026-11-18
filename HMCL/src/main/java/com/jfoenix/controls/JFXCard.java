// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.beans.DefaultProperty;
import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.scene.layout.Border;
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
/// Optional headline, subhead, supporting text, media, custom content, and actions are laid out
/// by the card. Text sections have 16-pixel padding; adjacent sections share that spacing.
/// Actions are end-aligned with 8-pixel gaps and wrap when necessary. Empty, invisible, or
/// unmanaged optional nodes occupy no space. Text wraps and follows the effective node orientation.
/// Media can precede or follow the header, or appear beside the text. The card's own padding
/// adds an outer inset to these sections. Only internal content is clipped, preserving the shadow.
///
/// Card layouts are flexible: these sections implement common Material configurations, rather
/// than requiring every card to contain every element. Collection spacing belongs to the parent.
///
/// Cards are noninteractive by default. Enable [#interactiveProperty()] for background actions,
/// then install an [#onActionProperty()] handler. Descendant controls retain their own input;
/// decorative nodes can be made mouse-transparent to let background clicks pass through.
/// Unmodified Space and Enter activate the focused card on release. Losing focus, disabling
/// interaction, hiding the card or an ancestor, or detaching its scene cancels a pending keyboard activation.
/// Feedback is the uniform state layer provided by [RipplerContainer], behind the content;
/// opaque content can obscure it. No expanding ripple is created.
///
/// Use the slot properties and [#getActions()] instead of modifying the inherited child list or the internal
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

    /// Arranges the optional content blocks and owns their internal layout containers.
    private final JFXCardLayout cardLayout;

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
        cardLayout = new JFXCardLayout(this);
        contentPane = (StackPane) getContainer();
        contentPane.getStyleClass().add("card-content");
        contentPane.setClip(contentClip);
        contentPane.getChildren().setAll(cardLayout);
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

    /// The optional primary title.
    private final StringProperty headline = new SimpleStringProperty(this, "headline", "");

    /// Returns the primary title property; null and empty strings omit its label.
    public final StringProperty headlineProperty() {
        return headline;
    }

    /// Returns the primary title, or null if explicitly cleared to null.
    public final @Nullable String getHeadline() {
        return headline.get();
    }

    /// Sets the primary title; text wraps within the available width.
    public final void setHeadline(@Nullable String value) {
        headline.set(value);
    }

    /// The optional secondary title.
    private final StringProperty subhead = new SimpleStringProperty(this, "subhead", "");

    /// Returns the secondary title property; null and empty strings omit its label.
    public final StringProperty subheadProperty() {
        return subhead;
    }

    /// Returns the secondary title, or null if explicitly cleared to null.
    public final @Nullable String getSubhead() {
        return subhead.get();
    }

    /// Sets the secondary title; text wraps within the available width.
    public final void setSubhead(@Nullable String value) {
        subhead.set(value);
    }

    /// The optional plain supporting text.
    private final StringProperty supportingText = new SimpleStringProperty(this, "supportingText", "");

    /// Returns the plain supporting text property; null and empty strings omit its label.
    public final StringProperty supportingTextProperty() {
        return supportingText;
    }

    /// Returns the plain supporting text, or null if explicitly cleared to null.
    public final @Nullable String getSupportingText() {
        return supportingText.get();
    }

    /// Sets the plain supporting text; text wraps within the available width.
    public final void setSupportingText(@Nullable String value) {
        supportingText.set(value);
    }

    /// The optional leading header graphic node.
    private final ObjectProperty<@Nullable Node> headerGraphic = new SimpleObjectProperty<>(this, "headerGraphic");

    /// Returns the leading header graphic property, initially null.
    /// The graphic keeps its preferred size; supply a suitably sized avatar or icon.
    /// Nodes must be distinct from all other card slots and valid children of a pane.
    /// Invisible or unmanaged nodes are omitted without changing their properties.
    public final ObjectProperty<@Nullable Node> headerGraphicProperty() {
        return headerGraphic;
    }

    /// Returns the leading header graphic, or null.
    public final @Nullable Node getHeaderGraphic() {
        return headerGraphic.get();
    }

    /// Sets [#headerGraphicProperty()]; null removes the node.
    public final void setHeaderGraphic(@Nullable Node value) {
        headerGraphic.set(value);
    }

    /// The optional trailing header action node.
    private final ObjectProperty<@Nullable Node> headerAction = new SimpleObjectProperty<>(this, "headerAction");

    /// Returns the trailing header action property, initially null.
    /// This node retains its own input and is placed after the header text.
    /// Nodes must be distinct from all other card slots and valid children of a pane.
    /// Invisible or unmanaged nodes are omitted without changing their properties.
    public final ObjectProperty<@Nullable Node> headerActionProperty() {
        return headerAction;
    }

    /// Returns the trailing header action, or null.
    public final @Nullable Node getHeaderAction() {
        return headerAction.get();
    }

    /// Sets [#headerActionProperty()]; null removes the node.
    public final void setHeaderAction(@Nullable Node value) {
        headerAction.set(value);
    }

    /// The optional media node.
    private final ObjectProperty<@Nullable Node> media = new SimpleObjectProperty<>(this, "media");

    /// Returns the media property, initially null.
    /// Resizable nodes fill the media frame. Other nodes are uniformly scaled and center-cropped
    /// through an internal wrapper, preserving their transforms and clips.
    /// Nodes must be distinct from all other card slots and valid children of a pane.
    /// Invisible or unmanaged nodes are omitted without changing their properties.
    public final ObjectProperty<@Nullable Node> mediaProperty() {
        return media;
    }

    /// Returns the media, or null.
    public final @Nullable Node getMedia() {
        return media.get();
    }

    /// Sets [#mediaProperty()]; null removes the node.
    public final void setMedia(@Nullable Node value) {
        media.set(value);
    }

    /// The optional custom body content node.
    private final ObjectProperty<@Nullable Node> content = new SimpleObjectProperty<>(this, "content");

    /// Returns the custom body content property, initially null.
    /// This node follows supporting text, separated by 16 pixels when both are present.
    /// It retains its own input, clip, and picking configuration.
    /// Nodes must be distinct from all other card slots and valid children of a pane.
    /// Invisible or unmanaged nodes are omitted without changing their properties.
    public final ObjectProperty<@Nullable Node> contentProperty() {
        return content;
    }

    /// Returns the custom body content, or null.
    public final @Nullable Node getContent() {
        return content.get();
    }

    /// Sets [#contentProperty()]; null removes the node.
    public final void setContent(@Nullable Node value) {
        content.set(value);
    }

    /// Mutable action nodes in visual and focus order.
    private final ObservableList<Node> actions = FXCollections.observableArrayList();

    /// Returns the mutable action list, initially empty. Nodes must be non-null, distinct from
    /// each other and all other slots, and valid children of a pane. Visible, managed actions
    /// wrap in insertion order, aligned to the end of each row; RTL mirrors this alignment.
    /// Button appearance, accessible names, and action handlers remain the caller's responsibility.
    public final ObservableList<Node> getActions() {
        return actions;
    }

    /// The position of media relative to the text sections.
    private final ObjectProperty<@Nullable MediaPlacement> mediaPlacement =
            new SimpleObjectProperty<>(this, "mediaPlacement", MediaPlacement.TOP);

    /// Returns the media placement property; null uses [MediaPlacement#TOP].
    public final ObjectProperty<@Nullable MediaPlacement> mediaPlacementProperty() {
        return mediaPlacement;
    }

    /// Returns the effective media placement.
    public final MediaPlacement getMediaPlacement() {
        @Nullable MediaPlacement value = mediaPlacement.get();
        return value == null ? MediaPlacement.TOP : value;
    }

    /// Sets [#mediaPlacementProperty()] without replacing the media node.
    public final void setMediaPlacement(@Nullable MediaPlacement value) {
        mediaPlacement.set(value);
    }

    /// The preferred width-to-height ratio of full-width media.
    private final DoubleProperty mediaAspectRatio = new SimpleDoubleProperty(this, "mediaAspectRatio", 16.0 / 9.0);

    /// Returns the width-to-height ratio for top or below-header media, initially 16:9.
    /// Nonpositive or nonfinite values fall back to 16:9. This is a configurable presentation
    /// default, not a required Material aspect ratio. Side media uses a square frame.
    public final DoubleProperty mediaAspectRatioProperty() {
        return mediaAspectRatio;
    }

    /// Returns the requested full-width media aspect ratio.
    public final double getMediaAspectRatio() {
        return mediaAspectRatio.get();
    }

    /// Sets [#mediaAspectRatioProperty()].
    public final void setMediaAspectRatio(double value) {
        mediaAspectRatio.set(value);
    }

    /// The preferred side-media width in logical pixels.
    private final DoubleProperty sideMediaSize = new SimpleDoubleProperty(this, "sideMediaSize", 80);

    /// Returns the preferred square size for side media, initially 80 logical pixels.
    /// Nonpositive or nonfinite values fall back to 80. Under width pressure the text and
    /// thumbnail can shrink; callers should provide enough width for their chosen content.
    public final DoubleProperty sideMediaSizeProperty() {
        return sideMediaSize;
    }

    /// Returns the requested side-media size in logical pixels.
    public final double getSideMediaSize() {
        return sideMediaSize.get();
    }

    /// Sets [#sideMediaSizeProperty()].
    public final void setSideMediaSize(double value) {
        sideMediaSize.set(value);
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

    /// Clips sections inside the border, preserving its rounded stroke beneath full-bleed media.
    private void updateClip() {
        @Nullable Background background = getBackground();
        CornerRadii radii = background == null || background.getFills().isEmpty()
                ? CornerRadii.EMPTY : background.getFills().get(0).getRadii();
        @Nullable Border border = getBorder();
        Insets insets = border == null ? Insets.EMPTY : border.getInsets();
        CornerRadii innerRadii = new CornerRadii(
                innerRadius(radii.getTopLeftHorizontalRadius(), radii.isTopLeftHorizontalRadiusAsPercentage(), getWidth(), insets.getLeft()),
                innerRadius(radii.getTopLeftVerticalRadius(), radii.isTopLeftVerticalRadiusAsPercentage(), getHeight(), insets.getTop()),
                innerRadius(radii.getTopRightVerticalRadius(), radii.isTopRightVerticalRadiusAsPercentage(), getHeight(), insets.getTop()),
                innerRadius(radii.getTopRightHorizontalRadius(), radii.isTopRightHorizontalRadiusAsPercentage(), getWidth(), insets.getRight()),
                innerRadius(radii.getBottomRightHorizontalRadius(), radii.isBottomRightHorizontalRadiusAsPercentage(), getWidth(), insets.getRight()),
                innerRadius(radii.getBottomRightVerticalRadius(), radii.isBottomRightVerticalRadiusAsPercentage(), getHeight(), insets.getBottom()),
                innerRadius(radii.getBottomLeftVerticalRadius(), radii.isBottomLeftVerticalRadiusAsPercentage(), getHeight(), insets.getBottom()),
                innerRadius(radii.getBottomLeftHorizontalRadius(), radii.isBottomLeftHorizontalRadiusAsPercentage(), getWidth(), insets.getLeft()),
                false, false, false, false, false, false, false, false);
        contentClip.setBackground(new Background(new BackgroundFill(Color.BLACK, innerRadii, insets)));
    }

    /// Resolves a background radius and removes the adjacent border inset without making it negative.
    private static double innerRadius(double radius, boolean percentage, double size, double inset) {
        return Math.max(0, (percentage ? radius * size : radius) - inset);
    }

    /// Positions the content clip in card coordinates, including padding and border insets.
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        contentClip.resizeRelocate(-contentPane.getLayoutX(), -contentPane.getLayoutY(), getWidth(), getHeight());
        updateClip();
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

    /// Places media around the header and supporting content without reordering action nodes.
    @NotNullByDefault
    public enum MediaPlacement {
        /// Full-width media above all text sections.
        TOP,
        /// Full-width media between the header and supporting content.
        BELOW_HEADER,
        /// A square thumbnail at the leading side of the header and supporting content.
        START,
        /// A square thumbnail at the trailing side of the header and supporting content.
        END
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
