/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

/**
 *
 * @author huang
 */
public class FailedEvent<T> extends Event {

    private final int failedTime;
    private T newResult;

    public FailedEvent(Object source, int failedTime, T newResult) {
        super(source);
        this.failedTime = failedTime;
        this.newResult = newResult;
    }

    public int getFailedTime() {
        return failedTime;
    }

    public T getNewResult() {
        return newResult;
    }

    public void setNewResult(T newResult) {
        this.newResult = newResult;
    }

}
