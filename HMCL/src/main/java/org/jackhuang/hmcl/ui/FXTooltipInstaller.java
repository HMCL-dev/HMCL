package org.jackhuang.hmcl.ui;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import net.burningtnt.bcigenerator.api.BytecodeImpl;
import net.burningtnt.bcigenerator.api.BytecodeImplError;
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
        {
            ready = true;
        }

        @Override
        protected void install0(Node node, double openDelay, double visibleDelay, double closeDelay, Tooltip tooltip) throws NoSuchMethodError {
            this.setShowDelay(tooltip, new Duration(openDelay));
            this.setShowDuration(tooltip, new Duration(visibleDelay));
            this.setHideDelay(tooltip, new Duration(closeDelay));

            Tooltip.install(node, tooltip);
        }

        @BytecodeImpl({
                "LABEL METHOD_HEAD",
                "ALOAD 1",
                "ALOAD 2",
                "INVOKEVIRTUAL Ljavafx/scene/control/Tooltip;setShowDelay(Ljavafx/util/Duration;)V",
                "LABEL RELEASE_PARAMETER",
                "RETURN",
                "LOCALVARIABLE this Lorg/jackhuang/hmcl/ui/FXTooltipInstaller$2; METHOD_HEAD RELEASE_PARAMETER 2",
                "LOCALVARIABLE tooltip Ljavafx/scene/control/Tooltip; METHOD_HEAD RELEASE_PARAMETER 0",
                "LOCALVARIABLE duration Ljavafx/util/Duration; METHOD_HEAD RELEASE_PARAMETER 1",
                "MAXS 2 3"
        })
        @SuppressWarnings("unused")
        private void setShowDelay(Tooltip tooltip, Duration duration) {
            throw new BytecodeImplError();
        }

        @BytecodeImpl({
                "LABEL METHOD_HEAD",
                "ALOAD 1",
                "ALOAD 2",
                "INVOKEVIRTUAL Ljavafx/scene/control/Tooltip;setShowDuration(Ljavafx/util/Duration;)V",
                "LABEL RELEASE_PARAMETER",
                "RETURN",
                "LOCALVARIABLE this Lorg/jackhuang/hmcl/ui/FXTooltipInstaller$2; METHOD_HEAD RELEASE_PARAMETER 2",
                "LOCALVARIABLE tooltip Ljavafx/scene/control/Tooltip; METHOD_HEAD RELEASE_PARAMETER 0",
                "LOCALVARIABLE duration Ljavafx/util/Duration; METHOD_HEAD RELEASE_PARAMETER 1",
                "MAXS 2 3"
        })
        @SuppressWarnings("unused")
        private void setShowDuration(Tooltip tooltip, Duration duration) {
            throw new BytecodeImplError();
        }

        @BytecodeImpl({
                "LABEL METHOD_HEAD",
                "ALOAD 1",
                "ALOAD 2",
                "INVOKEVIRTUAL Ljavafx/scene/control/Tooltip;setHideDelay(Ljavafx/util/Duration;)V",
                "LABEL RELEASE_PARAMETER",
                "RETURN",
                "LOCALVARIABLE this Lorg/jackhuang/hmcl/ui/FXTooltipInstaller$2; METHOD_HEAD RELEASE_PARAMETER 2",
                "LOCALVARIABLE tooltip Ljavafx/scene/control/Tooltip; METHOD_HEAD RELEASE_PARAMETER 0",
                "LOCALVARIABLE duration Ljavafx/util/Duration; METHOD_HEAD RELEASE_PARAMETER 1",
                "MAXS 2 3"
        })
        @SuppressWarnings("unused")
        private void setHideDelay(Tooltip tooltip, Duration duration) {
            throw new BytecodeImplError();
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
