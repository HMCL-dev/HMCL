// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/// Displays a title with optional tags and a subtitle.
public class TwoLineListItem extends VBox {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";

    private final HBox firstLine;
    private HBox secondLine;

    private final Label lblTitle;
    private Label lblSubtitle;

    /// Creates an empty item whose children do not receive mouse events.
    public TwoLineListItem() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMouseTransparent(true);

        lblTitle = new Label();
        lblTitle.getStyleClass().add("title");
        lblTitle.setTextOverrun(OverrunStyle.ELLIPSIS);

        this.firstLine = new HBox(lblTitle) {
            /// Includes the tags' natural width when requesting space from the parent.
            @Override
            protected double computePrefWidth(double height) {
                double width = super.computePrefWidth(height);
                for (Node child : getManagedChildren()) {
                    if (child instanceof TagsBox tagsBox) {
                        width += snapSizeX(tagsBox.computeNaturalPrefWidth(height));
                    }
                }
                return width;
            }
        };
        firstLine.getStyleClass().add("first-line");
        firstLine.setAlignment(Pos.CENTER_LEFT);

        this.getChildren().setAll(firstLine);
    }

    public TwoLineListItem(String titleString, String subtitleString) {
        this();

        setTitle(titleString);
        setSubtitle(subtitleString);
    }

    private void initSecondLine() {
        if (secondLine == null) {
            lblSubtitle = new Label();
            lblSubtitle.getStyleClass().add("subtitle");

            secondLine = new HBox(lblSubtitle);
        }
    }

    private final StringProperty title = new StringPropertyBase() {
        @Override
        public Object getBean() {
            return TwoLineListItem.this;
        }

        @Override
        public String getName() {
            return "title";
        }

        @Override
        protected void invalidated() {
            lblTitle.setText(get());
        }
    };

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    private StringProperty subtitle;

    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new StringPropertyBase() {
                @Override
                public Object getBean() {
                    return TwoLineListItem.this;
                }

                @Override
                public String getName() {
                    return "subtitle";
                }

                @Override
                protected void invalidated() {
                    String subtitle = get();

                    if (subtitle != null) {
                        initSecondLine();
                        lblSubtitle.setText(subtitle);

                        if (getChildren().size() == 1)
                            getChildren().add(secondLine);
                    } else if (secondLine != null) {
                        lblSubtitle.setText(null);
                        if (getChildren().size() > 1)
                            getChildren().setAll(firstLine);
                    }
                }
            };
        }
        return subtitle;
    }

    public String getSubtitle() {
        return subtitle != null ? subtitleProperty().get() : null;
    }

    public void setSubtitle(String subtitle) {
        if (this.subtitle == null && subtitle == null)
            return;

        subtitleProperty().set(subtitle);
    }

    public HBox getFirstLine() {
        return firstLine;
    }

    public Label getTitleLabel() {
        return lblTitle;
    }

    public Label getSubtitleLabel() {
        initSecondLine();
        return lblSubtitle;
    }

    /// Stores the mutable tag list once tag support has been initialized.
    private @Nullable ObservableList<Tag> tags;

    /// Returns the mutable list of tags displayed after the title.
    /// Tag values may be shared or repeated in the list. Replace a value to change its
    /// text or type. Changes must be made on the JavaFX application thread once this
    /// component is attached to a showing scene.
    ///
    /// @return the live list of tag values; elements must be non-null
    public ObservableList<Tag> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();

            HBox.setHgrow(lblTitle, Priority.ALWAYS);
            lblTitle.setMinWidth(0);
            firstLine.getChildren().setAll(lblTitle, new TagsBox(tags));
        }
        return tags;
    }

    /// Appends a tag with the given text and type.
    ///
    /// @param tag the tag text, or null for no text
    /// @param type the tag's semantic type
    /// @throws NullPointerException if type is null
    public void addTag(@Nullable String tag, Tag.Type type) {
        getTags().add(new Tag(tag, type));
    }

    /// Appends a tag with the default appearance and the given text.
    /// Null creates a tag with no text.
    public void addTag(@Nullable String tag) {
        addTag(tag, Tag.Type.NORMAL);
    }

    /// Appends tags in the collection's iteration order, retaining duplicate text.
    /// Null elements create tags with no text.
    public void addTags(Collection<@Nullable String> tags) {
        getTags().addAll(tags.stream().map(Tag::new).toList());
    }

    /// Appends each distinct text not already present in the tag list, in iteration order.
    /// Text is compared using [String#equals(Object)] regardless of type.
    /// Null elements create tags with no text.
    public void addTagsIfNotExist(Collection<@Nullable String> tags) {
        var current = getTags().stream().map(Tag::text).collect(Collectors.toSet());
        var target = new LinkedHashSet<>(tags);
        target.removeAll(current);
        addTags(target);
    }

    /// Appends a tag with the warning appearance and the given text.
    /// Null creates a tag with no text.
    public void addTagWarning(@Nullable String tag) {
        getTags().add(Tag.warning(tag));
    }

    @Override
    public String toString() {
        return "TwoLineListItem[title=%s, subtitle=%s, tags=%s]".formatted(getTitle(), getSubtitle(), tags);
    }
}
