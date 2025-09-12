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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;

public final class HMCLMultiMCBootstrap {
    private HMCLMultiMCBootstrap() {
    }

    public static void main(String[] args) throws Throwable {
        String profile = System.getProperty("hmcl.mmc.bootstrap");
        if (profile == null) {
            launchLegacy(args);
            return;
        }

        URI uri = URI.create(profile);
        if (Objects.equals(uri.getPath(), "/bootstrap_profile_v1/")) {
            launchV1(parseQuery(uri.getRawQuery()), args);
        }
    }

    private static void launchV1(Map<String, String> arguments, String[] args) throws Throwable {
        String mainClass = arguments.get("main_class");
        String installerInfo = arguments.get("installer");

        launch(installerInfo, mainClass, args);
    }

    private static void launchLegacy(String[] args) throws Throwable {
        String mainClass = new String(Base64.getUrlDecoder().decode(System.getProperty("hmcl.mmc.bootstrap.main")), StandardCharsets.UTF_8);
        String installerInfo = new String(Base64.getUrlDecoder().decode(System.getProperty("hmcl.mmc.bootstrap.installer")), StandardCharsets.UTF_8);

        launch(installerInfo, mainClass, args);
    }

    private static void launch(String installerInfo, String mainClass, String[] args) throws Throwable {
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

    private static Map<String, String> parseQuery(String queryParameterString) {
        if (queryParameterString == null) return Collections.emptyMap();

        Map<String, String> result = new HashMap<>();

        try (Scanner scanner = new Scanner(queryParameterString)) {
            scanner.useDelimiter("&");
            while (scanner.hasNext()) {
                String[] nameValue = scanner.next().split("=");
                if (nameValue.length == 0 || nameValue.length > 2) {
                    throw new IllegalArgumentException("bad query string");
                }

                String name = decodeURL(nameValue[0]);
                String value = nameValue.length == 2 ? decodeURL(nameValue[1]) : null;
                result.put(name, value);
            }
        }
        return result;
    }

    private static String decodeURL(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
}
