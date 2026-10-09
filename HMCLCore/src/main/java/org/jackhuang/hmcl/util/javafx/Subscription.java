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
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

import java.util.List;
import java.util.Objects;

/// Alternate for JavaFX version below 21
@FunctionalInterface
public interface Subscription {

    Subscription EMPTY = () -> {};

    static Subscription subscribe(Observable observable, Runnable invalidationSubscriber) {
        Objects.requireNonNull(invalidationSubscriber, "invalidationSubscriber cannot be null");
        InvalidationListener listener = obs -> invalidationSubscriber.run();

        observable.addListener(listener);

        return () -> observable.removeListener(listener);
    }

    static Subscription combine(Subscription... subscriptions) {
        List<Subscription> list = List.of(subscriptions);

        return () -> list.forEach(Subscription::unsubscribe);
    }

    void unsubscribe();

    default Subscription and(Subscription other) {
        Objects.requireNonNull(other, "other cannot be null");

        return () -> {
            unsubscribe();
            other.unsubscribe();
        };
    }
}
