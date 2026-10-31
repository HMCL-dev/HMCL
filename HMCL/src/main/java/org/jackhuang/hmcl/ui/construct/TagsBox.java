// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;

/// Renders a tag list within the space left by the other nodes in a title row.
/// Each box creates its own labels, allowing tag values to be shared across components.
@NotNullByDefault
final class TagsBox extends HBox {

    /// Selects the warning colors from the application theme.
    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");

    /// Creates a mouse-transparent view of the given list, with overflowing content clipped.
    /// Empty lists occupy no space. The list must contain no null elements, and changes
    /// must be made on the JavaFX application thread once attached to a showing scene.
    TagsBox(ObservableList<Tag> tags) {
        super(8);
        getStyleClass().add("tags");
        setAlignment(Pos.CENTER_LEFT);
        setMouseTransparent(true);

        // Start at zero during HBox allocation so tags do not compete with the title for space.
        setPrefWidth(0);
        setMinWidth(0);
        HBox.setHgrow(this, Priority.SOMETIMES);
        FXUtils.setOverflowHidden(this);

        var isNotEmpty = Bindings.isNotEmpty(tags);
        managedProperty().bind(isNotEmpty);
        visibleProperty().bind(isNotEmpty);

        updateTags(tags);
        tags.addListener((ListChangeListener<Tag>) change -> updateTags(change.getList()));
    }

    /// Replaces the rendered labels with a view of the current tag values in list order.
    private void updateTags(List<? extends Tag> tags) {
        getChildren().setAll(tags.stream().map(TagsBox::createLabel).toList());
    }

    /// Creates a label that retains its preferred width and uses the tag type's appearance.
    private static Label createLabel(Tag tag) {
        var label = new Label(tag.text());
        label.getStyleClass().add("tag");
        label.setMinWidth(USE_PREF_SIZE);
        switch (tag.type()) {
            case NORMAL -> { }
            case WARNING -> label.pseudoClassStateChanged(WARNING_PSEUDO_CLASS, true);
        }
        return label;
    }

    /// Returns the computed preferred width without applying the zero allocation-width override.
    ///
    /// @param height the available height, or -1 if unspecified
    /// @return the natural preferred width of the tags and their container
    double computeNaturalPrefWidth(double height) {
        return super.computePrefWidth(height);
    }
}
