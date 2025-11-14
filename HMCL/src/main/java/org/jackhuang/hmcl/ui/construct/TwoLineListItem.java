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
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.AggregatedObservableList;

import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class TwoLineListItem extends VBox {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";
    private static final int UNLIMITED_TAGS = -1;

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final ReadOnlyListWrapper<Tag> allTags = new ReadOnlyListWrapper<>(this, "allTags", FXCollections.observableArrayList());
    private final ObservableList<Label> shownTags = FXCollections.observableArrayList();
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    private final IntegerProperty maxShownTags = new SimpleIntegerProperty(UNLIMITED_TAGS);

    private final AggregatedObservableList<Node> firstLineChildren;

    private final Label overflowLabel = createTagLabel("", "tag-overflow");

    public TwoLineListItem(String titleString, String subtitleString) {
        this();

        title.set(titleString);
        subtitle.set(subtitleString);
    }

    public TwoLineListItem() {

        HBox firstLine = new HBox();
        firstLine.getStyleClass().add("first-line");

        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("title");
        lblTitle.textProperty().bind(title);
        FXUtils.showTooltipWhenTruncated(lblTitle);

        firstLineChildren = new AggregatedObservableList<>();
        firstLineChildren.appendList(FXCollections.singletonObservableList(lblTitle));
        firstLineChildren.appendList(shownTags);
        allTags.addListener((ListChangeListener<? super Tag>) this::updateShownTags);
        maxShownTags.addListener(observable -> reBuiltShownTags());

        Bindings.bindContent(firstLine.getChildren(), firstLineChildren.getAggregatedList());

        Label lblSubtitle = new Label();
        lblSubtitle.getStyleClass().add("subtitle");
        lblSubtitle.textProperty().bind(subtitle);
        FXUtils.showTooltipWhenTruncated(lblSubtitle);

        HBox secondLine = new HBox();
        secondLine.getChildren().setAll(lblSubtitle);

        getChildren().setAll(firstLine, secondLine);

        FXUtils.onChangeAndOperate(subtitle, subtitleString -> {
            if (subtitleString == null) getChildren().setAll(firstLine);
            else getChildren().setAll(firstLine, secondLine);
        });

        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private void updateShownTags(ListChangeListener.Change<? extends Tag> c) {
        if (maxShownTags.get() == UNLIMITED_TAGS || shownTags.size() < maxShownTags.get()) {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Tag tag : c.getAddedSubList()) {
                        if (maxShownTags.get() != UNLIMITED_TAGS && shownTags.size() > maxShownTags.get()) {
                            break;
                        }
                        shownTags.add(createTagLabel(tag.text, tag.styleClass));
                    }
                }
            }
        }
        setOverflowTag();
    }

    private void reBuiltShownTags() {
        shownTags.clear();
        for (Tag allTag : allTags) {
            if (maxShownTags.get() != UNLIMITED_TAGS && shownTags.size() > maxShownTags.get()) {
                break;
            }
            shownTags.add(createTagLabel(allTag.text, allTag.styleClass));
        }
        setOverflowTag();
    }

    private void setOverflowTag() {
        if (maxShownTags.get() != UNLIMITED_TAGS && allTags.size() > maxShownTags.get()) {
            FXUtils.installFastTooltip(overflowLabel, allTags.stream().skip(maxShownTags.get())
                    .map(Tag::text).collect(Collectors.joining("\n")));
            overflowLabel.setText(i18n("tag.overflow", (allTags.size() - maxShownTags.get())));
            if (!shownTags.contains(overflowLabel)) {
                shownTags.add(overflowLabel);
            }
        }
    }

    private static Label createTagLabel(String tag, String StyleClass) {
        Label tagLabel = FXUtils.newSafeTruncatedLabel(tag);
        HBox.setMargin(tagLabel, new Insets(0, 6, 0, 0));
        tagLabel.getStyleClass().add(StyleClass);
        return tagLabel;
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    public void addTag(String tag) {
        allTags.add(new Tag(tag, "tag"));
    }

    public void addTagWarning(String tag) {
        allTags.add(new Tag(tag, "tag-warning"));
    }

    public ReadOnlyListProperty<Tag> getTags() {
        return allTags.getReadOnlyProperty();
    }

    public void setMaxShownTags(int maxShownTags) {
        if (maxShownTags >= 0) {
            this.maxShownTags.set(maxShownTags);
        }
    }

    public IntegerProperty getMaxShownTags() {
        return maxShownTags;
    }

    public void clearTags() {
        allTags.clear();
        shownTags.clear();
    }

    @Override
    public String toString() {
        return getTitle();
    }

    public record Tag(String text, String styleClass) {
    }
}
