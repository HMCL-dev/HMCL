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
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests for immutable sequenced map behavior.
@NotNullByDefault
public final class ImmutableSequencedMapTest {

    /// Immutable sequenced maps follow the standard map object contract.
    @Test
    public void testObjectContract() {
        LinkedHashMap<String, Integer> source = new LinkedHashMap<>();
        source.put("b", 2);
        source.put("a", 1);

        ImmutableSequencedMap<String, Integer> map = ImmutableSequencedMap.copyOf(source);

        assertEquals(source, map);
        assertEquals(source.hashCode(), map.hashCode());
        assertEquals(source.toString(), map.toString());
        assertEquals(List.of("b", "a"), List.copyOf(map.keySet()));
    }
}
