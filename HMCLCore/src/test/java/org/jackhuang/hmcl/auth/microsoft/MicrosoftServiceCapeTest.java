/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.auth.microsoft;

import org.jackhuang.hmcl.auth.microsoft.MicrosoftService.MinecraftProfileResponse;
import org.jackhuang.hmcl.auth.yggdrasil.Texture;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/// Unit tests for the Minecraft Services cape response parsing and texture
/// extraction introduced for the cape management feature.
public final class MicrosoftServiceCapeTest {

    private static final String UUID = "069a79f444e94726a5befca90e38aaf5";

    /// Builds a profile response with the given JSON `capes` array.
    private static MinecraftProfileResponse parseProfile(String capesJson) {
        String json = """
                {
                  "id": "%s",
                  "name": "TestPlayer",
                  "skins": [
                    {"id": "skin-1", "state": "ACTIVE", "url": "https://example.com/skin.png", "variant": "CLASSIC", "alias": "Steve"}
                  ],
                  "capes": %s
                }
                """.formatted(UUID, capesJson);
        return JsonUtils.fromNonNullJson(json, MinecraftProfileResponse.class);
    }

    @Test
    public void testCapesDeserialization() {
        MinecraftProfileResponse profile = parseProfile("""
                [
                  {"id": "cape-migrator", "state": "ACTIVE", "url": "https://example.com/migrator.png", "alias": "Migrator"},
                  {"id": "cape-vanilla", "state": "INACTIVE", "url": "https://example.com/vanilla.png", "alias": "Vanilla"}
                ]
                """);

        assertEquals(2, profile.capes.size());
        assertEquals("cape-migrator", profile.capes.get(0).id);
        assertEquals("ACTIVE", profile.capes.get(0).state);
        assertEquals("Migrator", profile.capes.get(0).alias);
        assertEquals("INACTIVE", profile.capes.get(1).state);
    }

    @Test
    public void testActiveCapeTextureIsSelected() {
        MinecraftProfileResponse profile = parseProfile("""
                [
                  {"id": "cape-a", "state": "INACTIVE", "url": "https://example.com/a.png", "alias": "A"},
                  {"id": "cape-b", "state": "ACTIVE", "url": "https://example.com/b.png", "alias": "B"}
                ]
                """);

        Optional<Map<TextureType, Texture>> textures = MicrosoftService.getTextures(profile);

        assertTrue(textures.isPresent());
        Texture cape = textures.get().get(TextureType.CAPE);
        assertNotNull(cape);
        assertEquals("https://example.com/b.png", cape.url());
    }

    @Test
    public void testInactiveCapeProducesNoCapeTexture() {
        MinecraftProfileResponse profile = parseProfile("""
                [{"id": "cape-a", "state": "INACTIVE", "url": "https://example.com/a.png", "alias": "A"}]
                """);

        Optional<Map<TextureType, Texture>> textures = MicrosoftService.getTextures(profile);

        assertTrue(textures.isPresent());
        assertFalse(textures.get().containsKey(TextureType.CAPE));
    }

    @Test
    public void testEmptyCapesProducesNoCapeTexture() {
        MinecraftProfileResponse profile = parseProfile("[]");
        Optional<Map<TextureType, Texture>> textures = MicrosoftService.getTextures(profile);

        assertTrue(textures.isPresent());
        assertFalse(textures.get().containsKey(TextureType.CAPE));
    }
}
