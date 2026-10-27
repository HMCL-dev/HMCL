// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Mark if instances of the class are immutable.
 *
 * @author huangyuhui
 */
@Target(ElementType.TYPE)
public @interface Immutable {
}
