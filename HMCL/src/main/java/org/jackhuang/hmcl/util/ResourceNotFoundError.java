/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util;

import java.io.InputStream;

/**
 * Suppress the throwable when we make sure the resource cannot miss.
 * @see CrashReporter
 */
public class ResourceNotFoundError extends Error {
    public ResourceNotFoundError(String message) {
        super(message);
    }

    public ResourceNotFoundError(String message, Throwable cause) {
        super(message, cause);
    }

    public static InputStream getResourceAsStream(String url) {
        InputStream stream = ResourceNotFoundError.class.getResourceAsStream(url);
        if (stream == null)
            throw new ResourceNotFoundError("Resource not found: " + url);
        return stream;
    }
}
