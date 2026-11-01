// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.aprilfools;

import javafx.beans.binding.ObjectBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FXThread;
import org.jackhuang.hmcl.util.Lang;

import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class TheCopperAge {

    private static final ReadOnlyObjectWrapper<Degree> oxidationDegree = new ReadOnlyObjectWrapper<>(Degree.UNOXIDIZED);
    private static final ObjectBinding<Color> color = new ObjectBinding<>() {
        {
            bind(oxidationDegree);
        }

        @Override
        protected Color computeValue() {
            return oxidationDegree.get().getColor();
        }
    };
    private static final BooleanProperty waxed = new SimpleBooleanProperty(false);

    private static final ReadOnlyBooleanWrapper oxidizing = new ReadOnlyBooleanWrapper(false);

    private static final StringBinding displayedState = new StringBinding() {
        {
            bind(oxidationDegree, waxed, oxidizing);
        }

        @Override
        protected String computeValue() {
            if (!oxidizing.get()) return "";

            String waxedString = i18n("launcher.april_fools.copper.state.waxed");

            var degree = oxidationDegree.get();
            if (degree == Degree.UNOXIDIZED) {
                return waxed.get() ?
                        i18n("launcher.april_fools.copper.state.0", waxedString)
                        : "";
            } else {
                String degreeName = i18n("launcher.april_fools.copper.state." + degree.name().toLowerCase(Locale.ROOT));
                return waxed.get()
                        ? i18n("launcher.april_fools.copper.state.1", waxedString, degreeName)
                        : i18n("launcher.april_fools.copper.state.0", degreeName);
            }
        }
    };

    private static ScheduledExecutorService oxidationScheduler = null;

    public static ObservableValue<Color> colorProperty() {
        return color;
    }

    public static Color getColor() {
        return colorProperty().getValue();
    }

    public static ReadOnlyBooleanProperty oxidizingProperty() {
        return oxidizing.getReadOnlyProperty();
    }

    public static boolean isOxidizing() {
        return oxidizing.get();
    }

    public static StringBinding displayedState() {
        return displayedState;
    }

    @FXThread
    public static void startOxidation() {
        if (isOxidizing()) return;
        oxidizing.set(true);
        oxidationScheduler = Executors.newScheduledThreadPool(1, Lang.daemonThreadFactory("The Copper Age Oxidizer"));
        var random = new Random();

        oxidationScheduler.scheduleAtFixedRate(() -> {
            if (random.nextDouble() < 0.2) tryOxidize();
        }, 2, 5, TimeUnit.SECONDS);
    }

    @FXThread
    public static void stopAndClearState() {
        if (!isOxidizing()) return;
        oxidizing.set(false);
        oxidationScheduler.shutdownNow();
        oxidationScheduler = null;
        oxidationDegree.set(Degree.UNOXIDIZED);
        waxed.set(false);
    }

    private static void tryOxidize() {
        FXUtils.runInFX(() -> {
            if (!waxed.get()) oxidationDegree.set(Degree.values()[Math.min(oxidationDegree.get().ordinal() + 1, Degree.values().length - 1)]);
        });
    }

    @FXThread
    public static void wax() {
        waxed.set(true);
    }

    @FXThread
    public static void useAxe() {
        if (waxed.get()) {
            waxed.set(false);
        } else {
            oxidationDegree.set(Degree.values()[Math.max(oxidationDegree.get().ordinal() - 1, 0)]);
        }
    }

    private TheCopperAge() {
    }

    public enum Degree {
        UNOXIDIZED("#D87F33"),
        EXPOSED("#876B62"),
        WEATHERED("#3A8E8C"),
        OXIDIZED("#167E86");

        private final Color color;

        Degree(String color) {
            this.color = Color.web(color);
        }

        public Color getColor() {
            return color;
        }
    }
}
