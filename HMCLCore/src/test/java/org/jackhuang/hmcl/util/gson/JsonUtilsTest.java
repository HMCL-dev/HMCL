/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.gson.JsonUtils.listTypeOf;
import static org.jackhuang.hmcl.util.gson.JsonUtils.mapTypeOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public class JsonUtilsTest {

    @Test
    public void testGetTypeToken() {
        assertEquals(new TypeToken<List<Object>>(){}, listTypeOf(Object.class));
        assertEquals(new TypeToken<List<String>>(){}, listTypeOf(String.class));
        assertEquals(new TypeToken<List<Map<String, Integer>>>(){}, listTypeOf(mapTypeOf(String.class, Integer.class)));
        assertEquals(new TypeToken<List<Map<String, List<Integer>>>>(){}, listTypeOf(mapTypeOf(String.class, listTypeOf(Integer.class))));
    }
}
