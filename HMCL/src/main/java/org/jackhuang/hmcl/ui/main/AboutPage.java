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

import com.google.gson.*;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineButton;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AboutPage extends StackPane {

    private final WeakListenerHolder holder = new WeakListenerHolder();

    public AboutPage() {
        ComponentList about = new ComponentList();
        {
            var launcher = LineButton.createExternalLinkButton(Metadata.PUBLISH_URL);
            launcher.setLargeTitle(true);
            launcher.setLeading(FXUtils.newBuiltinImage("/assets/img/icon.png"));
            launcher.setTitle("Hello Minecraft! Launcher");
            launcher.setSubtitle(Metadata.VERSION);

            var author = LineButton.createExternalLinkButton("https://space.bilibili.com/1445341");
            author.setLargeTitle(true);
            author.setLeading(FXUtils.newBuiltinImage("/assets/img/yellow_fish.png"));
            author.setTitle("huanghongxun");
            author.setSubtitle(i18n("about.author.statement"));

            about.getContent().setAll(launcher, author);
        }

        ComponentList thanks = loadIconedTwoLineList("/assets/about/thanks.json");

        ComponentList deps = loadIconedTwoLineList("/assets/about/deps.json");

        ComponentList legal = new ComponentList();
        {
            var copyright = LineButton.createExternalLinkButton(Metadata.ABOUT_URL);
            copyright.setLargeTitle(true);
            copyright.setTitle(i18n("about.copyright"));
            copyright.setSubtitle(i18n("about.copyright.statement"));

            var claim = LineButton.createExternalLinkButton(Metadata.EULA_URL);
            claim.setLargeTitle(true);
            claim.setTitle(i18n("about.claim"));
            claim.setSubtitle(i18n("about.claim.statement"));

            var openSource = LineButton.createExternalLinkButton("https://github.com/HMCL-dev/HMCL");
            openSource.setLargeTitle(true);
            openSource.setTitle(i18n("about.open_source"));
            openSource.setSubtitle(i18n("about.open_source.statement"));

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

    private static Image loadImage(String url) {
        return url.startsWith("/")
                ? FXUtils.newBuiltinImage(url)
                : new Image(url);
    }

    private ComponentList loadIconedTwoLineList(String path) {
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

                var button = new LineButton();
                button.setLargeTitle(true);

                if (obj.get("externalLink") instanceof JsonPrimitive externalLink) {
                    button.setTrailingIcon(SVG.OPEN_IN_NEW);

                    String link = externalLink.getAsString();
                    button.setOnAction(event -> FXUtils.openLink(link));
                }

                if (obj.has("image")) {
                    JsonElement image = obj.get("image");
                    if (image.isJsonPrimitive()) {
                        button.setLeading(loadImage(image.getAsString()));
                    } else if (image.isJsonObject()) {
                        holder.add(FXUtils.onWeakChangeAndOperate(Themes.darkModeProperty(), darkMode -> {
                            button.setLeading(darkMode
                                    ? loadImage(image.getAsJsonObject().get("dark").getAsString())
                                    : loadImage(image.getAsJsonObject().get("light").getAsString())
                            );
                        }));
                    }
                }

                if (obj.get("title") instanceof JsonPrimitive title)
                    button.setTitle(title.getAsString());
                else if (obj.get("titleLocalized") instanceof JsonPrimitive titleLocalized)
                    button.setTitle(i18n(titleLocalized.getAsString()));

                if (obj.get("subtitle") instanceof JsonPrimitive subtitle)
                    button.setSubtitle(subtitle.getAsString());
                else if (obj.get("subtitleLocalized") instanceof JsonPrimitive subtitleLocalized)
                    button.setSubtitle(i18n(subtitleLocalized.getAsString()));

                componentList.getContent().add(button);
            }
        } catch (IOException | JsonParseException e) {
            LOG.warning("Failed to load list: " + path, e);
        }

        return componentList;
    }
}
