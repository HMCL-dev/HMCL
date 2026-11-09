// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.gson;

import java.lang.annotation.*;

/// Marks a class as being serialized/deserialized by Gson.
/// This annotation is not retained at runtime and only serves as a reminder to developers not to change field names arbitrarily.
///
/// TODO: Add this annotation to all classes in the HMCL project that need to be serialized/deserialized.
///
/// @author Glavo
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface JsonSerializable {
}
