// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download;

public class VersionMismatchException extends Exception {

    private final String expect, actual;

    public VersionMismatchException(String expect, String actual) {
        super("Mismatched game version requirement, library requires game to be " + expect + ", but actual is " + actual);
        this.expect = expect;
        this.actual = actual;
    }

    public String getExpect() {
        return expect;
    }

    public String getActual() {
        return actual;
    }
}
