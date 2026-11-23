// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNullByDefault;

/// Reports progress for one executing task. Workers may share this reporter
/// until the task body returns. A retry may report a smaller fraction.
@NotNullByDefault
public interface TaskProgress {
    /// Reports a fraction between zero and one. Intermediate updates may be rate-limited.
    /// Zero (start or restart) and one (complete) bypass the rate limit.
    ///
    /// @throws IllegalArgumentException if the fraction is outside the range or is NaN
    void update(double fraction);

    /// Reports completed units. A zero total or a count at or above the total reports completion.
    ///
    /// @throws IllegalArgumentException if either argument is negative
    default void update(long completed, long total) {
        if (completed < 0 || total < 0)
            throw new IllegalArgumentException("Invalid progress: " + completed + "/" + total);
        update(completed < total ? (double) completed / total : 1.0);
    }

    /// Reports a fraction between zero and one without rate limiting.
    ///
    /// @throws IllegalArgumentException if the fraction is outside the range or is NaN
    void updateImmediately(double fraction);
}
