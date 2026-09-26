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
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.SVG;

import java.util.*;

public class ServerNetworkLatencyPane extends StackPane {
    private static final Set<ServerNetworkLatencyPane> INSTANCES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Timeline TIMELINE = new Timeline();

    static {
        TIMELINE.getKeyFrames().add(new KeyFrame(Duration.millis(125), e -> {
            for (ServerNetworkLatencyPane c : List.copyOf(INSTANCES)) {
                if (c.state == State.PINGING) {
                    c.next();
                }
            }
        }));
        TIMELINE.setCycleCount(Animation.INDEFINITE);
        TIMELINE.play();
    }

    private final List<Node> pingAnimationItems;
    private int index = 0;
    private State state = State.PINGING;

    public ServerNetworkLatencyPane() {
        INSTANCES.add(this);
        this.pingAnimationItems = Arrays.asList(
                SVG.SERVER_SIGNAL_0_BAR.createIcon(),
                SVG.SERVER_SIGNAL_1_BAR.createIcon(),
                SVG.SERVER_SIGNAL_2_BAR.createIcon(),
                SVG.SERVER_SIGNAL_3_BAR.createIcon(),
                SVG.SERVER_SIGNAL_FULL.createIcon(),
                SVG.SERVER_SIGNAL_3_BAR.createIcon(),
                SVG.SERVER_SIGNAL_2_BAR.createIcon(),
                SVG.SERVER_SIGNAL_1_BAR.createIcon()
        );
        this.pingAnimationItems.forEach(c -> {
            c.setOpacity(0.3);
            c.setManaged(false);
            c.setVisible(false);
        });
    }

    private void next() {
        Node currentNode = pingAnimationItems.get(index++ % pingAnimationItems.size());
        Node nextNode = pingAnimationItems.get(index % pingAnimationItems.size());

        currentNode.setVisible(false);
        currentNode.setManaged(false);
        nextNode.setVisible(true);
        nextNode.setManaged(true);
    }

    public void error() {
        state = State.ERROR;
        getChildren().clear();
        getChildren().add(SVG.SERVER_SIGNAL_ERROR.createIcon());
    }

    public void pong(long networkLatency) {
        state = State.PONG;
        getChildren().clear();
        if (networkLatency < 150) {
            getChildren().add(SVG.SERVER_SIGNAL_FULL.createIcon());
        } else if (networkLatency < 300) {
            getChildren().add(SVG.SERVER_SIGNAL_3_BAR.createIcon());
        } else if (networkLatency < 600) {
            getChildren().add(SVG.SERVER_SIGNAL_2_BAR.createIcon());
        } else if (networkLatency < 1000) {
            getChildren().add(SVG.SERVER_SIGNAL_1_BAR.createIcon());
        } else {
            getChildren().add(SVG.SERVER_SIGNAL_0_BAR.createIcon());
        }
    }

    public void ping() {
        state = State.PINGING;
        getChildren().clear();
        getChildren().addAll(pingAnimationItems);
    }

    private enum State {
        PINGING,
        PONG,
        ERROR;
    }
}
