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

import org.jackhuang.hmcl.EntryPoint;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.setLocale;

/**
 * 通用重启工具类，用于：
 *   - 更新后重启
 *   - 切换界面语言后重启
 *   - 任何"必须重新启动才能生效"的场景
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
     * 设置要启动的JAR路径
     * 如果不设置，则使用当前运行的JAR
     */
    public Restarter setJarPath(Path jarPath) {
        this.jarPath = jarPath;
        return this;
    }

    /**
     * 立即重启
     */
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

        // 额外JVM选项
        command.addAll(jvmOptions);

        command.add("-jar");
        command.add(jar.toAbsolutePath().toString());

        // 程序参数
        command.addAll(programArgs);

        new ProcessBuilder(command)
                .directory(new File(System.getProperty("user.dir")))
                .inheritIO()
                .start();

        EntryPoint.exit(0);
    }

    /**
     * 立刻用完全相同的环境重启
     */
    public static void restartWithSameArgs() throws IOException {
        builder().restart();
    }

    /**
     * 切换界面语言后重启
     */
    public static void restartWithLocale(Locales.SupportedLocale locale) throws IOException {
        setLocale(locale);
        builder().restart();
    }

}