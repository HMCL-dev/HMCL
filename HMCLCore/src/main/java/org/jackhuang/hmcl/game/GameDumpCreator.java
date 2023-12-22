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

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class GameDumpCreator {
    private static final Pattern ACCESS_TOKEN_HIDER = Pattern.compile("--accessToken \\S+");

    private GameDumpCreator() {
    }

    private static final int TOOL_VERSION = 9;

    private static final int DUMP_TIME = 3;

    private static final int RETRY_TIME = 3;

    public static boolean checkDependencies() {
        return JavaVersion.CURRENT_JAVA.getParsedVersion() >= 9
                && Thread.currentThread().getContextClassLoader().getResource("com/sun/tools/attach/VirtualMachine.class") != null;
    }

    public static void writeDumpTo(long pid, Path path) throws IOException, InterruptedException, ClassNotFoundException {
        if (!checkDependencies()) {
            throw new ClassNotFoundException("com.sun.tools.attach.VirtualMachine");
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            // On local machine, the lvmid and the pid are the same.
            VirtualMachine vm = attachVM(String.valueOf(pid), writer);

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

        writeDumpHeadKeyValueTo("Tool Version", String.valueOf(TOOL_VERSION), writer, false);
        writeDumpHeadKeyValueTo("VM PID", vm.id(), writer, false);

        StringBuilder stringBuilder = new StringBuilder();
        {
            executeJCmd(vm, "VM.command_line", stringBuilder);
            writeDumpHeadKeyValueTo(
                    "VM Command Line",
                    ACCESS_TOKEN_HIDER.matcher(stringBuilder).replaceAll("--accessToken <access token>"),
                    writer,
                    true
            );
        }
        {
            stringBuilder.setLength(0);
            executeJCmd(vm, "VM.version", stringBuilder);
            writeDumpHeadKeyValueTo("VM Version", stringBuilder.toString(), writer, true);
        }

        writer.write("\n\n");
    }

    public static void writeDumpHeadKeyValueTo(String key, String value, Writer writer, boolean multiline) throws IOException {
        writer.write(key);
        writer.write(": ");

        if (multiline) {
            // Multiple Line Value
            writer.write('{');
            writer.write('\n');

            int lineStart = 0;
            int lineEnd = value.indexOf("\n", lineStart);

            while (true) {
                if (lineEnd == -1) {
                    if (lineStart < value.length()) {
                        writer.write("    ");
                        writer.write(value, lineStart, value.length() - lineStart);
                        writer.write('\n');
                    }
                    break;
                } else {
                    writer.write("    ");
                    writer.write(value, lineStart, lineEnd - lineStart);
                    writer.write('\n');
                    lineStart = lineEnd + 1;
                    lineEnd = value.indexOf("\n", lineStart);
                }
            }

            writer.write('}');
        } else {
            // Single Line Value
            writer.write(value);
        }
        writer.write('\n');
    }

    private static void writeDumpBodyTo(VirtualMachine vm, Writer writer) throws IOException {
        executeJCmd(vm, "Thread.print", writer);
    }

    private static VirtualMachine attachVM(String lvmid, Writer writer) throws IOException {
        for (int i = 0; i < RETRY_TIME; i++) {
            try {
                return VirtualMachine.attach(lvmid);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "An exception encountered while attaching vm " + lvmid, e);
                writer.write(StringUtils.getStackTrace(e));
                writer.write('\n');
            }
        }

        String message = "Cannot attach VM " + lvmid;
        writer.write(message);
        throw new IOException(message);
    }

    private static void executeJCmd(VirtualMachine vm, String command, Appendable target) throws IOException {
        try (Reader reader = new InputStreamReader(executeJCmdNative(vm, command), OperatingSystem.NATIVE_CHARSET)) {
            CharBuffer dataCache = CharBuffer.allocate(256);
            while (reader.read(dataCache) > 0) {
                dataCache.flip();
                target.append(dataCache);
                dataCache.clear();
            }
        } catch (Throwable throwable) {
            LOG.log(Level.WARNING, "An exception encountered while executing jcmd " + vm.id(), throwable);
            target.append(StringUtils.getStackTrace(throwable));
            target.append('\n');
        }
    }

    private static InputStream executeJCmdNative(VirtualMachine vm, String command) throws IOException, AttachNotSupportedException {
        if (vm instanceof sun.tools.attach.HotSpotVirtualMachine) {
            return ((sun.tools.attach.HotSpotVirtualMachine) vm).executeJCmd(command);
        } else {
            throw new AttachNotSupportedException("Unsupported VM implementation " + vm.getClass().getName());
        }
    }
}
