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

import com.jfoenix.skins.JFXProgressBarSkin;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Skin;

// Old
public class JFXProgressBar extends ProgressBar {
    private static final String DEFAULT_STYLE_CLASS = "jfx-progress-bar";
    public boolean forbidsRequestingLayout = false;

    public JFXProgressBar() {
        this.initialize();
    }

    public JFXProgressBar(double progress) {
        super(progress);
        this.initialize();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new JFXProgressBarSkin(this);
    }

    private void initialize() {
        this.setPrefWidth(200.0);
        this.getStyleClass().add("jfx-progress-bar");
    }

    public JFXProgressBar forbidsRequestingLayout() {
        this.forbidsRequestingLayout = true;
        return this;
    }

    @Override
    public void requestLayout() {
        if (!this.forbidsRequestingLayout) {
            super.requestLayout();
        }
    }
}
