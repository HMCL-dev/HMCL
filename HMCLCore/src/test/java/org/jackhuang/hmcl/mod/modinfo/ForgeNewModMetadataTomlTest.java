/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 HMCL contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jackhuang.hmcl.mod.modinfo;

import com.moandjiezana.toml.Toml;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for GitHub issue: mixed quoted / unquoted keys in neoforge.mods.toml dependencies.
 */
public final class ForgeNewModMetadataTomlTest {

    private static String invokePreprocessToml(String content) throws Exception {
        Method m = ForgeNewModMetadata.class.getDeclaredMethod("preprocessToml", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, content);
    }

    @Test
    public void preprocessMustNotCorruptInlineArrays() throws Exception {
        String in = "[mc-publish]\n    loaders=[\"neoforge\"]\n    game-versions=[\"1.21.1\"]\n";
        String out = invokePreprocessToml(in);
        assertTrue(out.contains("loaders=[\"neoforge\"]"), () -> "broken output: " + out);
        assertTrue(out.contains("game-versions=[\"1.21.1\"]"), () -> "broken output: " + out);
    }

    @Test
    public void preprocessNormalizesQuotedTableKeyInHeader() throws Exception {
        String in = "[[dependencies.neoecoae]]\n[dependencies.\"neoecoae\".mc-publish]\n";
        String out = invokePreprocessToml(in);
        assertTrue(out.contains("[[dependencies.neoecoae]]"));
        assertTrue(out.contains("[dependencies.neoecoae.mc-publish]"));
        assertFalse(out.contains("\"neoecoae\""));
    }

    @Test
    public void mixedDependencyQuotesAndMcPublishParses() throws Exception {
        String toml = """
                modLoader="javafml"
                loaderVersion="[4,)"
                license="GPLv3"

                [[mods]]
                modId="neoecoae"
                version="1.0.0"
                displayName="Neo ECO AE Extension"
                authors="A, B"
                description='''d'''

                [[dependencies.neoecoae]]
                modId="neoforge"
                type="required"
                versionRange="[21.1.0,)"
                ordering="NONE"
                side="BOTH"

                [[dependencies.neoecoae]]
                modId="ae2"
                type="required"
                versionRange="[1,)"
                ordering="NONE"
                side="BOTH"
                    [dependencies."neoecoae".mc-publish]
                        modrinth="ae2"

                [mc-publish]
                modrinth="x"
                curseforge=1
                loaders=["neoforge"]
                game-versions=["1.21.1"]
                """;
        String preprocessed = invokePreprocessToml(toml);
        Toml parsed = new Toml().read(preprocessed);
        ForgeNewModMetadata meta = parsed.to(ForgeNewModMetadata.class);
        assertNotNull(meta);
        assertFalse(meta.getMods().isEmpty());
        assertEquals("neoecoae", meta.getMods().get(0).getModId());
        assertEquals("Neo ECO AE Extension", meta.getMods().get(0).getDisplayName());
        assertEquals("A, B", meta.getMods().get(0).getAuthors());
    }
}
