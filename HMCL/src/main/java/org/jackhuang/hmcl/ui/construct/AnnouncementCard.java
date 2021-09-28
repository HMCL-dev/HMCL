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
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class AnnouncementCard extends VBox {

    public AnnouncementCard(String title, String content) {
        TextFlow tf;
        try {
            tf = FXUtils.segmentToTextFlow(content, AnnouncementCard::onAction);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to parse announcement content", e);
            tf = new TextFlow();
            tf.getChildren().setAll(new Text(content));
        }

        Label label = new Label(title);
        label.getStyleClass().add("title");
        getChildren().setAll(label, tf);
        setSpacing(14);
        getStyleClass().addAll("card", "announcement");
    }

    private static void onAction(String href) {
        if (href.startsWith("hmcl://")) {
            if ("hmcl://settings/feedback".equals(href)) {
                Controllers.getSettingsPage().showFeedback();
                Controllers.navigate(Controllers.getSettingsPage());
            }
        } else {
            FXUtils.openLink(href);
        }
    }
}
