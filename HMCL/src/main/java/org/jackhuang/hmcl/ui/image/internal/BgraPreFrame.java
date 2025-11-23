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

import org.jackhuang.hmcl.ui.image.AnimationFrame;

public class BgraPreFrame extends BgraPreCanvas implements AnimationFrame {
    final int xOffset;
    final int yOffset;
    final long duration;

    public BgraPreFrame(byte[] pixels, int width, int height, int xOffset, int yOffset, long duration) {
        super(pixels, width, height);

        this.duration = duration;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
    }

    public BgraPreFrame(int width, int height, int xOffset, int yOffset, long duration) {
        this(new byte[4 * width * height], width, height, xOffset, yOffset, duration);
    }

    public BgraPreFrame(BgraPreCanvas canvas, int width, int height, int xOffset, int yOffset, long duration) {
        this(canvas.getPixels(xOffset, yOffset, width, height), width, height, xOffset, yOffset, duration);
    }

    @Override
    public long getDuration() {
        return duration;
    }
}
