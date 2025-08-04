/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.image.internal;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.WritableValue;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.image.AnimationImage;

import java.lang.ref.WeakReference;

/**
 * @author Glavo
 */
public final class AnimationImageImpl extends WritableImage implements AnimationImage {

    private Animation animation;
    private final int[][] frames;
    private final int[] durations;
    private final int cycleCount;

    public AnimationImageImpl(int width, int height,
                              int[][] frames, int[] durations, int cycleCount) {
        super(width, height);

        if (frames.length != durations.length) {
            throw new IllegalArgumentException("frames.length != durations.length");
        }

        this.frames = frames;
        this.durations = durations;
        this.cycleCount = cycleCount;

        play();
    }

    public void play() {
        if (animation == null) {
            animation = new Animation(this);
            animation.timeline.play();
        }
    }

    private void updateImage(int frameIndex) {
        final int width = (int) getWidth();
        final int height = (int) getHeight();
        final int[] frame = frames[frameIndex];
        this.getPixelWriter().setPixels(0, 0,
                width, height,
                PixelFormat.getIntArgbInstance(),
                frame, 0, width
        );
    }

    private static final class Animation implements WritableValue<Integer> {
        private final Timeline timeline = new Timeline();
        private final WeakReference<AnimationImageImpl> imageRef;

        private Integer value;

        private Animation(AnimationImageImpl image) {
            this.imageRef = new WeakReference<>(image);
            timeline.setCycleCount(image.cycleCount);

            int duration = 0;

            for (int i = 0; i < image.frames.length; ++i) {
                timeline.getKeyFrames().add(
                        new KeyFrame(Duration.millis(duration),
                                new KeyValue(this, i, Interpolator.DISCRETE)));

                duration = duration + image.durations[i];
            }

            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(duration)));
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public void setValue(Integer value) {
            this.value = value;

            AnimationImageImpl image = imageRef.get();
            if (image == null) {
                timeline.stop();
                return;
            }
            image.updateImage(value);
        }
    }
}
