// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java;

import java.util.Set;

/**
 * @author Glavo
 */
public interface JavaDistribution<V extends JavaRemoteVersion> {
    String getDisplayName();

    Set<JavaPackageType> getSupportedPackageTypes();
}
