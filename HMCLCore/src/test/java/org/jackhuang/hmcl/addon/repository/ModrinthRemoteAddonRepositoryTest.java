// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.repository;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// Tests Modrinth dependency metadata conversion without network access.
public final class ModrinthRemoteAddonRepositoryTest {

    /// Preserves exact version requirements, including dependencies without a project ID.
    @Test
    public void preservesDependencyVersionRequirements() {
        var projectVersion = new ModrinthRemoteAddonRepository.ProjectVersion(
                "Root", "1.0.0", "",
                List.of(
                        new ModrinthRemoteAddonRepository.Dependency("required-version", "required-project", "required"),
                        new ModrinthRemoteAddonRepository.Dependency("version-only", null, "required")),
                List.of("1.20.1"), "release", List.of("forge"), false,
                "root-version", "root-project", "author", Instant.EPOCH, 0, "",
                List.of(new ModrinthRemoteAddonRepository.ProjectVersionFile(
                        Map.of(), "https://example.invalid/root.jar", "root.jar", true, 1)));

        List<RemoteAddon.Dependency> dependencies = projectVersion.toVersion().orElseThrow().dependencies();
        assertEquals("required-project", dependencies.get(0).getId());
        assertEquals("required-version", dependencies.get(0).getVersionId());
        assertNull(dependencies.get(1).getId());
        assertEquals("version-only", dependencies.get(1).getVersionId());
    }
}
