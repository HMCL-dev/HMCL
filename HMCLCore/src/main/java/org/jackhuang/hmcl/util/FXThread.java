// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.lang.annotation.*;

/**
 * Mark a method that can only be called on the JavaFX Application Thread,
 * or mark a field that can only be read/modified on the JavaFX Application Thread.
 *
 * @author Glavo
 */
@Documented
@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
public @interface FXThread {
}
