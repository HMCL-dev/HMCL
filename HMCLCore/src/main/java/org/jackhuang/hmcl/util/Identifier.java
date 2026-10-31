// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

public record Identifier(String namespace, String path) {

    public static final String DEFAULT_NAMESPACE = "minecraft";
    public static final char SEPARATOR = ':';

    public Identifier {
        assertValidNamespace(namespace, path);
        assertValidPath(namespace, path);
    }

    @Override
    public @NotNull String toString() {
        return namespace + SEPARATOR + path;
    }

    public static Identifier of(String identifier) {
        int separatorIndex = identifier.indexOf(SEPARATOR);
        if (separatorIndex >= 0) {
            String path = identifier.substring(separatorIndex + 1);
            if (separatorIndex != 0) {
                String namespace = identifier.substring(0, separatorIndex);
                return new Identifier(namespace, path);
            } else {
                return withDefaultNamespace(path);
            }
        } else {
            return withDefaultNamespace(identifier);
        }
    }

    public static Identifier withDefaultNamespace(String path) {
        return new Identifier(DEFAULT_NAMESPACE, path);
    }

    public static boolean isValid(String identifier) {
        int separatorIndex = identifier.indexOf(SEPARATOR);
        if (separatorIndex >= 0) {
            return isValidPath(identifier.substring(separatorIndex + 1))
                    && (separatorIndex == 0 || isValidNamespace(identifier.substring(0, separatorIndex)));
        } else {
            return isValidPath(identifier);
        }
    }

    public static boolean isValidPath(String path) {
        for (int i = 0; i < path.length(); i++) {
            if (!validPathChar(path.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidNamespace(String namespace) {
        if (namespace.equals("..")) {
            return false;
        }

        for (int i = 0; i < namespace.length(); i++) {
            if (!validNamespaceChar(namespace.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean validPathChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.';
    }

    public static boolean validNamespaceChar(char c) {
        return c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.';
    }

    private static void assertValidPath(String namespace, String path) {
        if (!isValidPath(path))
            throw new IllegalArgumentException("Non [a-z0-9/._-] character in path of location: " + namespace + ":" + path);
    }

    private static void assertValidNamespace(String namespace, String path) {
        if (!isValidNamespace(namespace))
            throw new IllegalArgumentException("Non [a-z0-9_.-] character in namespace of identifier: " + namespace + ":" + path);
    }
}
