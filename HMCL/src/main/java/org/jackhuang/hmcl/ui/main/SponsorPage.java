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

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.jfoenix.controls.JFXListCell;
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Holder;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class SponsorPage extends StackPane {
    private final JFXListView<Sponsor> listView;

    public SponsorPage() {
        VBox content = new VBox();
        content.setPadding(new Insets(10));
        content.setSpacing(10);
        content.setFillWidth(true);

        {
            StackPane sponsorPane = new StackPane();
            sponsorPane.getStyleClass().add("card");
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

        {
            StackPane pane = new StackPane();
            pane.getStyleClass().add("card");
            listView = new JFXListView<>();
            Holder<Object> lastCell = new Holder<>();
            listView.setCellFactory((listView) -> new JFXListCell<Sponsor>() {
                @Override
                public void updateItem(Sponsor item, boolean empty) {
                    super.updateItem(item, empty);

                    // https://mail.openjdk.org/pipermail/openjfx-dev/2022-July/034764.html
                    if (this == lastCell.value && !isVisible())
                        return;
                    lastCell.value = this;

                    if (!empty) {
                        setText(item.getName());
                        setGraphic(null);
                    }
                }
            });
            VBox.setVgrow(pane, Priority.ALWAYS);
            pane.getChildren().setAll(listView);
            content.getChildren().add(pane);
        }

        loadSponsorList();

        getChildren().setAll(content);
    }

    private void onSponsor() {
        FXUtils.openLink("https://hmcl.huangyuhui.net/api/redirect/sponsor");
    }

    private void loadSponsorList() {
        Task.<List<Sponsor>>supplyAsync(() -> HttpRequest.GET("https://hmcl.huangyuhui.net/api/sponsor").getJson(new TypeToken<List<Sponsor>>() {
        }.getType())).thenAcceptAsync(Schedulers.javafx(), sponsors -> {
            listView.getItems().setAll(sponsors);
        }).start();
    }

    private static class Sponsor {
        @SerializedName("name")
        private final String name;

        @SerializedName("create_time")
        private final Date createTime;

        @SerializedName("money")
        private final BigDecimal money;

        @SerializedName("contact")
        private final String contact;

        @SerializedName("afdian_id")
        private final String afdianId;

        public Sponsor() {
            this("", new Date(), BigDecimal.ZERO, "", "");
        }

        public Sponsor(String name, Date createTime, BigDecimal money, String contact, String afdianId) {
            this.name = name;
            this.createTime = createTime;
            this.money = money;
            this.contact = contact;
            this.afdianId = afdianId;
        }

        public String getName() {
            return name;
        }

        public Date getCreateTime() {
            return createTime;
        }

        public BigDecimal getMoney() {
            return money;
        }

        public String getContact() {
            return contact;
        }

        public String getAfdianId() {
            return afdianId;
        }
    }
}
