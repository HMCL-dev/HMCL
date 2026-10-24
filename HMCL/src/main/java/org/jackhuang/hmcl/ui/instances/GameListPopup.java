/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXPopup;
import com.jfoenix.skins.JFXPopupSkin;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.ui.FXUtils.SINE;

/// Instance popup with animated dismissal and native lifecycle handling.
@NotNullByDefault
final class GameListPopup extends JFXPopup {
    private final boolean animated;

    GameListPopup(Region content, boolean animated) {
        super(content);
        this.animated = animated;
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin(this);
    }

    /// Animates dismissal unless the owner window is hidden.
    @Override
    public void hide() {
        if (!isShowing()) {
            return;
        }

        @Nullable Window owner = getOwnerWindow();
        if (animated && owner != null && owner.isShowing() && getSkin() instanceof Skin skin) {
            skin.hide(this::hideImmediately);
        } else {
            hideImmediately();
        }
    }

    private void hideImmediately() {
        super.hide();
    }

    /// Closes using the opening animation's scale and anchor.
    @NotNullByDefault
    private static final class Skin extends JFXPopupSkin {
        private @Nullable Timeline closeAnimation;

        private boolean closing;

        private boolean mouseTransparentBeforeClosing;

        private Skin(GameListPopup popup) {
            super(popup);
        }

        @Override
        public void animate() {
            // The queued opening callback may arrive after dismissal starts.
            if (control.isShowing() && !closing) {
                super.animate();
            }
        }

        /// Closes from the current frame, then runs completion.
        private void hide(Runnable completion) {
            if (closing) {
                return;
            }
            closing = true;

            double opacity = container.getOpacity();
            double scaleX = scale.getX();
            double scaleY = scale.getY();
            // init() stops opening but resets opacity and scale; preserve the current frame.
            super.init();
            container.setOpacity(opacity);
            scale.setX(scaleX);
            scale.setY(scaleY);
            mouseTransparentBeforeClosing = container.isMouseTransparent();
            container.setMouseTransparent(true);

            Timeline animation = new Timeline(new KeyFrame(Duration.millis(160),
                    new KeyValue(container.opacityProperty(), 0, SINE),
                    new KeyValue(scale.xProperty(), 0, SINE),
                    new KeyValue(scale.yProperty(), 0, SINE)));
            closeAnimation = animation;
            animation.setOnFinished(event -> completion.run());
            animation.play();
        }

        @Override
        public void init() {
            if (closeAnimation != null) {
                closeAnimation.stop();
                closeAnimation = null;
            }
            if (closing) {
                container.setMouseTransparent(mouseTransparentBeforeClosing);
                closing = false;
            }
            super.init();
        }

        @Override
        public void dispose() {
            if (closeAnimation != null) {
                closeAnimation.stop();
                closeAnimation = null;
            }
            super.dispose();
        }
    }
}
