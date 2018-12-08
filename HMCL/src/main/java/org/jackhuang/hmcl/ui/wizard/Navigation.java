/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.wizard;

import org.jackhuang.hmcl.ui.animation.ContainerAnimations;

public interface Navigation {

    void onStart();
    void onNext();
    void onPrev(boolean cleanUp);
    boolean canPrev();
    void onFinish();
    void onEnd();
    void onCancel();

    enum NavigationDirection {
        START(ContainerAnimations.NONE),
        PREVIOUS(ContainerAnimations.SWIPE_RIGHT),
        NEXT(ContainerAnimations.SWIPE_LEFT),
        FINISH(ContainerAnimations.SWIPE_LEFT),
        IN(ContainerAnimations.ZOOM_IN),
        OUT(ContainerAnimations.ZOOM_OUT);

        private final ContainerAnimations animation;

        NavigationDirection(ContainerAnimations animation) {
            this.animation = animation;
        }

        public ContainerAnimations getAnimation() {
            return animation;
        }
    }
}
