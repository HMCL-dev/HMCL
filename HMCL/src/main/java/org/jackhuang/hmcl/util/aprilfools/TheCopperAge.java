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
package org.jackhuang.hmcl.util.aprilfools;

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
    private static final ObservableValue<Color> color = oxidationDegree.map(Degree::getColor);
    private static final BooleanProperty waxed = new SimpleBooleanProperty(false);

    private static final ReadOnlyBooleanWrapper oxidizing = new ReadOnlyBooleanWrapper(false);

    private static final StringBinding displayedState = new StringBinding() {
        {
            bind(oxidationDegree, waxed, oxidizing);
        }

        @Override
        protected String computeValue() {
            if (!oxidizing.get()) return "";
            String state;
            var degree = oxidationDegree.get();
            if (degree == Degree.UNOXIDIZED) {
                state = "";
            } else {
                state = i18n("launcher.april_fools.copper.state." + degree.name().toLowerCase(Locale.ROOT));
            }
            if (waxed.get()) state = i18n("launcher.april_fools.copper.state.waxed") + state;
            return state;
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
            if (random.nextDouble() < 0.1) tryOxidize();
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
