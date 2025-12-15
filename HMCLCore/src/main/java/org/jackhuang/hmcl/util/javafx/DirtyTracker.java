/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
