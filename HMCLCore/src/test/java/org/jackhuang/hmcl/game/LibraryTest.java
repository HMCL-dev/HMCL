// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        Library library = JsonUtils.GSON.fromJson(source, Library.class);
        JsonElement serialized = JsonUtils.GSON.toJsonTree(library);

        assertEquals(source, serialized);
        assertEquals(serialized, JsonUtils.GSON.toJsonTree(library));
        assertEquals(serialized, JsonUtils.GSON.toJsonTree(JsonUtils.GSON.fromJson(serialized, Library.class)));
    }

}
