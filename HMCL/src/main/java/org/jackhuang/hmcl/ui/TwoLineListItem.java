package org.jackhuang.hmcl.ui;

import javafx.beans.property.*;
import javafx.css.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TwoLineListItem extends StackPane {

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
        return StyleableProperties.STYLEABLES;
    }

    private static class StyleableProperties {

        private static final FontCssMetaData<TwoLineListItem> TITLE_FONT = new FontCssMetaData<TwoLineListItem>("-jfx-title-font", Font.font(15)) {
            @Override
            public boolean isSettable(TwoLineListItem control) {
                return control.title == null || !control.title.isBound();
            }

            @Override
            public StyleableProperty<Font> getStyleableProperty(TwoLineListItem control) {
                return control.titleFontProperty();
            }
        };

        private static final FontCssMetaData<TwoLineListItem> SUBTITLE_FONT = new FontCssMetaData<TwoLineListItem>("-jfx-subtitle-font", Font.getDefault()) {
            @Override
            public boolean isSettable(TwoLineListItem control) {
                return control.subtitle == null || !control.subtitle.isBound();
            }

            @Override
            public StyleableProperty<Font> getStyleableProperty(TwoLineListItem control) {
                return control.subtitleFontProperty();
            }
        };

        private static final CssMetaData<TwoLineListItem, Paint> TITLE_FILL = new CssMetaData<TwoLineListItem, Paint>("-jfx-title-fill", StyleConverter.getPaintConverter(), Color.BLACK) {
            @Override
            public boolean isSettable(TwoLineListItem control) {
                return control.titleFill == null || !control.titleFill.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(TwoLineListItem control) {
                return control.titleFillProperty();
            }
        };

        private static final CssMetaData<TwoLineListItem, Paint> SUBTITLE_FILL = new CssMetaData<TwoLineListItem, Paint>("-jfx-subtitle-fill", StyleConverter.getPaintConverter(), Color.GRAY) {
            @Override
            public boolean isSettable(TwoLineListItem control) {
                return control.subtitleFill == null || !control.subtitleFill.isBound();
            }

            @Override
            public StyleableProperty<Paint> getStyleableProperty(TwoLineListItem control) {
                return control.subtitleFillProperty();
            }
        };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        private StyleableProperties() {
        }

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables = new ArrayList<>(Node.getClassCssMetaData());
            Collections.addAll(styleables, TITLE_FONT);
            Collections.addAll(styleables, SUBTITLE_FONT);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
