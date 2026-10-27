// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.function;

import java.util.concurrent.Callable;

/**
 *
 * @author huangyuhui
 */
public interface ExceptionalSupplier<R, E extends Exception> {
    R get() throws E;
    
    default Callable<R> toCallable() {
        return this::get;
    }
}
