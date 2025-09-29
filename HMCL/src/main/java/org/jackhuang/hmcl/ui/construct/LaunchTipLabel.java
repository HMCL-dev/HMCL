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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;


public class LaunchTipLabel extends HBox {
    private final Text bottomTipText;
    private final TextFlow tfwBottomTip;
    private final Timeline tipTimeline;
    private static final List<String> tips;
    private static final List<String> shuffledTips;
    private static final Random random = new Random();
    private static int index = 0;

    static {
        tips = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> i18n(String.format("message.tips_%s", i)))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

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

    // They are useless now ...
    public void stopTips() {
        tipTimeline.stop();
    }

    public TextFlow getTipContainer() {
        return tfwBottomTip;
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
}
