// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

import java.io.IOException;

public class ArtifactMalformedException extends IOException {
    public ArtifactMalformedException(String message) {
        super(message);
    }

    public ArtifactMalformedException(String message, Throwable cause) {
        super(message, cause);
    }
}

