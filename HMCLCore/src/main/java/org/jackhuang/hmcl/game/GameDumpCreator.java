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

package org.jackhuang.hmcl.game;

import com.sun.tools.attach.VirtualMachine;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class GameDumpCreator {
    private static final Pattern ACCESS_TOKEN_HIDER = Pattern.compile("--accessToken \\S+");
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\R");

    private GameDumpCreator() {
    }

    private static final int TOOL_VERSION = 8;

    private static final int DUMP_TIME = 3;

    public static boolean checkDependencies() {
        return JavaVersion.CURRENT_JAVA.getParsedVersion() >= 9
                && Thread.currentThread().getContextClassLoader().getResource("com/sun/tools/attach/VirtualMachine.class") != null;
    }

    public static void writeDumpTo(long pid, Path path) throws IOException, InterruptedException, ClassNotFoundException {
        if (!checkDependencies()) {
            throw new ClassNotFoundException("com.sun.tools.attach.VirtualMachine");
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            VirtualMachine vm = attachVM(pid, writer);
            if (vm == null)
                return;

            try {
                writeDumpHeadTo(vm, writer);

                for (int i = 0; i < DUMP_TIME; i++) {
                    if (i > 0)
                        Thread.sleep(3000);

                    writer.write("====================\n");
                    writeDumpBodyTo(vm, writer);
                }
            } finally {
                vm.detach();
            }
        }
    }

    private static void writeDumpHeadTo(VirtualMachine vm, Writer writer) throws IOException {
        writer.write("===== Minecraft JStack Dump =====\n");

        writeDumpHeadKeyValueTo("Tool Version", String.valueOf(TOOL_VERSION), writer);
        writeDumpHeadKeyValueTo("VM PID", vm.id(), writer);

        StringBuilder stringBuilder = new StringBuilder();
        {
            runJcmd(vm, "VM.command_line", stringBuilder);
            writeDumpHeadKeyValueTo("VM Command Line",
                    ACCESS_TOKEN_HIDER.matcher(stringBuilder).replaceAll("--accessToken <access token>"),
                    writer);
        }
        {
            stringBuilder.setLength(0);
            runJcmd(vm, "VM.version", stringBuilder);
            writeDumpHeadKeyValueTo("VM Version", stringBuilder.toString(), writer);
        }

        writer.write("\n\n");
    }

    public static void writeDumpHeadKeyValueTo(String key, String value, Writer writer) throws IOException {
        writer.write(key);
        writer.write(": ");

        if (value.indexOf('\n') >= 0) {
            // Multiple Line Value
            writer.write('{');
            writer.write('\n');

            String[] lines = LINE_SEPARATOR.split(value);

            for (String line : lines) {
                writer.write("    ");
                writer.write(line);
                writer.write('\n');
            }

            writer.write('}');
        } else {
            // Single Line Value
            writer.write(value);
        }
        writer.write('\n');
    }

    private static void writeDumpBodyTo(VirtualMachine vm, Writer writer) throws IOException {
        runJcmd(vm, "Thread.print", writer);
    }

    private static VirtualMachine attachVM(long pid, Writer writer) throws IOException {
        for (int i = 0; i < 3; i++) {
            VirtualMachine vm;
            try {
                vm = VirtualMachine.attach(String.valueOf(pid));
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "An exception encountered while attaching vm " + pid, e);
                writer.write(StringUtils.getStackTrace(e));
                writer.write('\n');
                continue;
            }

            if (vm instanceof sun.tools.attach.HotSpotVirtualMachine) {
                return vm;
            } else {
                String message = "Unsupported VM type " + vm.getClass();
                LOG.log(Level.WARNING, message);
                writer.write(message);
                writer.write('\n');
                return null;
            }
        }

        return null;
    }

    private static boolean runJcmd(VirtualMachine vm, String command, Appendable target) throws IOException {
        assert vm instanceof sun.tools.attach.HotSpotVirtualMachine;

        try (Reader reader = new InputStreamReader(
                ((sun.tools.attach.HotSpotVirtualMachine) vm).executeJCmd(command), OperatingSystem.NATIVE_CHARSET)) {
            CharBuffer dataCache = CharBuffer.allocate(256);
            while (reader.read(dataCache) > 0) {
                dataCache.flip();
                target.append(dataCache);
                dataCache.clear();
            }
            return true;
        } catch (Throwable throwable) {
            LOG.log(Level.WARNING, "An exception encountered while running jcmd " + vm.id(), throwable);
            target.append(StringUtils.getStackTrace(throwable));
            target.append('\n');
        }
        return false;
    }
}
