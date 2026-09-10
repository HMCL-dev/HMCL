/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.controls;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * JFXClippedPane is a StackPane that clips its content if exceeding the pane bounds.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2018-06-02
 */
public class JFXClippedPane extends StackPane {

    private final Region clip = new Region();

    public JFXClippedPane() {
        super();
        init();
    }

    public JFXClippedPane(Node... children) {
        super(children);
        init();
    }

    private void init() {
        setClip(clip);
        clip.setBackground(new Background(new BackgroundFill(Color.BLACK, new CornerRadii(2), Insets.EMPTY)));
        backgroundProperty().addListener(observable -> JFXNodeUtils.updateBackground(getBackground(), clip));
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        clip.resizeRelocate(0, 0, getWidth(), getHeight());
    }
}
