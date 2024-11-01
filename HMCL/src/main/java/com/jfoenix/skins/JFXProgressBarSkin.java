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

package com.jfoenix.skins;

import com.jfoenix.adapters.skins.ProgressBarSkinAdapter;
import com.jfoenix.controls.JFXProgressBar;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

// Old
public class JFXProgressBarSkin extends ProgressBarSkinAdapter {
    private static final Color indicatorColor = Color.valueOf("#0F9D58");
    private static final Color trackColor = Color.valueOf("#E0E0E0");
    private StackPane bar;
    private Region clip;

    public JFXProgressBarSkin(JFXProgressBar progressBar) {
        super(progressBar);
        this.init();
        this.__registerChangeListener(this.getSkinnable().indeterminateProperty(), "INDETERMINATE");
    }

    @Override
    protected void __handleControlPropertyChanged(String p) {
        if ("INDETERMINATE".equals(p)) {
            this.init();
        }
    }

    protected void init() {
        this.bar = (StackPane) this.getChildren().get(1);
        this.bar.setBackground(new Background(new BackgroundFill(indicatorColor, CornerRadii.EMPTY, Insets.EMPTY)));
        this.bar.setPadding(new Insets(1.5));
        StackPane track = (StackPane) this.getChildren().get(0);
        track.setBackground(new Background(new BackgroundFill(trackColor, CornerRadii.EMPTY, Insets.EMPTY)));
        this.clip = new Region();
        this.clip.setBackground(new Background(new BackgroundFill(Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
        this.getSkinnable().setClip(this.clip);
        this.getSkinnable().requestLayout();
    }

    protected void layoutChildren(double x, double y, double W, double h) {
        super.layoutChildren(x, y, W, h);
        this.clip.resizeRelocate(x, y, W, h);
        if (this.getSkinnable().isIndeterminate()) {
            if (this.__getIndeterminateTransition() != null) {
                this.__getIndeterminateTransition().stop();
            }

            ProgressIndicator control = this.getSkinnable();
            double w = control.getWidth() - (this.snappedLeftInset() + this.snappedRightInset());
            double bWidth = this.bar.getWidth();
            this.__setIndeterminateTransition(new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(this.bar.scaleXProperty(), 0, Interpolator.EASE_IN),
                            new KeyValue(this.bar.translateXProperty(), -bWidth, Interpolator.LINEAR)),
                    new KeyFrame(Duration.seconds(0.5),
                            new KeyValue(this.bar.scaleXProperty(), 3, Interpolator.LINEAR),
                            new KeyValue(this.bar.translateXProperty(), w / 2.0, Interpolator.LINEAR)),
                    new KeyFrame(Duration.seconds(1.0),
                            new KeyValue(this.bar.scaleXProperty(), 0, Interpolator.EASE_OUT),
                            new KeyValue(this.bar.translateXProperty(), w, Interpolator.LINEAR))));
            this.__getIndeterminateTransition().setCycleCount(-1);
            this.__getIndeterminateTransition().play();
        }

    }
}

