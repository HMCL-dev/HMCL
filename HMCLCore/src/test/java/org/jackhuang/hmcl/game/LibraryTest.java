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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/// Tests for library JSON parsing and serialization.
@NotNullByDefault
public final class LibraryTest {

    /// Standard library JSON preserves every supported field during a round trip.
    @Test
    public void testStandardJsonRoundTrip() {
        JsonObject source = JsonParser.parseString("""
                {
                  "name": "org.example:library:1.0.0",
                  "url": "https://example.com/maven/",
                  "downloads": {
                    "artifact": {
                      "path": "org/example/library/1.0.0/library-1.0.0.jar",
                      "url": "https://example.com/library.jar",
                      "sha1": "0123456789abcdef",
                      "size": 42
                    }
                  },
                  "checksums": ["0123456789abcdef"],
                  "extract": {"exclude": ["META-INF/"]},
                  "natives": {"linux": "natives-linux"},
                  "rules": [{"action": "allow"}],
                  "hint": "local",
                  "filename": "library.jar"
                }
                """).getAsJsonObject();

        Library library = Library.fromJson(source);
        JsonObject serialized = library.toJsonObject();

        assertEquals(source, serialized);
        assertEquals(serialized, JsonUtils.GSON.toJsonTree(library));
        assertEquals(serialized, JsonUtils.GSON.fromJson(serialized, Library.class).toJsonObject());
    }

    /// TLauncher fields are normalized to their standard version JSON equivalents.
    @Test
    public void testTLauncherJsonUsesCanonicalFields() {
        JsonObject source = JsonParser.parseString("""
                {
                  "name": "org.example:library:1.0.0",
                  "artifact": {
                    "path": "org/example/library/1.0.0/library-1.0.0.jar",
                    "url": "https://example.com/library.jar"
                  },
                  "classifies": {
                    "linux": {
                      "path": "org/example/library/1.0.0/library-1.0.0-natives-linux.jar",
                      "url": "https://example.com/library-natives-linux.jar"
                    }
                  },
                  "MMC-hint": "local",
                  "MMC-filename": "library.jar"
                }
                """).getAsJsonObject();

        JsonObject serialized = Library.fromJson(source).toJsonObject();
        JsonObject downloads = serialized.getAsJsonObject("downloads");

        assertEquals(source.getAsJsonObject("artifact").get("path"), downloads.getAsJsonObject("artifact").get("path"));
        assertEquals(source.getAsJsonObject("classifies").getAsJsonObject("linux").get("path"),
                downloads.getAsJsonObject("classifiers").getAsJsonObject("linux").get("path"));
        assertEquals("local", serialized.get("hint").getAsString());
        assertEquals("library.jar", serialized.get("filename").getAsString());
        assertFalse(serialized.has("artifact"));
        assertFalse(serialized.has("classifies"));
        assertFalse(serialized.has("MMC-hint"));
        assertFalse(serialized.has("MMC-filename"));
    }
}
