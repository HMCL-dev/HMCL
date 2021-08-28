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
import javafx.geometry.Insets;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class HelpPage extends SpinnerPane {

    private final VBox content;

    public HelpPage() {
        content = new VBox();
        content.setPadding(new Insets(10));
        content.setSpacing(10);
        content.setFillWidth(true);
        setContent(content);

        IconedTwoLineListItem docPane = new IconedTwoLineListItem();
        docPane.setTitle(i18n("help.doc"));
        docPane.setSubtitle(i18n("help.detail"));
        docPane.setExternalLink(Metadata.HELP_URL);
        ComponentList doc = new ComponentList();
        doc.getContent().setAll(docPane);
        content.getChildren().add(doc);

        loadHelp();
    }

    private void loadHelp() {
        showSpinner();
        Task.<List<HelpCategory>>supplyAsync(() -> HttpRequest.GET("https://hmcl.huangyuhui.net/api/help/").getJson(new TypeToken<List<HelpCategory>>() {
        }.getType()))
                .thenAcceptAsync(Schedulers.javafx(), helpCategories -> {
                    for (HelpCategory category : helpCategories) {
                        ComponentList categoryPane = new ComponentList();

                        for (Help help : category.getItems()) {
                            IconedTwoLineListItem item = new IconedTwoLineListItem();
                            item.setTitle(help.getTitle());
                            item.setSubtitle(help.getSubtitle());
                            item.setExternalLink(help.getUrl());

                            categoryPane.getContent().add(item);
                        }

                        content.getChildren().add(ComponentList.createComponentListTitle(category.title));
                        content.getChildren().add(categoryPane);
                    }
                    hideSpinner();
                }).start();
    }

    private static class HelpCategory {
        @SerializedName("title")
        private final String title;

        @SerializedName("items")
        private final List<Help> items;

        public HelpCategory() {
            this("", Collections.emptyList());
        }

        public HelpCategory(String title, List<Help> items) {
            this.title = title;
            this.items = items;
        }

        public String getTitle() {
            return title;
        }

        public List<Help> getItems() {
            return items;
        }
    }

    private static class Help {
        @SerializedName("title")
        private final String title;

        @SerializedName("subtitle")
        private final String subtitle;

        @SerializedName("url")
        private final String url;

        public Help() {
            this("", "", "");
        }

        public Help(String title, String subtitle, String url) {
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getUrl() {
            return url;
        }
    }
}
