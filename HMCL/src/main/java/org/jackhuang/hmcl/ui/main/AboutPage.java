/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2022  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.IconedTwoLineListItem;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AboutPage extends StackPane {

    public AboutPage() {
        ComponentList about = new ComponentList();
        {
            IconedTwoLineListItem launcher = new IconedTwoLineListItem();
            launcher.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
            launcher.setTitle("Hello Minecraft! Launcher");
            launcher.setSubtitle(Metadata.VERSION);
            launcher.setExternalLink(Metadata.PUBLISH_URL);

            IconedTwoLineListItem author = new IconedTwoLineListItem();
            author.setImage(FXUtils.newBuiltinImage("/assets/img/yellow_fish.png"));
            author.setTitle("huanghongxun");
            author.setSubtitle(i18n("about.author.statement"));
            author.setExternalLink("https://space.bilibili.com/1445341");

            about.getContent().setAll(launcher, author);
        }

        ComponentList thanks = loadIconedTwoLineList("/assets/about/thanks.json");

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            IconedTwoLineListItem copyright = new IconedTwoLineListItem();
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));
            copyright.setExternalLink(Metadata.ABOUT_URL);

            IconedTwoLineListItem claim = new IconedTwoLineListItem();
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));
            claim.setExternalLink(Metadata.EULA_URL);

            IconedTwoLineListItem openSource = new IconedTwoLineListItem();
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));
            openSource.setExternalLink("https://github.com/HMCL-dev/HMCL");

            legal.getContent().setAll(copyright, claim, openSource);
        }

        VBox content = new VBox(16);
        content.setPadding(new Insets(10));
        content.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("about")),
                about,

                ComponentList.createComponentListTitle(i18n("about.thanks_to")),
                thanks,

                ComponentList.createComponentListTitle(i18n("about.dependency")),
                deps,

                ComponentList.createComponentListTitle(i18n("about.legal")),
                legal
        );


        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        FXUtils.smoothScrolling(scrollPane);
        getChildren().setAll(scrollPane);
    }

    private static ComponentList loadIconedTwoLineList(String path) {
        ComponentList componentList = new ComponentList();

        InputStream input = FXUtils.class.getResourceAsStream(path);
        if (input == null) {
            LOG.warning("Resources not found: " + path);
            return componentList;
        }

        try {
            JsonArray array = JsonUtils.fromJsonFully(input, JsonArray.class);

            for (JsonElement element : array) {
                JsonObject obj = element.getAsJsonObject();
                IconedTwoLineListItem item = new IconedTwoLineListItem();

                if (obj.has("image")) {
                    String image = obj.get("image").getAsString();
                    item.setImage(image.startsWith("/")
                            ? FXUtils.newBuiltinImage(image)
                            : new Image(image));
                }

                if (obj.has("title"))
                    item.setTitle(obj.get("title").getAsString());
                else if (obj.has("titleLocalized"))
                    item.setTitle(i18n(obj.get("titleLocalized").getAsString()));

                if (obj.has("subtitle"))
                    item.setSubtitle(obj.get("subtitle").getAsString());
                else if (obj.has("subtitleLocalized"))
                    item.setSubtitle(i18n(obj.get("subtitleLocalized").getAsString()));

                if (obj.has("externalLink")) {
                    String link = obj.get("externalLink").getAsString();
                    item.setExternalLink(link);
                    FXUtils.installFastTooltip(item.getExternalLinkButton(), link);
                }

                componentList.getContent().add(item);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }
}
