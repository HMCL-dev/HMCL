// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNullByDefault;

/// Persisted garbage collector preferences; recommendation ranges in the UI are not compatibility limits.
@NotNullByDefault
public enum GcStrategy {
    /// Preserves HMCL's existing G1 collector and additional tuning parameters.
    TUNED_G1,
    /// Selects G1 without additional collector tuning.
    G1,
    /// Prefers generational ZGC, then non-generational ZGC; unsupported runtimes stop the launch.
    /// The former generational-only preference is read as the unified strategy when loading older settings.
    @SerializedName(value = "ZGC", alternate = {"GENERATIONAL_ZGC"})
    ZGC,
    /// Prefers production generational Shenandoah, then regular Shenandoah; no other collector is substituted.
    SHENANDOAH,
    /// Leaves collector selection and tuning to Java or user-supplied arguments.
    DEFAULT
}
