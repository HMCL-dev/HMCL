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

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
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

    private static Label createTag(String tag, PseudoClass pseudoClass) {
        var tagLabel = new Label(tag);
        tagLabel.getStyleClass().add("tag");
        tagLabel.setMinWidth(Label.USE_PREF_SIZE);
        if (pseudoClass != null)
            tagLabel.pseudoClassStateChanged(pseudoClass, true);
        return tagLabel;
    }

    /// Stores the mutable tag list once tag support has been initialized.
    private @Nullable ObservableList<Label> tags;

    /// Returns the mutable list of tags displayed after the title.
    public ObservableList<Label> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();

            var tagsBox = new TagsBox();
            tagsBox.getStyleClass().add("tags");
            tagsBox.setAlignment(Pos.CENTER_LEFT);

            HBox.setHgrow(tagsBox, Priority.SOMETIMES);

            Bindings.bindContent(tagsBox.getChildren(), tags);
            var isNotEmpty = Bindings.isNotEmpty(tags);
            tagsBox.managedProperty().bind(isNotEmpty);
            tagsBox.visibleProperty().bind(isNotEmpty);

            FXUtils.setOverflowHidden(tagsBox);

            HBox.setHgrow(lblTitle, Priority.ALWAYS);
            lblTitle.setMinWidth(0);
            firstLine.getChildren().setAll(lblTitle, tagsBox);
        }
        return tags;
    }

    /// Reports its natural width separately from the width used to allocate space within the title row.
    @NotNullByDefault
    private static final class TagsBox extends HBox {

        /// Creates a tag container with zero minimum and preferred allocation widths.
        private TagsBox() {
            super(8);
            // Start at zero during HBox allocation so tags do not compete with the title for space.
            setPrefWidth(0);
            setMinWidth(0);
        }

        /// Returns the computed preferred width without applying the zero preferred-width override.
        ///
        /// @param height the available height, or -1 if unspecified
        /// @return the natural preferred width of the tags and their container
        private double computeNaturalPrefWidth(double height) {
            return super.computePrefWidth(height);
        }
    }

    public void addTag(String tag, PseudoClass pseudoClass) {
        getTags().add(createTag(tag, pseudoClass));
    }

    public void addTag(String tag) {
        addTag(tag, null);
    }

    public void addTags(Collection<String> tags) {
        getTags().addAll(tags.stream().map(tag -> createTag(tag, null)).toList());
    }

    public void addTagsIfNotExist(Collection<String> tags) {
        var current = getTags().stream().map(Label::getText).collect(Collectors.toSet());
        var target = new LinkedHashSet<>(tags);
        target.removeAll(current);
        addTags(target);
    }

    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");

    public void addTagWarning(String tag) {
        addTag(tag, WARNING_PSEUDO_CLASS);
    }

    @Override
    public String toString() {
        return "TwoLineListItem[title=%s, subtitle=%s, tags=%s]".formatted(getTitle(), getSubtitle(), tags);
    }
}
