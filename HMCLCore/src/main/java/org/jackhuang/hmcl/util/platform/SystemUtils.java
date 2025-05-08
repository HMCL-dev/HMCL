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
package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.java.JavaRuntime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class SystemUtils {
    private SystemUtils() {}

    public static int callExternalProcess(String... command) throws IOException, InterruptedException {
        return callExternalProcess(Arrays.asList(command));
    }

    public static int callExternalProcess(List<String> command) throws IOException, InterruptedException {
        return callExternalProcess(new ProcessBuilder(command));
    }

    public static int callExternalProcess(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        ManagedProcess managedProcess = new ManagedProcess(processBuilder);
        managedProcess.pumpInputStream(LOG::info);
        managedProcess.pumpErrorStream(LOG::info);
        return managedProcess.getProcess().waitFor();
    }

    public static Result run(String... command) throws IOException, InterruptedException {
        return run(Arrays.asList(command));
    }

    public static Result run(List<String> command) throws IOException, InterruptedException {
        ArrayList<String> stdout = new ArrayList<>();
        ArrayList<String> stderr = new ArrayList<>();

        ManagedProcess managedProcess = new ManagedProcess(new ProcessBuilder(command));
        managedProcess.pumpInputStream(LOG::info);
        managedProcess.pumpErrorStream(LOG::info);
        int exitCode = managedProcess.getProcess().waitFor();


        return new Result(exitCode, Collections.unmodifiableList(stdout), Collections.unmodifiableList(stderr));
    }

    public static boolean supportJVMAttachment() {
        return JavaRuntime.CURRENT_VERSION >= 9 && Thread.currentThread().getContextClassLoader().getResource("com/sun/tools/attach/VirtualMachine.class") != null;
    }

    public static final class BadExitCodeException extends RuntimeException {
        private final int exitCode;

        public BadExitCodeException(int exitCode) {
            super("Bad exit code: " + exitCode);
            this.exitCode = exitCode;
        }

        public int getExitCode() {
            return exitCode;
        }
    }

    public static final class Result {
        private final int exitCode;
        private final List<String> stdout;
        private final List<String> stderr;

        public Result(int exitCode, List<String> stdout, List<String> stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public List<String> getStdout() {
            return stdout;
        }

        public List<String> getStderr() {
            return stderr;
        }

        public void checkExitCode() {
            if (exitCode != 0) {
                throw new BadExitCodeException(exitCode);
            }
        }
    }
}
