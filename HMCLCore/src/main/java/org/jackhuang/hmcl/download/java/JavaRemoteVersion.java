// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java;

/**
 * @author Glavo
 */
public interface JavaRemoteVersion {
    int getJdkVersion();

    String getJavaVersion();

    String getDistributionVersion();
}
