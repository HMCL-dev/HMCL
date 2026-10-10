/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances.server;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.SVG;

public class ServerNetworkLatencyPane extends StackPane {
    private static final SVG[] FRAMES = {
            SVG.SERVER_SIGNAL_0_BAR,
            SVG.SERVER_SIGNAL_1_BAR,
            SVG.SERVER_SIGNAL_2_BAR,
            SVG.SERVER_SIGNAL_3_BAR,
            SVG.SERVER_SIGNAL_FULL,
            SVG.SERVER_SIGNAL_3_BAR,
            SVG.SERVER_SIGNAL_2_BAR,
            SVG.SERVER_SIGNAL_1_BAR
    };

    private final SVGPath icon = SVG.NONE.createIcon();
    private final Timeline timeline = new Timeline(new KeyFrame(Duration.millis(125), e -> nextFrame()));
    private int index = 0;
    private State state = State.PINGING;

    public ServerNetworkLatencyPane() {
        timeline.setCycleCount(Animation.INDEFINITE);
        getChildren().setAll(icon);
        sceneProperty().addListener((obs, o, n) -> updateTimeline());
        ping();
    }

    private void nextFrame() {
        setIcon(FRAMES[index++ % FRAMES.length], "", 0.3);
    }

    private void updateTimeline() {
        if (state == State.PINGING && getScene() != null) {
            timeline.play();
        } else {
            timeline.stop();
        }
    }

    public void ping() {
        state = State.PINGING;
        index = 0;
        nextFrame();
        updateTimeline();
    }

    public void pong(long latency) {
        state = State.PONG;
        updateTimeline();

        SVG svg = latency < 150 ? SVG.SERVER_SIGNAL_FULL
                : latency < 300 ? SVG.SERVER_SIGNAL_3_BAR
                : latency < 600 ? SVG.SERVER_SIGNAL_2_BAR
                : latency < 1000 ? SVG.SERVER_SIGNAL_1_BAR
                : SVG.SERVER_SIGNAL_0_BAR;
        setIcon(svg, "", 1);
    }

    public void error() {
        state = State.ERROR;
        updateTimeline();
        setIcon(SVG.SERVER_SIGNAL_ERROR, "-fx-fill: -monet-error;", 1);
    }

    private void setIcon(SVG svg, String style, double opacity) {
        icon.setContent(svg.getPath());
        icon.setStyle(style);
        icon.setOpacity(opacity);
    }

    private enum State {
        PINGING,
        PONG,
        ERROR;
    }
}
