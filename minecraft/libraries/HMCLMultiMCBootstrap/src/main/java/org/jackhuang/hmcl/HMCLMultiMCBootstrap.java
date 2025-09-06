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
package org.jackhuang.hmcl;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class HMCLMultiMCBootstrap {
    private HMCLMultiMCBootstrap() {
    }

    public static void main(String[] args) throws Exception {
        String mainClass = read("hmcl.mmc.bootstrap.main");
        String installerInfo = read("hmcl.mmc.bootstrap.installer");

        System.out.println("This version is installed by HMCLCore's MultiMC combat layer.");
        System.out.println("Installer Properties:");
        System.out.println(installerInfo);
        System.out.println("Main Class: " + mainClass);
        System.out.println("GAME MAY CRASH DUE TO BUGS. TEST YOUR GAME ON OFFICIAL MMC BEFORE REPORTING BUGS TO AUTHORS.");

        Method[] methods = Class.forName(mainClass).getMethods();
        for (Method method : methods) {
            // https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.1.4
            if ("main".equals(method.getName()) &&
                    Modifier.isStatic(method.getModifiers()) &&
                    method.getReturnType() == void.class &&
                    method.getParameterCount() == 1 &&
                    method.getParameters()[0].getType() == String[].class
            ) {
                method.invoke(null, (Object) args);
                return;
            }
        }

        throw new IllegalArgumentException("Cannot find method 'main(String[])' in " + mainClass);
    }

    private static String read(String key) {
        return new String(Base64.getUrlDecoder().decode(System.getProperty(key)), StandardCharsets.UTF_8);
    }
}
