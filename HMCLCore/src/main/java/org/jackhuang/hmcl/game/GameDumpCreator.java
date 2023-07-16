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
            sun.tools.attach.HotSpotVirtualMachine vm = attachVM(pid, writer);
            if (vm == null)
                return;

            try {
                writeDumpHeadTo(vm, writer);
                writer.write("====================\n");
                writeDumpBodyTo(vm, writer);
            } finally {
                vm.detach();
            }

            for (int i = 1; i < DUMP_TIME; i++) {
                Thread.sleep(3000);
                writer.write("====================\n");

                vm = attachVM(pid, writer);
                if (vm != null) {
                    try {
                        writeDumpBodyTo(vm, writer);
                    } finally {
                        vm.detach();
                    }
                }
            }
        }
    }

    private static void writeDumpHeadTo(sun.tools.attach.HotSpotVirtualMachine vm, Writer writer) throws IOException {
        writer.write("===== Minecraft JStack Dump =====\n");

        writer.write("Tool Version: " + TOOL_VERSION + "\n");
        writer.write("VM PID: " + vm.id() + "\n");

        StringBuilder builder = new StringBuilder(1024);
        if (runJcmd(vm, "VM.command_line", builder)) {
            writer.write(ACCESS_TOKEN_HIDER.matcher(builder).replaceAll("--accessToken <access token>"));
        } else {
            writer.write("VM Arguments:\n");
            writer.append(builder);
        }
        writer.write('\n');

        builder.setLength(0);
        runJcmd(vm, "VM.version", builder);
        writer.write("VM Version:\n");
        writer.append(builder);
        writer.write('\n');
    }

    private static void writeDumpBodyTo(sun.tools.attach.HotSpotVirtualMachine vm, Writer writer) throws IOException {
        runJcmd(vm, "Thread.print", writer);
    }

    private static sun.tools.attach.HotSpotVirtualMachine attachVM(long lvmid, Writer writer) throws IOException {
        try {
            return (sun.tools.attach.HotSpotVirtualMachine) VirtualMachine.attach(String.valueOf(lvmid));
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "An exception encountered while attaching vm " + lvmid, e);
            writer.write(StringUtils.getStackTrace(e));
            writer.write('\n');
            return null;
        }
    }

    private static boolean runJcmd(sun.tools.attach.HotSpotVirtualMachine vm, String command, Appendable target) throws IOException {
        try (Reader reader = new InputStreamReader(vm.executeJCmd(command), OperatingSystem.NATIVE_CHARSET)) {
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
