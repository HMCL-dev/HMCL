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
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.List;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class HelpPage extends SpinnerPane {

    private final VBox content;

    public HelpPage() {
        content = new VBox();
        content.setPadding(new Insets(10));
        content.setSpacing(10);
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        setContent(scrollPane);

        var docPane = LineButton.createExternalLinkButton(Metadata.DOCS_URL);
        docPane.setLargeTitle(true);
        docPane.setTitle(i18n("help.doc"));
        docPane.setSubtitle(i18n("help.detail"));

        ComponentList doc = new ComponentList();
        doc.getContent().setAll(docPane);
        content.getChildren().add(doc);

        loadHelp();
    }

    private void loadHelp() {
        showSpinner();
        Task.supplyAsync(() -> HttpRequest.GET(Metadata.DOCS_URL + "/index.json").getJson(listTypeOf(HelpCategory.class)))
                .thenAcceptAsync(Schedulers.javafx(), helpCategories -> {
                    for (HelpCategory category : helpCategories) {
                        ComponentList categoryPane = new ComponentList();

                        for (Help help : category.items()) {
                            var item = LineButton.createExternalLinkButton(help.url());
                            item.setLargeTitle(true);
                            item.setTitle(help.title());
                            item.setSubtitle(help.subtitle());

                            categoryPane.getContent().add(item);
                        }

                        content.getChildren().add(ComponentList.createComponentListTitle(category.title()));
                        content.getChildren().add(categoryPane);
                    }
                    hideSpinner();
                }).start();
    }

    @JsonSerializable
    private record HelpCategory(
            @SerializedName("title") String title,
            @SerializedName("items") List<Help> items) {
    }

    @JsonSerializable
    private record Help(
            @SerializedName("title") String title,
            @SerializedName("subtitle") String subtitle,
            @SerializedName("url") String url) {
    }
}
