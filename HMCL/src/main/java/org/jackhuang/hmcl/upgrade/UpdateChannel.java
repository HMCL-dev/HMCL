// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.Metadata;

public enum UpdateChannel {
    STABLE("stable"),
    DEVELOPMENT("dev"),
    NIGHTLY("nightly");

    public final String channelName;

    UpdateChannel(String channelName) {
        this.channelName = channelName;
    }

    public static UpdateChannel getChannel() {
        if (Metadata.isDev()) {
            return DEVELOPMENT;
        } else if (Metadata.isNightly()) {
            return NIGHTLY;
        } else {
            return STABLE;
        }
    }
}
