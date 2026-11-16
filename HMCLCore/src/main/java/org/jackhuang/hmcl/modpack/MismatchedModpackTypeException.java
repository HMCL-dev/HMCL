// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

public class MismatchedModpackTypeException extends Exception {
    private final String required;
    private final String found;

    public MismatchedModpackTypeException(String required, String found) {
        super("Required " + required + ", but found " + found);

        this.required = required;
        this.found = found;
    }

    public String getRequired() {
        return required;
    }

    public String getFound() {
        return found;
    }
}
