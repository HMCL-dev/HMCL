// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class for implementing {@link Observable}.
 *
 * @author yushijinhun
 */
@NotNullByDefault
public final class ObservableHelper implements Observable, InvalidationListener {

    private final List<InvalidationListener> listeners = new CopyOnWriteArrayList<>();
    private final Observable source;

    public ObservableHelper() {
        this.source = this;
    }

    public ObservableHelper(Observable source) {
        this.source = source;
    }

    /**
     * This method can be called from any thread.
     */
    @Override
    public void addListener(InvalidationListener listener) {
        listeners.add(listener);
    }

    /**
     * This method can be called from any thread.
     */
    @Override
    public void removeListener(InvalidationListener listener) {
        listeners.remove(listener);
    }

    public void invalidate() {
        listeners.forEach(it -> it.invalidated(source));
    }

    @Override
    public void invalidated(Observable observable) {
        listeners.forEach(it -> it.invalidated(observable));
    }

    public void receiveUpdatesFrom(Observable observable) {
        observable.removeListener(this); // remove the previously added listener(if any)
        observable.addListener(this);
    }
}
