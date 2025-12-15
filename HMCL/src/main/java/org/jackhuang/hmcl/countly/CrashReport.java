package org.jackhuang.hmcl.countly;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CrashReport {

    private final Thread thread;
    private final Throwable throwable;
    private final String stackTrace;

    public CrashReport(Thread thread, Throwable throwable) {
        this.thread = thread;
        this.throwable = throwable;
        stackTrace = StringUtils.getStackTrace(throwable);
    }

    public Throwable getThrowable() {
        return this.throwable;
    }

    public boolean shouldBeReport() {
        if (!stackTrace.contains("org.jackhuang"))
            return false;

        if (throwable instanceof VirtualMachineError)
            return false;

        return true;
    }

    public String getDisplayText() {
        return "---- Hello Minecraft! Crash Report ----\n" +
                "  Version: " + Metadata.VERSION + "\n" +
                "  Time: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()) + "\n" +
                "  Thread: " + thread + "\n" +
                "\n  Content: \n    " +
                stackTrace + "\n\n" +
                "-- System Details --\n" +
                "  Operating System: " + OperatingSystem.SYSTEM_NAME + ' ' + OperatingSystem.SYSTEM_VERSION.getVersion() + "\n" +
                "  System Architecture: " + Architecture.SYSTEM_ARCH.getDisplayName() + "\n" +
                "  Java Architecture: " + Architecture.CURRENT_ARCH.getDisplayName() + "\n" +
                "  Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor") + "\n" +
                "  Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor") + "\n" +
                "  JVM Max Memory: " + Runtime.getRuntime().maxMemory() + "\n" +
                "  JVM Total Memory: " + Runtime.getRuntime().totalMemory() + "\n" +
                "  JVM Free Memory: " + Runtime.getRuntime().freeMemory() + "\n";
    }
}
