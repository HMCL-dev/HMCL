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
package org.jackhuang.hmcl.task;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;

import static java.util.Objects.requireNonNull;

public class DownloadException extends IOException {

    private final URI uri;

    public DownloadException(URI uri, @NotNull Throwable cause) {
        super("Unable to download " + uri + ", " + cause.getMessage(), requireNonNull(cause));
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }
}
