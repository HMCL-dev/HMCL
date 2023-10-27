/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.Logging;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.logging.Level;

public abstract class TooltipInstaller {
    // Fallback: Java8TooltipInstaller -> Java9TooltipInstaller -> NoDurationTooltipInstaller

    private static final class Java8TooltipInstaller extends TooltipInstaller {
        public static TooltipInstaller of() {
            try {
                // Java 8
                Class<?> behaviorClass = Class.forName("javafx.scene.control.Tooltip$TooltipBehavior");
                Constructor<?> behaviorConstructor = behaviorClass.getDeclaredConstructor(Duration.class, Duration.class, Duration.class, boolean.class);
                behaviorConstructor.setAccessible(true);
                Method installMethod = behaviorClass.getDeclaredMethod("install", Node.class, Tooltip.class);
                installMethod.setAccessible(true);
                return new Java8TooltipInstaller(behaviorConstructor, installMethod);
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot use Java 8 Tooltip Installer, fallback to Java 9 Tooltip Installer.", e);
                return Java9TooltipInstaller.of();
            }
        }

        private final Constructor<?> behaviorConstructor;

        private final Method installMethod;

        public Java8TooltipInstaller(Constructor<?> behaviorConstructor, Method installMethod) {
            this.behaviorConstructor = behaviorConstructor;
            this.installMethod = installMethod;
        }

        @Override
        protected TooltipInstaller installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
            try {
                installMethod.invoke(
                        behaviorConstructor.newInstance(new Duration(openDelay), new Duration(visibleDelay), new Duration(closeDelay), false),
                        node,
                        tooltip
                );

                return this;
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot use Java 8 Tooltip Installer, fallback to Java 9 Tooltip Installer.", e);
                return Java9TooltipInstaller.of().installTooltip(node, openDelay, visibleDelay, closeDelay, tooltip);
            }
        }
    }

    private static final class Java9TooltipInstaller extends TooltipInstaller {
        public static TooltipInstaller of() {
            try {
                Method setShowDelay = Tooltip.class.getMethod("setShowDelay", Duration.class);
                Method setShowDuration = Tooltip.class.getMethod("setShowDuration", Duration.class);
                Method setHideDelay = Tooltip.class.getMethod("setHideDelay", Duration.class);
                return new Java9TooltipInstaller(setShowDelay, setShowDuration, setHideDelay);
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot use Java 9 Tooltip Installer, fallback to No Duration Tooltip Installer.", e);
                return new NoDurationTooltipInstaller();
            }
        }

        private final Method setShowDelayMethod;
        private final Method setShowDurationMethod;
        private final Method setHideDelayMethod;

        private Java9TooltipInstaller(Method setShowDelayMethod, Method setShowDurationMethod, Method setHideDelayMethod) {
            this.setShowDelayMethod = setShowDelayMethod;
            this.setShowDurationMethod = setShowDurationMethod;
            this.setHideDelayMethod = setHideDelayMethod;
        }

        @Override
        protected TooltipInstaller installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
            try {
                setShowDelayMethod.invoke(tooltip, new Duration(openDelay));
                setShowDurationMethod.invoke(tooltip, new Duration(visibleDelay));
                setHideDelayMethod.invoke(tooltip, new Duration(closeDelay));
                Tooltip.install(node, tooltip);
                return this;
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot use Java 9 Tooltip Installer, fallback to No Duration Tooltip Installer.", e);
                return new NoDurationTooltipInstaller().installTooltip(node, openDelay, visibleDelay, closeDelay, tooltip);
            }
        }
    }

    private static final class NoDurationTooltipInstaller extends TooltipInstaller {
        @Override
        protected TooltipInstaller installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
            Tooltip.install(node, tooltip);
            return this;
        }
    }

    private static TooltipInstaller installer = null;

    // FXThread
    public static void install(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
        FXUtils.checkFxUserThread();

        installer = (installer == null ? Java8TooltipInstaller.of() : installer).installTooltip(node, openDelay, visibleDelay, closeDelay, tooltip);
    }

    protected abstract TooltipInstaller installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip);
}
