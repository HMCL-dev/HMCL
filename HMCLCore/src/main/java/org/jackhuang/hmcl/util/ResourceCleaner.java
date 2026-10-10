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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Semaphore;

/// @author Glavo
@NotNullByDefault
public abstract class ResourceCleaner implements AutoCloseable {
    private static final VarHandle CLEANED_HANDLE;

    public static ResourceCleaner acquire(Semaphore semaphore) throws InterruptedException {
        semaphore.acquire();
        return new ResourceCleaner() {
            @Override
            protected void clean() {
                semaphore.release();
            }
        };
    }

    static {
        try {
            CLEANED_HANDLE = MethodHandles.lookup()
                    .findVarHandle(ResourceCleaner.class, "cleaned", boolean.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private volatile boolean cleaned = false;

    @Override
    public void close() {
        if (!(boolean) CLEANED_HANDLE.getAndSet(this, true)) {
            clean();
        }
    }

    protected abstract void clean();
}
