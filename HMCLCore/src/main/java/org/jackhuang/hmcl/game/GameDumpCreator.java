package org.jackhuang.hmcl.game;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jackhuang.hmcl.util.platform.CurrentJava;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class GameDumpCreator {
    private static class DumpHead {
        private final Map<String, String> infos = new LinkedHashMap<>();

        private static final byte[] head = "----- Minecraft JStack Dump -----\n".getBytes(StandardCharsets.UTF_8);

        private static final byte[] keyValueSinglelineSplit = ": ".getBytes(StandardCharsets.UTF_8);

        private static final byte[] multipleLinePrefix = "    ".getBytes(StandardCharsets.UTF_8);

        public String push(String key, String value) {
            return infos.put(key, value);
        }

        public void writeTo(OutputStream outputStream) throws IOException {
            outputStream.write(head);
            for (Map.Entry<String, String> entry : infos.entrySet()) {
                outputStream.write(entry.getKey().getBytes(StandardCharsets.UTF_8));
                outputStream.write(keyValueSinglelineSplit);

                if (entry.getValue().contains("\n")) {
                    // Multiple Line Value
                    outputStream.write('{');
                    outputStream.write('\n');

                    String[] lines = entry.getValue().split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        if (i != lines.length - 1) {
                            outputStream.write(multipleLinePrefix);
                            outputStream.write(lines[i].getBytes(StandardCharsets.UTF_8));
                            outputStream.write('\n');
                        } else {
                            // Last line
                            if (lines[i].length() == 0) {
                                // An empty last Line
                                outputStream.write('}');
                            } else {
                                // Not an empty last lien
                                outputStream.write(multipleLinePrefix);
                                outputStream.write(lines[i].getBytes(StandardCharsets.UTF_8));
                                outputStream.write('\n');
                                outputStream.write('}');
                            }
                        }
                    }
                } else {
                    // Single Line Value
                    outputStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                }
                outputStream.write('\n');
            }
            outputStream.write('\n');
            outputStream.write('\n');
        }
    }

    private static final int TOOL_VERSION = 8;

    private static final int DUMP_TIME = 3;

    private static final byte[] spritLine = new byte[22];

    static {
        Arrays.fill(spritLine, (byte) '-');
        Arrays.fill(spritLine, 20, spritLine.length, (byte) '\n');
    }

    public static boolean checkDependencies() {
        if (!CurrentJava.checkToolPackageDepdencies()) {
            // Check whether tools.jar is available.
            return false;
        }
        if (JavaVersion.CURRENT_JAVA.getParsedVersion() >= 9) {
            // Method Process.pid() is provided on Java 9 or later.
            // All the Operating System is accepted.
            return true;
        }
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            // On Windows, there is no ways to get the pid before Java 9 (We can only get the handle field of a Process).
            return false;
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX || OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            // On Linux or Mac, we can get field UnixProcess.pid field to get the pid even before Java 9.
            return true;
        }
        // Unknown Operating System, no fallback available.
        return false;
    }

    public static void writeDumpTo(long pid, File file) {
        if (!checkDependencies()) {
            throw new UnsupportedOperationException(new ClassNotFoundException("com.sun.tools.attach.VirtualMachine"));
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            writeHeadDumpTo(pid, fileOutputStream);

            for (int i = 0; i < DUMP_TIME; i++) {
                writeDataDumpTo(pid, fileOutputStream);
                fileOutputStream.write(spritLine);

                if (i < DUMP_TIME - 1) {
                    Thread.sleep(3000);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeHeadDumpTo(long pid, OutputStream outputStream) throws IOException {
        DumpHead dumpHead = new DumpHead();
        dumpHead.push("Tool Version", String.valueOf(TOOL_VERSION));
        dumpHead.push("VM PID", String.valueOf(pid));
        {
            StringBuilder stringBuilder = new StringBuilder();
            try {
                VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
                safeReadVMInputstream(((HotSpotVirtualMachine) vm).executeJCmd("VM.command_line"), stringBuilder::append);
                vm.detach();
            } catch (AttachNotSupportedException e) {
                LOG.log(Level.WARNING, String.format("An Error happend while attaching vm %d", pid), e);
            }
            dumpHead.push("VM Command Line", stringBuilder.toString());
        }
        {
            StringBuilder stringBuilder = new StringBuilder();
            try {
                VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
                safeReadVMInputstream(((HotSpotVirtualMachine) vm).executeJCmd("VM.version"), stringBuilder::append);
                vm.detach();
            } catch (AttachNotSupportedException e) {
                LOG.log(Level.WARNING, String.format("An Error happend while attaching vm %d", pid), e);
            }
            dumpHead.push("VM Version", stringBuilder.toString());
        }

        dumpHead.writeTo(outputStream);
    }

    private static void writeDataDumpTo(long pid, OutputStream outputStream) {
        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(pid));
            safeReadVMInputstream(((HotSpotVirtualMachine) vm).remoteDataDump("-e -l"), (char[] data) -> {
                try {
                    outputStream.write(new String(data).getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            vm.detach();
        } catch (Throwable e) {
            LOG.log(Level.WARNING, String.format("An Error happend while attaching vm %d", pid), e);
            try {
                outputStream.write(String.format("An Error happend while attaching vm %d\n\n", pid).getBytes(StandardCharsets.UTF_8));
                PrintWriter printWriter = new PrintWriter(outputStream);
                e.printStackTrace(printWriter);
                printWriter.flush();
                outputStream.write('\n');
            } catch (IOException e2) {
                throw new RuntimeException(e2);
            }
        }
    }

    private static void safeReadVMInputstream(InputStream vmInputStream, Consumer<char[]> consumer) throws IOException {
        try (
                BufferedInputStream bufferedInputStream = new BufferedInputStream(vmInputStream);
                InputStreamReader inputStreamReader = new InputStreamReader(bufferedInputStream, StandardCharsets.UTF_8)) {
            char[] dataCache = new char[256];
            int status;

            do {
                status = inputStreamReader.read(dataCache);

                if (status > 0) {
                    consumer.accept(status == dataCache.length ? dataCache : Arrays.copyOf(dataCache, status));
                }
            } while (status > 0);
        }
    }
}
