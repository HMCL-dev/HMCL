// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

public enum TriPreference {

    /// Always perform the action without asking
    ALWAYS,

    /// Skip the action without asking
    NEVER,

    /// Let the user decide whether to perform the action each time the event is triggered
    CONFIRM_EACH_TIME
}
