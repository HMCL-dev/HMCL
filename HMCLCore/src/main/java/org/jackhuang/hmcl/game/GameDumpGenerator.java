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
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Generate a JVM dump on a process.
 * WARNING: Initializing this class may cause NoClassDefFoundError.
 */
public final class GameDumpGenerator {
    private GameDumpGenerator() {
    }

    private static final int TOOL_VERSION = 9;

    private static final int DUMP_TIME = 3;

    private static final int RETRY_TIME = 3;

    public static void writeDumpTo(long pid, Path path) throws IOException, InterruptedException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            // On a local machine, the lvmid and the pid are the same.
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
            execute(vm, "VM.command_line", stringBuilder);
            writeDumpHeadKeyValueTo(
                    "VM Command Line",
                    Logger.filterForbiddenToken(stringBuilder.toString()),
                    writer,
                    true
            );
        }
        {
            stringBuilder.setLength(0);
            execute(vm, "VM.version", stringBuilder);
            writeDumpHeadKeyValueTo("VM Version", stringBuilder.toString(), writer, true);
        }

        writer.write("\n\n");
    }

    public static void writeDumpHeadKeyValueTo(String key, String value, Writer writer, boolean multiline) throws IOException {
        writer.write(key);
        writer.write(':');
        writer.write(' ');

        if (multiline) {
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
            writer.write(value);
        }
        writer.write('\n');
    }

    private static void writeDumpBodyTo(VirtualMachine vm, Writer writer) throws IOException {
        execute(vm, "Thread.print -e -l", writer);
    }

    private static VirtualMachine attachVM(String lvmid, Writer writer) throws IOException, InterruptedException {
        for (int i = 0; i < RETRY_TIME; i++) {
            try {
                return VirtualMachine.attach(lvmid);
            } catch (Throwable e) {
                LOG.warning("An exception encountered while attaching vm " + lvmid, e);
                writer.write(StringUtils.getStackTrace(e));
                writer.write('\n');
                Thread.sleep(3000);
            }
        }

        String message = "Cannot attach VM " + lvmid;
        writer.write(message);
        throw new IOException(message);
    }

    private static void execute(VirtualMachine vm, String command, Appendable target) throws IOException {
        try (Reader reader = new InputStreamReader(executeJVMCommand(vm, command), OperatingSystem.NATIVE_CHARSET)) {
            char[] data = new char[256];
            CharBuffer cb = CharBuffer.wrap(data);
            int len;
            while ((len = reader.read(data)) > 0) { // Directly read the data into a CharBuffer would cause useless array copy actions.
                target.append(cb, 0, len);
            }
        } catch (Throwable throwable) {
            LOG.warning("An exception encountered while executing jcmd " + vm.id(), throwable);
            target.append(StringUtils.getStackTrace(throwable));
            target.append('\n');
        }
    }

    private static InputStream executeJVMCommand(VirtualMachine vm, String command) throws IOException, AttachNotSupportedException {
        if (vm instanceof sun.tools.attach.HotSpotVirtualMachine) {
            return ((sun.tools.attach.HotSpotVirtualMachine) vm).executeJCmd(command);
        } else {
            throw new AttachNotSupportedException("Unsupported VM implementation " + vm.getClass().getName());
        }
    }
}
