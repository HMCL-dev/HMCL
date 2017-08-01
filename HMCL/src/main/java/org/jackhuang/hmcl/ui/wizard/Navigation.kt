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
package org.jackhuang.hmcl.ui.wizard

import org.jackhuang.hmcl.ui.animation.ContainerAnimations

interface Navigation {
    fun onStart()
    fun onNext()
    fun onPrev(cleanUp: Boolean)
    fun canPrev(): Boolean
    fun onFinish()
    fun onCancel()

    enum class NavigationDirection(val animation: ContainerAnimations) {
        START(ContainerAnimations.NONE),
        PREVIOUS(ContainerAnimations.SWIPE_RIGHT),
        NEXT(ContainerAnimations.SWIPE_LEFT),
        FINISH(ContainerAnimations.SWIPE_LEFT),
        IN(ContainerAnimations.ZOOM_IN),
        OUT(ContainerAnimations.ZOOM_OUT)
    }
}