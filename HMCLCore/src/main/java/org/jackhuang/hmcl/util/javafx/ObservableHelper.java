/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Helper class for implementing {@link Observable}.
 *
 * @author yushijinhun
 */
public class ObservableHelper implements Observable, InvalidationListener {

    private List<InvalidationListener> listeners = new CopyOnWriteArrayList<>();
    private Observable source;

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
        this.invalidate();
    }

    public void receiveUpdatesFrom(Observable observable) {
        observable.removeListener(this); // remove the previously added listener(if any)
        observable.addListener(this);
    }
}
