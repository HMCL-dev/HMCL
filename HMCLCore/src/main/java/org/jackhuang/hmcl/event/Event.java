/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public class Event {

    /**
     * The object on which the Event initially occurred.
     */
    protected final transient Object source;

    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @throws NullPointerException if source is null.
     */
    public Event(Object source) {
        Objects.requireNonNull(source);

        this.source = source;
    }

    /**
     * The object on which the Event initially occurred.
     *
     * @return The object on which the Event initially occurred.
     */
    public Object getSource() {
        return source;
    }

    /**
     * Returns a String representation of this Event.
     *
     * @return A a String representation of this Event.
     */
    public String toString() {
        return new ToStringBuilder(this).append("source", source).toString();
    }

    private boolean canceled;

    /**
     * true if this event is canceled.
     *
     * @throws UnsupportedOperationException if trying to cancel a non-cancelable event.
     */
    public final boolean isCanceled() {
        return canceled;
    }

    /**
     * @param canceled new value
     * @throws UnsupportedOperationException if trying to cancel a non-cancelable event.
     */
    public final void setCanceled(boolean canceled) {
        if (!isCancelable())
            throw new UnsupportedOperationException("Attempted to cancel a non-cancelable event: " + getClass());
        this.canceled = canceled;
    }

    /**
     * true if this Event this cancelable.
     */
    public boolean isCancelable() {
        return false;
    }

    public boolean hasResult() {
        return false;
    }

    private Result result = Result.DEFAULT;

    /**
     * Retutns the value set as the result of this event
     */
    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        if (!hasResult())
            throw new UnsupportedOperationException("Attempted to set result on a no result event: " + this.getClass() + " of type.");
        this.result = result;
    }

    public enum Result {
        DENY,
        DEFAULT,
        ALLOW
    }
}
