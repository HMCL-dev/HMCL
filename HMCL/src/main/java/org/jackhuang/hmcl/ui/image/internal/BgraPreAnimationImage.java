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

import java.util.List;

/**
 * @author Glavo
 */
public final class BgraPreAnimationImage extends AnimationImage {
    private final List<BgraPreFrame> frames;

    public BgraPreAnimationImage(int width, int height, int cycleCount, List<BgraPreFrame> frames) {
        super(width, height, cycleCount);
        this.frames = frames;

        play();
    }

    @Override
    public int getFramesCount() {
        return frames.size();
    }

    @Override
    public long getDuration(int index) {
        return frames.get(index).getDuration();
    }

    @Override
    protected void updateImage(int frameIndex) {
        BgraPreFrame frame = frames.get(frameIndex);
        getPixelWriter().setPixels(frame.xOffset, frame.yOffset, frame.width, frame.height,
                PixelFormat.getByteBgraPreInstance(),
                frame.pixels, 0, frame.width
        );
    }
}
