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

import javafx.scene.image.PixelFormat;
import org.jackhuang.hmcl.ui.image.AnimationImage;

/**
 * @author Glavo
 */
public final class AnimationImageImpl extends AnimationImage {
    private final int[][] frames;
    private final int[] durations;

    public AnimationImageImpl(int width, int height,
                              int[][] frames, int[] durations, int cycleCount) {
        super(width, height, cycleCount);
        if (frames.length != durations.length) {
            throw new IllegalArgumentException("frames.length != durations.length");
        }

        this.frames = frames;
        this.durations = durations;
        play();
    }

    @Override
    public int getFramesCount() {
        return frames.length;
    }

    @Override
    public long getDuration(int index) {
        return durations[index];
    }

    protected void updateImage(int frameIndex) {
        final int width = (int) getWidth();
        final int height = (int) getHeight();
        final int[] frame = frames[frameIndex];
        this.getPixelWriter().setPixels(0, 0,
                width, height,
                PixelFormat.getIntArgbInstance(),
                frame, 0, width
        );
    }
}
