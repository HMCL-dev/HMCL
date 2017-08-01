/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl

import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import org.jackhuang.hmcl.util.ValidationTypeAdapterFactory
import org.jackhuang.hmcl.util.Validation
import org.junit.Before
import org.junit.Test
import java.io.IOException

class GsonTest {

    class JsonTest(val required: Int = 0, val optional: Int = 0)
        : Validation {

        override fun validate() {
            if (required == 0)
                throw IllegalStateException("Required field missing")
        }
    }

    val JSON_WITH_REQUIRED_FIELDS = "{\"required\": 123, \"optional\": 234}"
    val JSON_WITHOUT_REQUIRED_FIELDS = "{\"optional\": 234}"
    val GSON = GsonBuilder().registerTypeAdapterFactory(ValidationTypeAdapterFactory)
            .create()

    @Before
    fun setup() {
    }

    @Test
    fun test() {
        GSON.fromJson<JsonTest>(JSON_WITH_REQUIRED_FIELDS, JsonTest::class.java)
        try {
            GSON.fromJson<JsonTest>(JSON_WITHOUT_REQUIRED_FIELDS, JsonTest::class.java)
            throw AssertionError("Failed json test")
        } catch(e: JsonSyntaxException) {
            // nothing
        } catch (e: Exception) {
            throw AssertionError("Failed json test")
        }
    }
}