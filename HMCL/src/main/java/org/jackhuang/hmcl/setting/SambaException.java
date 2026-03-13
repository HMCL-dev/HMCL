/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
*/
package org.jackhuang.hmcl.setting;

public final class SambaException extends RuntimeException {
    public SambaException() {
    }

    public SambaException(String message) {
        super(message);
    }

    public SambaException(String message, Throwable cause) {
        super(message, cause);
    }

    public SambaException(Throwable cause) {
        super(cause);
    }
}
