/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.ExceptionalSupplier;

public final class SimpleTaskResult<V> extends TaskResult<V> {
    private final String id;
    private final ExceptionalSupplier<V, ?> supplier;

    public SimpleTaskResult(String id, ExceptionalSupplier<V, ?> supplier) {
        this.id = id;
        this.supplier = supplier;
    }

    @Override
    public void execute() throws Exception {
        setResult(supplier.get());
    }

    @Override
    public String getId() {
        return id;
    }
}
