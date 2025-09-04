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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/// @author Glavo
public final class DirtyTracker implements InvalidationListener {

    private final Set<Observable> tracked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Observable> dirty = Collections.newSetFromMap(new IdentityHashMap<>());

    public boolean isDirty(Observable observable) {
        return dirty.contains(observable);
    }

    public void markClean(Observable observable) {
        if (tracked.add(observable))
            observable.addListener(this);
        dirty.remove(observable);
    }

    public void markDirty(Observable observable) {
        if (tracked.remove(observable))
            observable.removeListener(this);
        dirty.add(observable);
    }

    @Override
    public void invalidated(Observable observable) {
        markDirty(observable);
    }
}
