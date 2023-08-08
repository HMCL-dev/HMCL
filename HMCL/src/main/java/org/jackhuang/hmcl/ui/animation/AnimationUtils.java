package org.jackhuang.hmcl.ui.animation;

import org.jackhuang.hmcl.setting.ConfigHolder;

public final class AnimationUtils {

    private AnimationUtils() {
    }

    /**
     * Trigger initialization of this class.
     * Should be called from {@link org.jackhuang.hmcl.setting.Settings#init()}.
     */
    @SuppressWarnings("JavadocReference")
    public static void init() {
    }

    private static final boolean enabled = !ConfigHolder.config().isAnimationDisabled();

    public static boolean isAnimationEnabled() {
        return enabled;
    }
}
