/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleablePropertyFactory;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;

import java.util.List;

public class TwoLineListItem extends StackPane {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    private final StyleableObjectProperty<Font> titleFont = new SimpleStyleableObjectProperty<>(StyleableProperties.TITLE_FONT, this, "title-font", Font.font(15));
    private final StyleableObjectProperty<Font> subtitleFont = new SimpleStyleableObjectProperty<>(StyleableProperties.SUBTITLE_FONT, this, "subtitle-font", Font.getDefault());

    private final StyleableObjectProperty<Paint> titleFill = new SimpleStyleableObjectProperty<>(StyleableProperties.TITLE_FILL, this, "title-fill", Color.BLACK);
    private final StyleableObjectProperty<Paint> subtitleFill = new SimpleStyleableObjectProperty<>(StyleableProperties.SUBTITLE_FILL, this, "subtitle-fill", Color.GRAY);

    public TwoLineListItem(String titleString, String subtitleString) {
        this();

        title.set(titleString);
        subtitle.set(subtitleString);
    }

    public TwoLineListItem() {
        Label lblTitle = new Label();
        lblTitle.textFillProperty().bind(titleFill);
        lblTitle.fontProperty().bind(titleFont);
        lblTitle.textProperty().bind(title);

        Label lblSubtitle = new Label();
        lblSubtitle.textFillProperty().bind(subtitleFill);
        lblSubtitle.fontProperty().bind(subtitleFont);
        lblSubtitle.textProperty().bind(subtitle);

        VBox vbox = new VBox();
        vbox.getChildren().setAll(lblTitle, lblSubtitle);
        getChildren().setAll(vbox);

        getStyleClass().add(DEFAULT_STYLE_CLASS);
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

    public Font getTitleFont() {
        return titleFont.get();
    }

    public StyleableObjectProperty<Font> titleFontProperty() {
        return titleFont;
    }

    public void setTitleFont(Font titleFont) {
        this.titleFont.set(titleFont);
    }

    public Font getSubtitleFont() {
        return subtitleFont.get();
    }

    public StyleableObjectProperty<Font> subtitleFontProperty() {
        return subtitleFont;
    }

    public void setSubtitleFont(Font subtitleFont) {
        this.subtitleFont.set(subtitleFont);
    }

    public Paint getTitleFill() {
        return titleFill.get();
    }

    public StyleableObjectProperty<Paint> titleFillProperty() {
        return titleFill;
    }

    public void setTitleFill(Paint titleFill) {
        this.titleFill.set(titleFill);
    }

    public Paint getSubtitleFill() {
        return subtitleFill.get();
    }

    public StyleableObjectProperty<Paint> subtitleFillProperty() {
        return subtitleFill;
    }

    public void setSubtitleFill(Paint subtitleFill) {
        this.subtitleFill.set(subtitleFill);
    }

    @Override
    public String toString() {
        return getTitle();
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.FACTORY.getCssMetaData();
    }

    private static class StyleableProperties {
        private static final StyleablePropertyFactory<TwoLineListItem> FACTORY = new StyleablePropertyFactory<>(StackPane.getClassCssMetaData());

        private static final CssMetaData<TwoLineListItem, Font> TITLE_FONT = FACTORY.createFontCssMetaData("-jfx-title-font", s -> s.titleFont, Font.font(15));
        private static final CssMetaData<TwoLineListItem, Font> SUBTITLE_FONT = FACTORY.createFontCssMetaData("-jfx-subtitle-font", s -> s.subtitleFont);
        private static final CssMetaData<TwoLineListItem, Paint> TITLE_FILL = FACTORY.createPaintCssMetaData("-jfx-title-fill", s -> s.titleFill);
        private static final CssMetaData<TwoLineListItem, Paint> SUBTITLE_FILL = FACTORY.createPaintCssMetaData("-jfx-subtitle-fill", s -> s.subtitleFill, Color.GREY);
    }
}
