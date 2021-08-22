/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.main;

import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.ui.FXUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class SponsorPage extends StackPane {
    public SponsorPage() {
        VBox content = new VBox();
        content.setFillWidth(true);

        {
            StackPane sponsorPane = new StackPane();
            sponsorPane.setCursor(Cursor.HAND);
            sponsorPane.setOnMouseClicked(e -> onSponsor());

            GridPane gridPane = new GridPane();

            ColumnConstraints col = new ColumnConstraints();
            col.setHgrow(Priority.SOMETIMES);
            col.setMaxWidth(Double.POSITIVE_INFINITY);

            gridPane.getColumnConstraints().setAll(col);

            RowConstraints row = new RowConstraints();
            row.setMinHeight(Double.NEGATIVE_INFINITY);
            row.setValignment(VPos.TOP);
            row.setVgrow(Priority.SOMETIMES);
            gridPane.getRowConstraints().setAll(row);

            {
                Label label = new Label(i18n("sponsor.hmcl"));
                label.setWrapText(true);
                label.setTextAlignment(TextAlignment.JUSTIFY);
                GridPane.setRowIndex(label, 0);
                GridPane.setColumnIndex(label, 0);
                gridPane.getChildren().add(label);
            }

            sponsorPane.getChildren().setAll(gridPane);
            content.getChildren().add(sponsorPane);
        }

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setContent(content);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);
    }

    private void onSponsor() {
        FXUtils.openLink("https://hmcl.huangyuhui.net/api/redirect/sponsor");
    }
}
