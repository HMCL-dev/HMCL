package org.jackhuang.hmcl.game;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.CurrentJava;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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

    public static void writeDumpTo(long pid, File file) throws IOException, InterruptedException, ClassNotFoundException {
        if (!checkDependencies()) {
            throw new ClassNotFoundException("com.sun.tools.attach.VirtualMachine");
        }

        try (PrintWriter printWriter = new PrintWriter(file)) {
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

    public static void writeDumpHeadTo(long lvmid, PrintWriter printWriter) {
        DumpHead dumpHead = new DumpHead();
        dumpHead.push("Tool Version", String.valueOf(TOOL_VERSION));
        dumpHead.push("VM PID", String.valueOf(lvmid));
        {
            StringBuilder stringBuilder = new StringBuilder();
            safeAttachVM(lvmid, "VM.command_line", stringBuilder::append, (value, throwable) -> {
                stringBuilder.append('\n');
                stringBuilder.append(String.format("An Error happend while attaching vm %d\n\n", lvmid));
                stringBuilder.append(StringUtils.getStackTrace(throwable));
                stringBuilder.append('\n');
            });
            dumpHead.push("VM Command Line", ACCESS_TOKEN_HIDER.matcher(stringBuilder).replaceAll("--accessToken <access token>"));
        }
        {
            StringBuilder stringBuilder = new StringBuilder();
            safeAttachVM(lvmid, "VM.version", stringBuilder::append, (value, throwable) -> {
                stringBuilder.append('\n');
                stringBuilder.append(String.format("An Error happend while attaching vm %d\n\n", lvmid));
                stringBuilder.append(StringUtils.getStackTrace(throwable));
                stringBuilder.append('\n');
            });
            dumpHead.push("VM Version", stringBuilder.toString());
        }

        dumpHead.writeTo(printWriter);
    }

    private static void writeDumpBodyTo(long lvmid, PrintWriter printWriter) {
        safeAttachVM(lvmid, "Thread.print", printWriter::write, (value, throwable) -> {
            printWriter.write(String.format("An Error happend while attaching vm %d\n\n", lvmid));
            throwable.printStackTrace(printWriter);
            printWriter.write('\n');
        });
    }

    private static void safeAttachVM(long lvmid, String command, Consumer<char[]> vmInputStreamReader, BiConsumer<Long, Throwable> onException) {
        try {
            VirtualMachine vm = VirtualMachine.attach(String.valueOf(lvmid));

            try (
                    BufferedInputStream bufferedInputStream = new BufferedInputStream(((sun.tools.attach.HotSpotVirtualMachine) vm).executeJCmd(command));
                    InputStreamReader inputStreamReader = new InputStreamReader(bufferedInputStream, StandardCharsets.UTF_8)) {
                char[] dataCache = new char[256];
                int status;

                do {
                    status = inputStreamReader.read(dataCache);

                    if (status > 0) {
                        vmInputStreamReader.accept(status == dataCache.length ? dataCache : Arrays.copyOf(dataCache, status));
                    }
                } while (status > 0);
            } finally {
                vm.detach();
            }
        } catch (AttachNotSupportedException e) {
            LOG.log(Level.WARNING, String.format("An AttachNotSupported Exception happened while attaching vm %d", lvmid), e);
            onException.accept(lvmid, e);
        } catch (IOException e) {
            LOG.log(Level.WARNING, String.format("An IO Exception happened while attaching vm %d", lvmid), e);
            onException.accept(lvmid, e);
        } catch (Throwable throwable) {
            LOG.log(Level.WARNING, String.format("An unknown Exception happened while attaching vm %d", lvmid), throwable);
            onException.accept(lvmid, throwable);
        }
    }
}
