// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package com.jfoenix.controls;

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Arranges the optional sections of a card while keeping caller-owned nodes intact.
@NotNullByDefault
final class JFXCardLayout extends VBox {
    /// Removes duplicate top padding between adjacent text or action sections.
    private static final PseudoClass CONTINUATION = PseudoClass.getPseudoClass("continuation");

    /// Removes section padding already supplied by a side-media row.
    private static final PseudoClass EMBEDDED = PseudoClass.getPseudoClass("embedded");

    /// The card whose properties supply the section contents.
    private final JFXCard card;

    /// Arranges the leading graphic, title block, and trailing action.
    private final HBox header = new HBox();

    /// Keeps the headline above the subhead.
    private final VBox titles = new VBox();

    /// The wrapping primary title.
    private final Label headline;

    /// The wrapping secondary title.
    private final Label subhead;

    /// The wrapping supporting text.
    private final Label supportingText;

    /// Contains supporting text followed by caller-defined content.
    private final VBox body = new VBox();

    /// Wraps action nodes into end-aligned rows.
    private final FlowPane actions = new FlowPane();

    /// Contains the header and body beside a thumbnail.
    private final VBox textColumn = new VBox();

    /// Places a thumbnail at either logical end of the text column.
    private final HBox sideRow = new HBox();

    /// Resizes or center-crops media without overwriting its clip or transforms.
    private final MediaFrame mediaFrame;

    /// Rebuilds sections when properties or caller-owned node visibility change.
    private final InvalidationListener structureListener = observable -> updateStructure();

    /// Nodes currently observed for visibility and managed-state changes.
    private final Set<Node> observedNodes = new HashSet<>();

    /// Creates the section containers and observes card properties for the lifetime of the card.
    JFXCardLayout(JFXCard card) {
        this.card = card;
        headline = label("card-headline", card.headlineProperty());
        subhead = label("card-subhead", card.subheadProperty());
        supportingText = label("card-supporting-text", card.supportingTextProperty());
        mediaFrame = new MediaFrame(card);
        getStyleClass().add("card-layout");
        header.getStyleClass().add("card-header");
        titles.getStyleClass().add("card-titles");
        body.getStyleClass().add("card-body");
        actions.getStyleClass().add("card-actions");
        sideRow.getStyleClass().add("card-side-row");
        header.setAlignment(Pos.CENTER_LEFT);
        sideRow.setAlignment(Pos.TOP_LEFT);
        sideRow.setFillHeight(false);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPrefWrapLength(0);
        titles.setMinWidth(0);
        textColumn.setMinWidth(0);
        HBox.setHgrow(titles, Priority.ALWAYS);
        HBox.setHgrow(textColumn, Priority.ALWAYS);
        for (Region section : List.of(this, header, titles, body, actions, textColumn, sideRow, mediaFrame)) {
            section.setPickOnBounds(false);
            section.setMinHeight(USE_PREF_SIZE);
        }
        card.headlineProperty().addListener(structureListener);
        card.subheadProperty().addListener(structureListener);
        card.supportingTextProperty().addListener(structureListener);
        card.headerGraphicProperty().addListener(structureListener);
        card.headerActionProperty().addListener(structureListener);
        card.contentProperty().addListener(structureListener);
        card.mediaProperty().addListener(structureListener);
        card.mediaPlacementProperty().addListener(structureListener);
        card.getActions().addListener(structureListener);
        card.mediaAspectRatioProperty().addListener(observable -> mediaFrame.requestLayout());
        card.sideMediaSizeProperty().addListener(observable -> mediaFrame.requestLayout());
        updateStructure();
    }

    /// Creates an input-transparent, start-aligned label that wraps instead of truncating text.
    private static Label label(String styleClass, StringProperty text) {
        Label label = new Label();
        label.getStyleClass().add(styleClass);
        label.textProperty().bind(text);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMinHeight(USE_PREF_SIZE);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMouseTransparent(true);
        return label;
    }

    /// Returns whether a caller-owned node participates in the layout.
    private static boolean present(@Nullable Node node) {
        return node != null && node.isVisible() && node.isManaged();
    }

    /// Returns whether a text slot supplies a label.
    private static boolean hasText(@Nullable String text) {
        return text != null && !text.isEmpty();
    }

    /// Updates visibility observation without retaining removed caller-owned nodes.
    private void observeNodes() {
        Set<Node> current = new HashSet<>(card.getActions());
        for (@Nullable Node node : new @Nullable Node[]{card.getHeaderGraphic(), card.getHeaderAction(), card.getContent(), card.getMedia()}) {
            if (node != null)
                current.add(node);
        }
        for (Node node : observedNodes) {
            if (!current.contains(node)) {
                node.visibleProperty().removeListener(structureListener);
                node.managedProperty().removeListener(structureListener);
            }
        }
        for (Node node : current) {
            if (!observedNodes.contains(node)) {
                node.visibleProperty().addListener(structureListener);
                node.managedProperty().addListener(structureListener);
            }
        }
        observedNodes.clear();
        observedNodes.addAll(current);
    }

    /// Updates a section only when its children differ, preserving focus in unchanged controls.
    private static void children(Pane pane, List<Node> nodes) {
        if (!pane.getChildren().equals(nodes))
            pane.getChildren().setAll(nodes);
    }

    /// Moves a section out of its previous layout container before assigning a new parent.
    private static void detach(Node node) {
        if (node.getParent() instanceof Pane parent)
            parent.getChildren().remove(node);
    }

    /// Reconciles optional slots, media position, and padding without reserving space for absent sections.
    private void updateStructure() {
        observeNodes();
        List<Node> titleNodes = new ArrayList<>();
        if (hasText(card.getHeadline()))
            titleNodes.add(headline);
        if (hasText(card.getSubhead()))
            titleNodes.add(subhead);
        children(titles, titleNodes);

        List<Node> headerNodes = new ArrayList<>();
        if (present(card.getHeaderGraphic()))
            headerNodes.add(card.getHeaderGraphic());
        headerNodes.add(titles);
        if (present(card.getHeaderAction()))
            headerNodes.add(card.getHeaderAction());
        children(header, headerNodes);
        boolean hasHeader = !titleNodes.isEmpty() || headerNodes.size() > 1;

        List<Node> bodyNodes = new ArrayList<>();
        if (hasText(card.getSupportingText()))
            bodyNodes.add(supportingText);
        if (present(card.getContent()))
            bodyNodes.add(card.getContent());
        children(body, bodyNodes);
        boolean hasBody = !bodyNodes.isEmpty();
        @Unmodifiable List<Node> actionNodes = card.getActions().stream().filter(JFXCardLayout::present).toList();
        children(actions, actionNodes);
        boolean hasActions = !actionNodes.isEmpty();
        boolean hasMedia = present(card.getMedia());
        mediaFrame.setMedia(hasMedia ? card.getMedia() : null);
        boolean side = hasMedia && isSideMedia();
        header.pseudoClassStateChanged(EMBEDDED, side);
        body.pseudoClassStateChanged(EMBEDDED, side);
        body.pseudoClassStateChanged(CONTINUATION, hasHeader
                && !(hasMedia && card.getMediaPlacement() == JFXCard.MediaPlacement.BELOW_HEADER));
        actions.pseudoClassStateChanged(CONTINUATION, hasBody || hasHeader && (!hasMedia || side
                || card.getMediaPlacement() != JFXCard.MediaPlacement.BELOW_HEADER));

        List<Node> sections = new ArrayList<>();
        if (side) {
            if (header.getParent() != textColumn)
                detach(header);
            if (body.getParent() != textColumn)
                detach(body);
            if (mediaFrame.getParent() != sideRow)
                detach(mediaFrame);
            List<Node> textNodes = new ArrayList<>();
            if (hasHeader)
                textNodes.add(header);
            if (hasBody)
                textNodes.add(body);
            children(textColumn, textNodes);
            children(sideRow, card.getMediaPlacement() == JFXCard.MediaPlacement.START
                    ? List.of(mediaFrame, textColumn) : List.of(textColumn, mediaFrame));
            sections.add(sideRow);
        } else {
            if (header.getParent() != this)
                detach(header);
            if (body.getParent() != this)
                detach(body);
            if (mediaFrame.getParent() != this)
                detach(mediaFrame);
            if (hasMedia && card.getMediaPlacement() == JFXCard.MediaPlacement.TOP)
                sections.add(mediaFrame);
            if (hasHeader)
                sections.add(header);
            if (hasMedia && card.getMediaPlacement() == JFXCard.MediaPlacement.BELOW_HEADER)
                sections.add(mediaFrame);
            if (hasBody)
                sections.add(body);
        }
        if (hasActions)
            sections.add(actions);
        children(this, sections);
        mediaFrame.requestLayout();
    }

    /// Returns whether media is positioned beside the text rather than spanning the card.
    private boolean isSideMedia() {
        return card.getMediaPlacement() == JFXCard.MediaPlacement.START
                || card.getMediaPlacement() == JFXCard.MediaPlacement.END;
    }

    /// Provides width-dependent media sizing and clips overflow without changing caller-owned clips.
    @NotNullByDefault
    private static final class MediaFrame extends Region {
        /// Supplies the placement, aspect ratio, and thumbnail size.
        private final JFXCard card;

        /// Applies center-crop transforms around nonresizable media.
        private final Group group = new Group();

        /// Prevents cropped media from painting over adjacent sections.
        private final Rectangle clip = new Rectangle();

        /// Creates an empty frame whose children are positioned explicitly.
        private MediaFrame(JFXCard card) {
            this.card = card;
            getStyleClass().add("card-media");
            group.setAutoSizeChildren(false);
            group.setManaged(false);
            group.layoutBoundsProperty().addListener(observable -> requestLayout());
            setClip(clip);
            getChildren().add(group);
            setMinWidth(0);
        }

        /// Replaces the media node without modifying its own input, clip, or transform properties.
        private void setMedia(@Nullable Node node) {
            if (node == null) {
                group.getChildren().clear();
            } else if (group.getChildren().isEmpty() || group.getChildren().get(0) != node) {
                group.getChildren().setAll(node);
            }
        }

        /// Returns whether this frame uses a square side thumbnail.
        private boolean isSide() {
            return card.getMediaPlacement() == JFXCard.MediaPlacement.START
                    || card.getMediaPlacement() == JFXCard.MediaPlacement.END;
        }

        /// Returns a finite, positive width-to-height ratio.
        private double ratio() {
            double value = card.getMediaAspectRatio();
            return isSide() ? 1 : Double.isFinite(value) && value > 0 ? value : 16.0 / 9.0;
        }

        /// Returns a finite, positive preferred thumbnail width.
        private double sideSize() {
            double value = card.getSideMediaSize();
            return Double.isFinite(value) && value > 0 ? value : 80;
        }

        /// Computes media height from its allocated width.
        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        /// Uses thumbnail size or the natural media width when a parent has no width constraint.
        @Override
        protected double computePrefWidth(double height) {
            if (isSide())
                return sideSize();
            if (group.getChildren().isEmpty())
                return 0;
            Node media = group.getChildren().get(0);
            return media.isResizable() ? media.prefWidth(-1) : group.getLayoutBounds().getWidth();
        }

        /// Preserves the configured aspect ratio at the actual available width.
        @Override
        protected double computePrefHeight(double width) {
            return Math.max(0, width < 0 ? prefWidth(-1) : width) / ratio();
        }

        /// Limits side thumbnails to their preferred width while allowing full-width media to grow.
        @Override
        protected double computeMaxWidth(double height) {
            return isSide() ? sideSize() : Double.MAX_VALUE;
        }

        /// Keeps surplus card height from stretching the media's aspect ratio.
        @Override
        protected double computeMaxHeight(double width) {
            return prefHeight(width);
        }

        /// Fills resizable media or center-crops nonresizable media through the wrapper transform.
        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();
            clip.setWidth(width);
            clip.setHeight(height);
            if (group.getChildren().isEmpty())
                return;
            Node media = group.getChildren().get(0);
            group.setScaleX(1);
            group.setScaleY(1);
            if (media.isResizable()) {
                media.resizeRelocate(0, 0, width, height);
                group.relocate(0, 0);
            } else {
                Bounds bounds = group.getLayoutBounds();
                double scale = bounds.getWidth() > 0 && bounds.getHeight() > 0
                        ? Math.max(width / bounds.getWidth(), height / bounds.getHeight()) : 1;
                group.setScaleX(scale);
                group.setScaleY(scale);
                group.relocate((width - bounds.getWidth()) / 2, (height - bounds.getHeight()) / 2);
            }
        }
    }
}
