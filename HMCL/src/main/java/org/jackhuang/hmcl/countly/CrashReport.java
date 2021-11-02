package org.jackhuang.hmcl.countly;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public class CrashReport {

    private final Thread thread;
    private final Throwable throwable;
    private final String stackTrace;

    private boolean nonFatal;

    public CrashReport(Thread thread, Throwable throwable) {
        this.thread = thread;
        this.throwable = throwable;
        stackTrace = StringUtils.getStackTrace(throwable);
        nonFatal = false;
    }

    public CrashReport setNonFatal() {
        nonFatal = true;
        return this;
    }

    public boolean shouldBeReport() {
        if (!stackTrace.contains("org.jackhuang"))
            return false;

        return true;
    }

    public Map<String, Object> getMetrics(long runningTime) {
        return mapOf(
                pair("_run", runningTime),
                pair("_app_version", Metadata.VERSION),
                pair("_os", OperatingSystem.SYSTEM_NAME),
                pair("_os_version", OperatingSystem.SYSTEM_VERSION),
                pair("_disk_current", getDiskAvailable()),
                pair("_disk_total", getDiskTotal()),
                pair("_ram_current", getMemoryAvailable()),
                pair("_ram_total", Runtime.getRuntime().maxMemory() / BYTES_IN_MB),
                pair("_error", stackTrace),
                pair("_logs", Logging.getLogs()),
                pair("_name", throwable.getLocalizedMessage()),
                pair("_nonfatal", nonFatal)
        );
    }

    public String getDisplayText() {
        return "---- Hello Minecraft! Crash Report ----\n" +
                "  Version: " + Metadata.VERSION + "\n" +
                "  Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\n" +
                "  Thread: " + thread + "\n" +
                "\n  Content: \n    " +
                stackTrace + "\n\n" +
                "-- System Details --\n" +
                "  Operating System: " + OperatingSystem.SYSTEM_NAME + ' ' + OperatingSystem.SYSTEM_VERSION + "\n" +
                "  System Architecture: " + Architecture.SYSTEM_ARCH_NAME + "\n" +
                "  Java Architecture: " + Architecture.CURRENT_ARCH_NAME + "\n" +
                "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n" +
                "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n" +
                "  JVM Max Memory: " + Runtime.getRuntime().maxMemory() + "\n" +
                "  JVM Total Memory: " + Runtime.getRuntime().totalMemory() + "\n" +
                "  JVM Free Memory: " + Runtime.getRuntime().freeMemory() + "\n";
    }

    private static final Long BYTES_IN_MB = 1024L * 1024;

    private static long getMemoryAvailable() {
        Long total = Runtime.getRuntime().totalMemory();
        Long availMem = Runtime.getRuntime().freeMemory();
        return (total - availMem) / BYTES_IN_MB;
    }

    private static long getDiskAvailable() {
        long total = 0, free = 0;
        for (File f : File.listRoots()) {
            total += f.getTotalSpace();
            free += f.getUsableSpace();
        }
        return (total - free) / BYTES_IN_MB;
    }

    private static long getDiskTotal() {
        long total = 0;
        for (File f : File.listRoots()) {
            total += f.getTotalSpace();
        }
        return total / BYTES_IN_MB;
    }
}
