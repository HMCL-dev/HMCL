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

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class TwoLineListItem extends VBox {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";

    private final HBox firstLine;
    private HBox secondLine;

    private final Label lblTitle;
    private Label lblSubtitle;

    public TwoLineListItem() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setMouseTransparent(true);

        lblTitle = new Label();
        lblTitle.getStyleClass().add("title");

        this.firstLine = new HBox(lblTitle);
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

    public Label getTitleLabel() {
        return lblTitle;
    }

    public Label getSubtitleLabel() {
        initSecondLine();
        return lblSubtitle;
    }

    private ObservableList<Label> tags;

    public ObservableList<Label> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();

            var tagsBox = new HBox(8);
            tagsBox.getStyleClass().add("tags");
            tagsBox.setAlignment(Pos.CENTER_LEFT);
            Bindings.bindContent(tagsBox.getChildren(), tags);

            var scrollPane = new ScrollPane(tagsBox);
            HBox.setHgrow(scrollPane, Priority.ALWAYS);
            lblTitle.setMinWidth(Label.USE_PREF_SIZE);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            FXUtils.onChangeAndOperate(tagsBox.heightProperty(), height -> FXUtils.setLimitHeight(scrollPane, height.doubleValue()));
            firstLine.getChildren().setAll(lblTitle, scrollPane);

            tags.addListener((ListChangeListener<? super Label>) change -> {
                Platform.runLater(scrollPane::requestLayout);
            });
        }
        return tags;
    }

    public void addTag(String tag, PseudoClass pseudoClass) {
        var tagLabel = new Label(tag);
        tagLabel.getStyleClass().add("tag");
        if (pseudoClass != null)
            tagLabel.pseudoClassStateChanged(pseudoClass, true);
        getTags().add(tagLabel);
    }

    public void addTag(String tag) {
        addTag(tag, null);
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
