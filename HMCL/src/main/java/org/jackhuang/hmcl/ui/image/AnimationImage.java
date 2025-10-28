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
package org.jackhuang.hmcl.ui.image;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.WritableValue;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.lang.ref.WeakReference;

/**
 * @author Glavo
 */
public abstract class AnimationImage extends WritableImage {
    private Animation animation;
    protected final int cycleCount;
    protected final int width;
    protected final int height;

    public AnimationImage(int width, int height, int cycleCount) {
        super(width, height);
        this.cycleCount = cycleCount;
        this.width = width;
        this.height = height;
    }

    public void play() {
        if (animation == null) {
            animation = new Animation(this);
            animation.timeline.play();
        }
    }

    public abstract int getFramesCount();

    public abstract long getDuration(int index);

    protected abstract void updateImage(int frameIndex);

    private static final class Animation implements WritableValue<Integer> {
        private final Timeline timeline = new Timeline();
        private final WeakReference<AnimationImage> imageRef;

        private Integer value;

        private Animation(AnimationImage image) {
            this.imageRef = new WeakReference<>(image);
            timeline.setCycleCount(image.cycleCount);

            long duration = 0;

            int frames = image.getFramesCount();
            for (int i = 0; i < frames; ++i) {
                timeline.getKeyFrames().add(
                        new KeyFrame(Duration.millis(duration),
                                new KeyValue(this, i, Interpolator.DISCRETE)));

                duration = duration + image.getDuration(i);
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

            AnimationImage image = imageRef.get();
            if (image == null) {
                timeline.stop();
                return;
            }
            image.updateImage(value);
        }
    }
}
