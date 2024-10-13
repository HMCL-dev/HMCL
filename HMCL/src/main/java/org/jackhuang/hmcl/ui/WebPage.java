/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WebPage extends SpinnerPane implements DecoratorPage {

    private final ObjectProperty<DecoratorPage.State> stateProperty;

    public WebPage(String title, String content) {
        this.stateProperty = new SimpleObjectProperty<>(DecoratorPage.State.fromTitle(title));
        this.setBackground(new Background(new BackgroundFill(Color.WHITE, null, null)));

        Task.supplyAsync(() -> {
            Document document = Jsoup.parseBodyFragment(content);
            HTMLRenderer renderer = new HTMLRenderer(uri -> {
                Controllers.confirm(i18n("web.open_in_browser", uri), i18n("message.confirm"), () -> {
                    FXUtils.openLink(uri.toString());
                }, null);
            });
            renderer.appendNode(document);
            return renderer.render();
        }).whenComplete(Schedulers.javafx(), ((result, exception) -> {
            if (exception == null) {
                ScrollPane scrollPane = new ScrollPane();
                scrollPane.setFitToWidth(true);
                scrollPane.setContent(result);
                setContent(scrollPane);
                setFailedReason(null);
            } else {
                LOG.warning("Failed to load content", exception);
                setFailedReason(i18n("web.failed"));
            }
        })).start();
    }

    @Override
    public ReadOnlyObjectProperty<DecoratorPage.State> stateProperty() {
        return stateProperty;
    }
}
