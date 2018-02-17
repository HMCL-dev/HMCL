package org.jackhuang.hmcl.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.util.Pair;

public class TwoLineListItem extends StackPane {
    private final Label lblTitle = new Label();
    private final Label lblSubtitle = new Label();
    private final String title;
    private final String subtitle;

    public TwoLineListItem(Pair<String, String> pair) {
        this(pair.getKey(), pair.getValue());
    }

    public TwoLineListItem(String title, String subtitle) {
        lblTitle.setStyle("-fx-font-size: 15;");
        lblSubtitle.setStyle("-fx-font-size: 10; -fx-text-fill: gray;");

        this.title = title;
        this.subtitle = subtitle;

        lblTitle.setText(title);
        lblSubtitle.setText(subtitle);

        VBox vbox = new VBox();
        vbox.getChildren().setAll(lblTitle, lblSubtitle);
        getChildren().setAll(vbox);
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
