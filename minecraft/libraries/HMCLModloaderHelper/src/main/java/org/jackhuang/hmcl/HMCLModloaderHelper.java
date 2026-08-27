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
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class HMCLModloaderHelper {
    private HMCLModloaderHelper() {
        throw new AssertionError();
    }

    public static void premain(String agentArgs, Instrumentation inst) {
        initAgent(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initAgent(inst);
    }

    private static void initAgent(Instrumentation inst) {
        for (Class<?> loadedClass : inst.getAllLoadedClasses()) {
            try {
                fixProtectionDomain(loadedClass.getProtectionDomain());
            } catch (Throwable ignored) {
            }
        }

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (protectionDomain != null) {
                    fixProtectionDomain(protectionDomain);
                }
                return null;
            }
        }, true);
    }

    private static void fixProtectionDomain(ProtectionDomain pd) {
        if (pd == null) return;
        try {
            CodeSource cs = pd.getCodeSource();
            if (cs != null) {
                fixCodeSource(cs);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void fixCodeSource(CodeSource cs) {
        try {
            URL location = cs.getLocation();
            if (location == null) return;

            URL fixedLocation = cleanUrl(location);
            if (fixedLocation != null && !fixedLocation.equals(location)) {
                Field locationField = CodeSource.class.getDeclaredField("location");
                locationField.setAccessible(true);
                locationField.set(cs, fixedLocation);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static URL cleanUrl(URL url) {
        if (url == null) return null;
        String urlString = url.toString();

        if (urlString.startsWith("jar:") || "jar".equalsIgnoreCase(url.getProtocol())) {
            String inner = urlString.substring(4);
            int bangIndex = inner.indexOf('!');
            if (bangIndex != -1) {
                inner = inner.substring(0, bangIndex);
            }
            try {
                return new URL(inner);
            } catch (MalformedURLException e) {
                if (inner.startsWith("file:")) {
                    inner = inner.substring(5);
                }
                try {
                    return new File(inner).toURI().toURL();
                } catch (MalformedURLException ignored) {
                }
            }
        }

        try {
            URI uri = url.toURI();
            if (uri.isOpaque() || (uri.getScheme() != null && !uri.getScheme().equalsIgnoreCase("file"))) {
                String path = url.getPath();
                if (path != null) {
                    if (path.startsWith("file:")) {
                        path = path.substring(5);
                    }
                    int bangIndex = path.indexOf('!');
                    if (bangIndex != -1) {
                        path = path.substring(0, bangIndex);
                    }
                    return new File(path).toURI().toURL();
                }
            }
        } catch (Exception e) {
            try {
                String path = url.getPath();
                if (path != null) {
                    int bangIndex = path.indexOf('!');
                    if (bangIndex != -1) {
                        path = path.substring(0, bangIndex);
                    }
                    return new File(path).toURI().toURL();
                }
            } catch (Exception ignored) {
            }
        }
        return url;
    }
}
