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

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.lang.System.Logger.Level;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;


public class LaunchTipLabel extends HBox {
    private final Text bottomTipText;
    private final TextFlow tfwBottomTip;
    private final Timeline tipTimeline;
    private static final List<String> tips;
    private static final List<String> shuffledTips;
    private static final Random random = new Random();
    private static int index = 0;

    static {
        tips = loadTipsFromJson();
        shuffledTips = new ArrayList<>(tips);
        Collections.shuffle(shuffledTips, random);
    }

    public LaunchTipLabel() {
        setAlignment(Pos.CENTER);

        tfwBottomTip = new TextFlow();
        tfwBottomTip.setTextAlignment(TextAlignment.CENTER);
        tfwBottomTip.setStyle("-fx-text-fill: rgba(100, 100, 100, 0.9)");
        tfwBottomTip.setPadding(new Insets(0, 8, 0, 0));
        tfwBottomTip.setMaxWidth(300);

        bottomTipText = new Text(getRandomTip());
        tfwBottomTip.getChildren().add(bottomTipText);

        getChildren().add(tfwBottomTip);

        tipTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> nextTip()));
        tipTimeline.setCycleCount(Animation.INDEFINITE);
        tipTimeline.play();
    }

    private void nextTip() {
        String next = getRandomTip();
        Platform.runLater(() -> bottomTipText.setText(next));
    }

    private static String getRandomTip() {
        if (index >= shuffledTips.size()) {
            shuffledTips.clear();
            shuffledTips.addAll(tips);
            Collections.shuffle(shuffledTips, random);
            index = 0;
        }
        return shuffledTips.get(index++);
    }
    private static List<String> loadTipsFromJson() {
        List<String> result = new ArrayList<>();
        String resourceName = "assets.lang.launch_tips.tips";

        try {
            java.net.URL url = I18n.getBuiltinResource(resourceName, "json");
            LOG.log(Level.DEBUG, "Loading " + url);
            if (url != null) {
                Gson gson = new Gson();
                try (InputStream inputStream = url.openStream();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    LaunchTipsData data = gson.fromJson(reader, LaunchTipsData.class);
                    if (data != null && data.tips != null) {
                        result.addAll(data.tips);
                    }
                }
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // ignored
        }

        if (result.isEmpty()) {
            result.add("Welcome to HMCL!");
        }

        return result;
    }


    private static class LaunchTipsData {
        @SerializedName("tips")
        private List<String> tips;
    }
}
