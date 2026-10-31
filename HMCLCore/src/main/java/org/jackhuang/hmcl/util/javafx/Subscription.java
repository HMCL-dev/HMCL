// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
