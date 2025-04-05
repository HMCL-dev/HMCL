/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * @author Glavo
 */
public final class ModuleHelper {

    public static void addEnableNativeAccess(Class<?> clazzInModule) {
        Module module = clazzInModule.getModule();
        if (module.isNamed()) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Module.class, MethodHandles.lookup());
                MethodHandle implAddEnableNativeAccess = lookup.findVirtual(Module.class, "implAddEnableNativeAccess", MethodType.methodType(Module.class));
                Module ignored = (Module) implAddEnableNativeAccess.invokeExact(module);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
            }
        } else {
            System.err.println("TODO: Add enable native access for anonymous modules is not yet supported");
        }
    }

    private ModuleHelper() {
    }
}
