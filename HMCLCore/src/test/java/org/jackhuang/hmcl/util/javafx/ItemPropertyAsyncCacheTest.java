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
package org.jackhuang.hmcl.util.javafx;

import org.junit.jupiter.api.Test;

import java.lang.ref.SoftReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;

/// @author Glavo
public class ItemPropertyAsyncCacheTest {

    /// A value stored by one item must be visible to another item built from the same key.
    ///
    /// This is what lets a rebuilt list item reuse the value the item it replaced had decoded: the
    /// cache is keyed by what the value depends on rather than held on the item itself.
    @Test
    public void testSharedValueOutlivesTheItemThatStoredIt() {
        ConcurrentMap<Object, SoftReference<CompletableFuture<String>>> shared = new ConcurrentHashMap<>();
        ItemPropertyAsyncCache.Shared<String, Object> first =
                new ItemPropertyAsyncCache.Shared<>(new Object(), shared, "key", () -> "decoded", () -> "default");
        ItemPropertyAsyncCache.Shared<String, Object> second =
                new ItemPropertyAsyncCache.Shared<>(new Object(), shared, "key", () -> "decoded", () -> "default");

        assertNull(second.getFuture(), "an item must not see a value before one is stored");

        CompletableFuture<String> stored = CompletableFuture.completedFuture("decoded");
        first.setFuture(stored);

        assertSame(stored, second.getFuture(), "a second item must reuse the stored value");
        assertEquals("decoded", second.getFuture().getNow(null));
    }

    /// Keys must not leak into each other.
    @Test
    public void testSharedValueIsScopedToItsKey() {
        ConcurrentMap<Object, SoftReference<CompletableFuture<String>>> shared = new ConcurrentHashMap<>();
        ItemPropertyAsyncCache.Shared<String, Object> first =
                new ItemPropertyAsyncCache.Shared<>(new Object(), shared, "key", () -> "decoded", () -> "default");
        ItemPropertyAsyncCache.Shared<String, Object> other =
                new ItemPropertyAsyncCache.Shared<>(new Object(), shared, "other", () -> "decoded", () -> "default");

        first.setFuture(CompletableFuture.completedFuture("decoded"));

        assertNotNull(first.getFuture());
        assertNull(other.getFuture(), "a different key must not reuse the value");
    }

    /// Storing must replace the previous value rather than accumulate entries.
    @Test
    public void testStoringReplacesThePreviousValue() {
        ConcurrentMap<Object, SoftReference<CompletableFuture<String>>> shared = new ConcurrentHashMap<>();
        ItemPropertyAsyncCache.Shared<String, Object> cache =
                new ItemPropertyAsyncCache.Shared<>(new Object(), shared, "key", () -> "decoded", () -> "default");

        cache.setFuture(CompletableFuture.completedFuture("old"));
        cache.setFuture(CompletableFuture.completedFuture("new"));

        assertEquals(1, shared.size());
        assertEquals("new", cache.getFuture().getNow(null));
    }
}
