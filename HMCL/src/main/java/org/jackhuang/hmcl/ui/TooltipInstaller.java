/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public class TooltipInstaller {
    public static final TooltipInstaller INSTALLER;

    static {
        TooltipInstaller installer = null;

        try {
            installer = new NewInstaller();
        } catch (Throwable e) {
            try {
                installer = new OldInstaller();
            } catch (Throwable e2) {
                e2.addSuppressed(e);
                LOG.warning("Failed to initialize TooltipInstaller", e2);
            }
        }

        INSTALLER = installer != null ? installer : new TooltipInstaller();
    }

    TooltipInstaller() {
    }

    public void installTooltip(Node node, Duration showDelay, Duration showDuration, Duration hideDelay, Tooltip tooltip) {
        Tooltip.install(node, tooltip);
    }

    // For Java 8
    private static final class OldInstaller extends TooltipInstaller {
        private static final Constructor<?> createTooltipBehavior;
        private static final Method installTooltipBehavior;

        static {
            try {
                Class<?> behaviorClass = Class.forName("javafx.scene.control.Tooltip$TooltipBehavior");
                createTooltipBehavior = behaviorClass.getDeclaredConstructor(Duration.class, Duration.class, Duration.class, boolean.class);
                createTooltipBehavior.setAccessible(true);
                installTooltipBehavior = behaviorClass.getDeclaredMethod("install", Node.class, Tooltip.class);
                installTooltipBehavior.setAccessible(true);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public void installTooltip(Node node, Duration showDelay, Duration showDuration, Duration hideDelay, Tooltip tooltip) {
            try {
                Object behavior = createTooltipBehavior.newInstance(showDelay, showDuration, hideDelay, false);
                installTooltipBehavior.invoke(behavior, node, tooltip);
            } catch (ReflectiveOperationException e) {
                LOG.warning("Failed to set tooltip show delay", e);
                Tooltip.install(node, tooltip);
            }
        }
    }

    // For Java 9+
    private static final class NewInstaller extends TooltipInstaller {
        private static final MethodHandle setTooltipShowDelay;
        private static final MethodHandle setTooltipShowDuration;
        private static final MethodHandle setTooltipHideDelay;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodType methodType = MethodType.methodType(void.class, Duration.class);

                setTooltipShowDelay = lookup.findVirtual(Tooltip.class, "setShowDelay", methodType);
                setTooltipShowDuration = lookup.findVirtual(Tooltip.class, "setShowDuration", methodType);
                setTooltipHideDelay = lookup.findVirtual(Tooltip.class, "setHideDelay", methodType);
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public void installTooltip(Node node, Duration showDelay, Duration showDuration, Duration hideDelay, Tooltip tooltip) {
            try {
                setTooltipShowDelay.invokeExact(tooltip, showDelay);
                setTooltipShowDuration.invokeExact(tooltip, showDuration);
                setTooltipHideDelay.invokeExact(tooltip, hideDelay);
            } catch (Throwable e) {
                LOG.warning("Failed to set tooltip show delay", e);
            }

            Tooltip.install(node, tooltip);
        }
    }
}
