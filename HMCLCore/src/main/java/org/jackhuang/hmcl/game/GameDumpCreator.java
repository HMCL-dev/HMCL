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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class GameDumpCreator {
    private static final Pattern ACCESS_TOKEN_HIDER = Pattern.compile("--accessToken [0-9a-f]*");

    private GameDumpCreator() {
    }

    private static final class DumpHead {
        private final Map<String, String> infos = new LinkedHashMap<>();

        public void push(String key, String value) {
            infos.put(key, value);
        }

        public void writeTo(PrintWriter printWriter) {
            printWriter.write("===== Minecraft JStack Dump =====\n");
            for (Map.Entry<String, String> entry : infos.entrySet()) {
                printWriter.write(entry.getKey());
                printWriter.write(": ");

                if (entry.getValue().contains("\n")) {
                    // Multiple Line Value
                    printWriter.write('{');
                    printWriter.write('\n');

                    String[] lines = entry.getValue().split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        if (i != lines.length - 1) {
                            printWriter.write("    ");
                            printWriter.write(lines[i]);
                            printWriter.write('\n');
                        } else {
                            // Last line
                            if (lines[i].length() == 0) {
                                // An empty last Line
                                printWriter.write('}');
                            } else {
                                // Not an empty last lien
                                printWriter.write("    ");
                                printWriter.write(lines[i]);
                                printWriter.write('\n');
                                printWriter.write('}');
                            }
                        }
                    }
                } else {
                    // Single Line Value
                    printWriter.write(entry.getValue());
                }
                printWriter.write('\n');
            }
            printWriter.write('\n');
            printWriter.write('\n');
        }
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

        try (PrintWriter printWriter = new PrintWriter(Files.newBufferedWriter(path), false)) {
            writeDumpHeadTo(pid, printWriter);

            for (int i = 0; i < DUMP_TIME; i++) {
                writeDumpBodyTo(pid, printWriter);
                printWriter.write("====================\n");

                if (i < DUMP_TIME - 1) {
                    Thread.sleep(3000);
                }
            }
        }
    }

    private static void writeDumpHeadTo(long lvmid, PrintWriter printWriter) throws IOException {
        DumpHead dumpHead = new DumpHead();
        dumpHead.push("Tool Version", String.valueOf(TOOL_VERSION));
        dumpHead.push("VM PID", String.valueOf(lvmid));
        {
            StringBuilder stringBuilder = new StringBuilder();
            attachVM(lvmid, "VM.command_line", stringBuilder);
            dumpHead.push("VM Command Line", ACCESS_TOKEN_HIDER.matcher(stringBuilder).replaceAll("--accessToken <access token>"));
        }
        {
            StringBuilder stringBuilder = new StringBuilder();
            attachVM(lvmid, "VM.version", stringBuilder);
            dumpHead.push("VM Version", stringBuilder.toString());
        }

        dumpHead.writeTo(printWriter);
    }

    private static void writeDumpBodyTo(long lvmid, PrintWriter printWriter) throws IOException {
        attachVM(lvmid, "Thread.print", printWriter);
    }

    private static void attachVM(long lvmid, String command, Appendable appendable) throws IOException {
        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(lvmid));

            try (InputStreamReader inputStreamReader = new InputStreamReader(new BufferedInputStream(((sun.tools.attach.HotSpotVirtualMachine) vm).executeJCmd(command)))) {
                char[] dataCache = new char[256];
                int status;

                do {
                    status = inputStreamReader.read(dataCache);

                    if (status > 0) {
                        appendable.append(CharBuffer.wrap(dataCache, 0, status));
                    }
                } while (status > 0);
            } finally {
                vm.detach();
            }
        } catch (Throwable throwable) {
            LOG.log(Level.WARNING, String.format("An Exception happened while attaching vm %d", lvmid), throwable);
            appendable.append('\n');
            appendable.append(StringUtils.getStackTrace(throwable));
            appendable.append('\n');
        }
    }
}
