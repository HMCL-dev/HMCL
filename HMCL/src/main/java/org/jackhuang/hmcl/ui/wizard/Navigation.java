// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.util.SettingsMap;

public interface Navigation {

    void onStart();

    void onNext();

    void onPrev(boolean cleanUp);

    boolean canPrev();

    void onFinish();

    void onEnd();

    void onCancel();

    SettingsMap getSettings();

    enum NavigationDirection {
        START(ContainerAnimations.NONE),
        PREVIOUS(ContainerAnimations.BACKWARD),
        NEXT(ContainerAnimations.FORWARD),
        FINISH(ContainerAnimations.FORWARD);

        private final ContainerAnimations animation;

        NavigationDirection(ContainerAnimations animation) {
            this.animation = animation;
        }

        public ContainerAnimations getAnimation() {
            return animation;
        }
    }
}
