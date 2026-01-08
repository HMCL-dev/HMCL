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

import javafx.beans.InvalidationListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A horizontal bar that displays multiple text tags.
 * When there is not enough space, it collapses the rightmost tags into a "+N" indicator
 * with a tooltip showing the collapsed tag contents.
 */
public class TagsBar extends Pane {
    private static final String DEFAULT_STYLE_CLASS = "tags-bar";
    private static final double TAG_SPACING = 8.0;

    private final ObservableList<Tag> tags = FXCollections.observableArrayList();
    private final List<Label> tagLabels = new ArrayList<>();
    private final Text collapsedIndicator = new Text();
    private final Tooltip collapsedTooltip = new Tooltip();

    private int visibleTagCount = 0;

    public TagsBar() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        collapsedIndicator.getStyleClass().add("collapsed-indicator");
        collapsedIndicator.setManaged(false);
        collapsedIndicator.setVisible(false);
        getChildren().add(collapsedIndicator);

        // Install tooltip on collapsed indicator
        FXUtils.installFastTooltip(collapsedIndicator, collapsedTooltip);

        tags.addListener((ListChangeListener<Tag>) c -> {
            rebuildTagLabels();
            requestLayout();
        });

        tags.addListener((InvalidationListener) obs -> requestLayout());
    }

    private void rebuildTagLabels() {
        // Remove old labels
        getChildren().removeAll(tagLabels);
        tagLabels.clear();

        // Create new labels
        for (Tag tag : tags) {
            Label label = new Label();
            label.setText(tag.text());
            label.setManaged(false);
            label.getStyleClass().add(tag.warning() ? "tag-warning" : "tag");
            tagLabels.add(label);
        }

        // Add new labels
        getChildren().addAll(tagLabels);

        // Ensure collapsed indicator is on top
        collapsedIndicator.toFront();
    }

    public ObservableList<Tag> getTags() {
        return tags;
    }

    public void addTag(String text) {
        tags.add(new Tag(text, false));
    }

    public void addTagWarning(String text) {
        tags.add(new Tag(text, true));
    }


    @Override
    protected double computeMinWidth(double height) {
        // Minimum width is the width of the collapsed indicator "+N" if there are tags
        if (tags.isEmpty()) {
            return 0;
        }
        // Estimate the width of "+N" where N is the total tag count
        collapsedIndicator.setText("+" + tags.size());
        return snapSizeX(collapsedIndicator.prefWidth(-1));
    }

    @Override
    protected double computePrefWidth(double height) {
        if (tags.isEmpty()) {
            return 0;
        }
        double width = 0;
        for (Label label : tagLabels) {
            width += label.prefWidth(-1);
        }
        // Add spacing between tags
        width += TAG_SPACING * (tagLabels.size() - 1);
        return snapSizeX(width);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computePrefHeight(double width) {
        double maxHeight = 0;
        for (Label label : tagLabels) {
            maxHeight = Math.max(maxHeight, label.prefHeight(-1));
        }
        if (maxHeight == 0) {
            maxHeight = collapsedIndicator.prefHeight(-1);
        }
        return snapSizeY(maxHeight);
    }

    @Override
    protected void layoutChildren() {
        if (tags.isEmpty()) {
            collapsedIndicator.setVisible(false);
            return;
        }

        double availableWidth = getWidth();
        double height = getHeight();

        // Calculate widths of all tags
        double[] tagWidths = new double[tagLabels.size()];
        for (int i = 0; i < tagLabels.size(); i++) {
            tagWidths[i] = tagLabels.get(i).prefWidth(-1);
        }

        // Calculate how many tags can fit
        visibleTagCount = 0;
        double currentWidth = 0;

        // First, calculate the collapsed indicator width for the worst case
        collapsedIndicator.setText("+" + tags.size());
        double collapsedWidth = collapsedIndicator.prefWidth(-1);

        // Try to fit as many tags as possible
        for (int i = 0; i < tagLabels.size(); i++) {
            double tagWidth = tagWidths[i];
            double spacing = (visibleTagCount > 0) ? TAG_SPACING : 0;
            // Calculate how many tags would be hidden if we stop here (including current tag)
            int hiddenCount = tagLabels.size() - i;

            if (i < tagLabels.size() - 1) {
                // Not the last tag - need to check if we can fit this tag + collapsed indicator
                collapsedIndicator.setText("+" + hiddenCount);
                double neededCollapsedWidth = collapsedIndicator.prefWidth(-1) + TAG_SPACING;

                if (currentWidth + spacing + tagWidth + neededCollapsedWidth <= availableWidth) {
                    currentWidth += spacing + tagWidth;
                    visibleTagCount++;
                } else {
                    // Can't fit this tag plus the collapsed indicator
                    break;
                }
            } else {
                // This is the last tag - no collapsed indicator needed if it fits
                if (currentWidth + spacing + tagWidth <= availableWidth) {
                    currentWidth += spacing + tagWidth;
                    visibleTagCount++;
                }
            }
        }

        // Special case: if no tags can be shown but we have space for the indicator, show only indicator
        boolean showingCollapsedIndicator;
        if (visibleTagCount == 0 && !tags.isEmpty()) {
            collapsedIndicator.setText("+" + tags.size());
            showingCollapsedIndicator = collapsedWidth <= availableWidth;
        } else {
            showingCollapsedIndicator = visibleTagCount < tagLabels.size();
        }

        // Layout visible tags
        double x = 0;
        for (int i = 0; i < tagLabels.size(); i++) {
            Label label = tagLabels.get(i);
            if (i < visibleTagCount) {
                label.setVisible(true);
                double labelWidth = tagWidths[i];
                layoutInArea(label, x, 0, labelWidth, height, 0, HPos.LEFT, VPos.CENTER);
                x += labelWidth + TAG_SPACING;
            } else {
                label.setVisible(false);
            }
        }

        // Layout collapsed indicator
        if (showingCollapsedIndicator) {
            int hiddenCount = tags.size() - visibleTagCount;
            collapsedIndicator.setText("+" + hiddenCount);
            collapsedIndicator.setVisible(true);
            double indicatorWidth = collapsedIndicator.prefWidth(-1);
            layoutInArea(collapsedIndicator, x, 0, indicatorWidth, height, 0, HPos.LEFT, VPos.CENTER);

            // Update tooltip with hidden tags
            updateCollapsedTooltip();
        } else {
            collapsedIndicator.setVisible(false);
        }
    }

    private void updateCollapsedTooltip() {
        StringBuilder sb = new StringBuilder();
        for (int i = visibleTagCount; i < tags.size(); i++) {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(tags.get(i).text());
        }
        collapsedTooltip.setText(sb.toString());
    }


    /**
     * A tag with its text content and warning flag.
     */
    public record Tag(String text, boolean warning) {
    }
}

