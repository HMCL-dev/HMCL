/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui

import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color

object RenderedPartlyTexture {

    fun renderAvatar(ctx: GraphicsContext, skinScale: Int, skinImg: Array<Array<Color>>, width: Double) {
        val helmBs = width / (1.0 * skinScale)
        val headBs = (width - 1.0 * helmBs) / (1.0 * skinScale)
        val headOffset = helmBs / 2.0

        // render head.front
        renderRange(ctx, skinImg, head_front_x * skinScale, head_front_y * skinScale, head_front_w * skinScale, head_front_h * skinScale, headOffset, headOffset, headBs)

        // render helm.front
        renderRange(ctx, skinImg, helm_front_x * skinScale, helm_front_y * skinScale, helm_front_w * skinScale, helm_front_h * skinScale, 0.0, 0.0, helmBs)
    }

    private fun renderRange(ctx: GraphicsContext, img: Array<Array<Color>>, x0: Int, y0: Int, w: Int, h: Int, tx0: Double, ty0: Double, bs: Double) {
        var x: Int
        var y: Int
        var tx: Double
        var ty: Double
        for (dx in 0..w - 1) {
            for (dy in 0..h - 1) {
                x = x0 + dx
                y = y0 + dy
                tx = tx0 + bs * dx
                ty = ty0 + bs * dy
                ctx.fill = img[x][y]
                ctx.fillRect(tx, ty, bs, bs)
            }
        }
    }

    /*
     * The specification of skin can be found at https://github.com/minotar/skin-spec
     */

    internal val head_front_x = 1
    internal val head_front_y = 1
    internal val head_front_w = 1
    internal val head_front_h = 1

    internal val helm_front_x = 5
    internal val helm_front_y = 1
    internal val helm_front_w = 1
    internal val helm_front_h = 1

}