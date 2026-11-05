// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.ref.Reference;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies project lookup, nullable registry selection and reclaimable indices.
@NotNullByDefault
class AddonProjectInfoRegistryTest {
    /// Creates an isolated registry backed by deterministic test data.
    private static AddonProjectInfoRegistry<ModProjectInfo> registry() {
        return new AddonProjectInfoRegistry<>("/addon/project-info-test.csv", 3) {
            @Override
            protected int getFieldsCount() {
                return 7;
            }

            @Override
            protected ModProjectInfo parseAddonProjectInfo(String[] fields) {
                return new ModProjectInfo(Integer.parseInt(fields[0]), fields[1], fields[2],
                        fields[3], fields[4], fields[5], fields[6]);
            }
        };
    }

    /// Unsupported types have no registry rather than an empty implementation.
    @Test
    void selectsOnlySupportedRegistries() {
        assertSame(AddonProjectInfoRegistry.MOD, AddonProjectInfoRegistry.getByAddonType(RemoteAddon.Type.MOD));
        assertSame(AddonProjectInfoRegistry.MODPACK, AddonProjectInfoRegistry.getByAddonType(RemoteAddon.Type.MODPACK));
        assertNull(AddonProjectInfoRegistry.getByAddonType(null));
        assertNull(AddonProjectInfoRegistry.getByAddonType(RemoteAddon.Type.RESOURCE_PACK));
    }

    /// Name matches require a compatible ID; duplicate IDs keep the first entry.
    @Test
    void preservesLookupPrecedence() {
        var registry = registry();
        var first = registry.getInfos().get(0);
        var second = registry.getInfos().get(1);
        assertSame(first, registry.getInfoByCurseForgeId("shared"));
        assertSame(first, registry.getInfo("shared-id", null));
        assertSame(second, registry.getInfo(null, "Second_Name!"));
        assertSame(first, registry.getInfo("first", "Second Name"));
        assertNull(registry.getInfo("missing", "Second Name"));
        assertNull(registry.getInfo(null, null));
        assertNull(registry.getInfoByCurseForgeId(" "));
    }

    /// Clearing each index reference rebuilds lookups without losing results.
    @Test
    void rebuildsClearedIndices() throws ReflectiveOperationException {
        var registry = registry();
        var first = registry.getInfos().get(0);
        assertSame(first, registry.getInfo("first", "First Name"));
        assertSame(first, registry.getInfo("first", null));
        assertSame(first, registry.getInfoByCurseForgeId("shared"));
        var matches = registry.search("First");
        for (String name : List.of("modIdMapRef", "subnameMapRef", "curseForgeMapRef", "keywordsRef")) {
            var field = AddonProjectInfoRegistry.class.getDeclaredField(name);
            field.setAccessible(true);
            @Nullable Reference<?> reference = (Reference<?>) field.get(registry);
            assertNotNull(reference);
            reference.clear();
        }
        assertSame(first, registry.getInfo("first", "First Name"));
        assertSame(first, registry.getInfo("first", null));
        assertSame(first, registry.getInfoByCurseForgeId("shared"));
        assertEquals(matches, registry.search("First"));
    }

    /// UTF-8 names and escaped commas survive loading; empty searches return no matches.
    @Test
    void readsAndDisplaysNames() {
        var registry = registry();
        var third = registry.getInfos().get(2);
        assertEquals("\u673a\u68b0\u52a8\u529b", third.mainName());
        assertEquals("[EX] \u673a\u68b0\u52a8\u529b (Example, Name)", third.displayName());
        assertEquals(List.of(third), registry.search("\u673a\u68b0\u52a8\u529b"));
        assertTrue(registry.search(" ").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> registry.search("First").clear());
    }
}
