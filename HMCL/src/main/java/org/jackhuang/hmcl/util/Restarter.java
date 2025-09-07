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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.JarUtils;

/**
 * <p>A common restart tool class, used for:</p>
 * <ul>
 *   <li>Restart after update</li>
 *   <li>Restart after switching interface language</li>
 *   <li>Any scenario that requires a restart to take effect</li>
 * </ul>
 */
public final class Restarter {

    private final List<String> jvmOptions = new ArrayList<>();
    private final List<String> programArgs = new ArrayList<>();
    private Path jarPath;

    private Restarter() { }

    public static Restarter builder() {
        return new Restarter();
    }

    public Restarter addSystemProperty(String key, String value) {
        jvmOptions.add("-D" + key + "=" + value);
        return this;
    }

    public Restarter addProgramArguments(List<String> args) {
        programArgs.addAll(args);
        return this;
    }

    /**
     * <p>
     * Set the JAR path to start
     * If not, the current running JAR will be used by default
     * </p>
     */
    public Restarter setJarPath(Path jarPath) {
        this.jarPath = jarPath;
        return this;
    }

    public void restart() throws IOException {
        Path jar = this.jarPath;
        if (jar == null) {
            jar = JarUtils.thisJarPath();
        }
        if (jar == null) {
            throw new IOException("Cannot locate JAR file");
        }

        List<String> command = new ArrayList<>();
        command.add(JavaRuntime.getDefault().getBinary().toString());

        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            Object k = e.getKey();
            if (k instanceof String && ((String) k).startsWith("hmcl.")) {
                command.add("-D" + k + "=" + e.getValue());
            }
        }

        command.addAll(jvmOptions);

        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());

        command.addAll(programArgs);

        new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .inheritIO()
                .start();

        EntryPoint.exit(0);
    }
}
