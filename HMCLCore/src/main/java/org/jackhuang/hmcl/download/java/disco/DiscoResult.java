/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.disco;

import com.google.gson.reflect.TypeToken;

import java.util.List;

/**
 * @author Glavo
 */
public final class DiscoResult<T> {

    @SuppressWarnings("unchecked")
    public static <T> TypeToken<DiscoResult<T>> typeOf(Class<T> argType) {
        return (TypeToken<DiscoResult<T>>) TypeToken.getParameterized(DiscoResult.class, argType);
    }

    private final List<T> result;
    private final String message;

    private DiscoResult(List<T> result, String message) {
        this.result = result;
        this.message = message;
    }

    public List<T> getResult() {
        return result;
    }

    public String getMessage() {
        return message;
    }
}
