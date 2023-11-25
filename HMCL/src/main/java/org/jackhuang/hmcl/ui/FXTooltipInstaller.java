package org.jackhuang.hmcl.ui;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import org.jackhuang.hmcl.util.Logging;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.logging.Level;

public enum FXTooltipInstaller {
    JAVA_8 {
        private final MethodHandle behaviorConstructor;
        private final MethodHandle installMethod;

        {
            MethodHandle behaviorConstructor0, installMethod0;
            try {
                MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
                Constructor<?> constructor = Class.forName("javafx.scene.control.Tooltip$TooltipBehavior").getDeclaredConstructor(
                        Duration.class, Duration.class, Duration.class, boolean.class
                );
                constructor.setAccessible(true);
                behaviorConstructor0 = LOOKUP.unreflectConstructor(constructor);

                installMethod0 = LOOKUP.findVirtual(
                        Class.forName("javafx.scene.control.Tooltip$TooltipBehavior"),
                        "install",
                        MethodType.methodType(void.class, Node.class, Tooltip.class)
                );
                ready = true;
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot initialize JAVA_8 tooltip installer.", e);
                behaviorConstructor0 = null;
                installMethod0 = null;
                ready = false;
            }
            behaviorConstructor = behaviorConstructor0;
            installMethod = installMethod0;
        }

        @Override
        protected void install0(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) throws Throwable {
            if (behaviorConstructor == null || installMethod == null) {
                throw new IllegalStateException("JAVA_8 tooltip installer is not initialized.");
            }
            installMethod.invoke(
                    behaviorConstructor.invoke(new Duration(openDelay), new Duration(visibleDelay), new Duration(closeDelay), false),
                    node,
                    tooltip
            );
        }
    },
    JAVA_9 {
        private final MethodHandle setShowDelay;
        private final MethodHandle setShowDuration;
        private final MethodHandle setHideDelay;

        {
            MethodHandle setShowDelay1, setShowDuration1, setHideDelay1;
            try {
                MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

                setShowDelay1 = LOOKUP.findVirtual(Tooltip.class, "setShowDelay", MethodType.methodType(void.class, Duration.class));
                setShowDuration1 = LOOKUP.findVirtual(Tooltip.class, "setShowDuration", MethodType.methodType(void.class, Duration.class));
                setHideDelay1 = LOOKUP.findVirtual(Tooltip.class, "setHideDelay", MethodType.methodType(void.class, Duration.class));

                ready = true;
            } catch (ReflectiveOperationException e) {
                Logging.LOG.log(Level.WARNING, "Cannot initialize JAVA_9 tooltip installer.", e);
                setShowDelay1 = null;
                setShowDuration1 = null;
                setHideDelay1 = null;

                ready = false;
            }
            setShowDelay = setShowDelay1;
            setShowDuration = setShowDuration1;
            setHideDelay = setHideDelay1;
        }

        @Override
        protected void install0(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) throws Throwable {
            if (setShowDelay == null || setShowDuration == null || setHideDelay == null) {
                throw new IllegalStateException("JAVA_9 tooltip installer is not initialized.");
            }

            setShowDelay.invoke(tooltip, new Duration(openDelay));
            setShowDuration.invoke(tooltip, new Duration(visibleDelay));
            setHideDelay.invoke(tooltip, new Duration(closeDelay));

            Tooltip.install(node, tooltip);
        }
    },
    NO_DURATION {
        {
            ready = true;
        }

        @Override
        protected void install0(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
            Tooltip.install(node, tooltip);
        }
    };

    private static FXTooltipInstaller installer = values()[0];

    protected abstract void install0(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) throws Throwable;

    protected boolean ready = false;

    private FXTooltipInstaller install(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
        if (!this.ready) {
            FXTooltipInstaller next = values()[this.ordinal() + 1];
            Logging.LOG.log(Level.WARNING, "Tooltip installer " + this.name() + " is not initialized, fallback to " + next.name() + ".");
            return next.install(node, openDelay, visibleDelay, closeDelay, tooltip);
        }

        try {
            this.install0(node, openDelay, visibleDelay, closeDelay, tooltip);
            return this;
        } catch (Throwable e) {
            FXTooltipInstaller next = values()[this.ordinal() + 1];
            Logging.LOG.log(Level.WARNING, "Cannot install tooltip with " + this.name() + " , fallback to " + next.name() + ".", e);
            return next.install(node, openDelay, visibleDelay, closeDelay, tooltip);
        }
    }

    public static void installTooltip(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) {
        installer = installer.install(node, openDelay, visibleDelay, closeDelay, tooltip);
    }
}
