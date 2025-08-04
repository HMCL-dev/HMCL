package org.jackhuang.hmcl.ui.image.internal;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.WritableValue;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import java.lang.ref.WeakReference;
import java.util.List;

public final class AnimationImageImpl extends WritableImage {

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
