// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.javafx;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/// @author Glavo
public final class DirtyTracker {

    private final Set<Observable> dirty = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Listener listener = new Listener(this);

    public void track(Observable observable) {
        if (!dirty.contains(observable))
            observable.addListener(listener);
    }

    public boolean isDirty(Observable observable) {
        return dirty.contains(observable);
    }

    public void markDirty(Observable observable) {
        observable.removeListener(listener);
        dirty.add(observable);
    }

    private static final class Listener implements InvalidationListener, WeakListener {

        private final WeakReference<DirtyTracker> trackerReference;

        public Listener(DirtyTracker trackerReference) {
            this.trackerReference = new WeakReference<>(trackerReference);
        }

        @Override
        public boolean wasGarbageCollected() {
            return trackerReference.get() == null;
        }

        @Override
        public void invalidated(Observable observable) {
            observable.removeListener(this);

            DirtyTracker tracker = trackerReference.get();
            if (tracker != null)
                tracker.markDirty(observable);
        }
    }
}
