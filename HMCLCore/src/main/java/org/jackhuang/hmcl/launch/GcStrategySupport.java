// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.launch;

import org.jackhuang.hmcl.game.GcStrategy;
import org.jackhuang.hmcl.game.LaunchOptions;
import org.jackhuang.hmcl.util.platform.Bits;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Resolves collector preferences against the actual game Java executable before generating arguments.
@NotNullByDefault
public final class GcStrategySupport {
    /// Utility class.
    private GcStrategySupport() {
    }

    /// Recognizes collector selection/tuning arguments, excluding heap capacity and unrelated JIT options.
    /// User GC parameters take precedence over the entire generated GC policy, including tuning.
    public static boolean isGcArgument(String argument) {
        if (argument.equals("-Xincgc") || argument.equals("-Xconcgc") || argument.startsWith("-Xmn"))
            return true;
        if (!argument.startsWith("-XX:"))
            return false;
        String key = argument.substring(4).replaceFirst("^[+-]", "").split("=", 2)[0];
        return key.contains("GC") || key.startsWith("G1") || key.startsWith("Z")
                || key.startsWith("Shenandoah") || key.startsWith("CMS")
                || key.equals("InitiatingHeapOccupancyPercent") || key.equals("NewRatio")
                || key.equals("NewSize") || key.equals("MaxNewSize") || key.contains("Survivor")
                || key.equals("MaxTenuringThreshold") || key.equals("UseStringDeduplication");
    }

    /// Adds GC parameters without changing heap capacity; custom GC arguments and disabled generation win.
    /// Returns the effective collector, or [GcStrategy#DEFAULT] when Java/user arguments remain in control.
    public static GcStrategy apply(CommandBuilder command, LaunchOptions options) throws IOException {
        if (options.isNoGeneratedJVMArgs() || options.isNoGeneratedOptimizingJVMArgs())
            return GcStrategy.DEFAULT;
        if (!command.noneMatch(GcStrategySupport::isGcArgument)) {
            LOG.info("GC strategy is controlled by custom JVM arguments");
            return GcStrategy.DEFAULT;
        }
        if (options.getGcStrategy() == GcStrategy.DEFAULT) {
            LOG.info("GC strategy leaves collector selection and tuning to Java");
            return GcStrategy.DEFAULT;
        }

        int version = options.getJava().getParsedVersion();
        Path executable = options.getJavaExecutable();
        Resolution resolution;
        try {
            resolution = resolve(options.getGcStrategy(), version, options.getJava().getBits() == Bits.BIT_64,
                    arguments -> supports(executable, arguments));
        } catch (ProbeInterruptedException e) {
            throw new IOException("Interrupted while checking garbage collector support", e.getCause());
        } catch (ProbeFailedException e) {
            throw new GcStrategyException(e.reason, options.getGcStrategy(), options.getJava().getVersion(), executable, e.getCause());
        }
        if ((options.getGcStrategy() == GcStrategy.ZGC || options.getGcStrategy() == GcStrategy.SHENANDOAH)
                && resolution.strategy() != options.getGcStrategy())
            throw new GcStrategyException(GcStrategyException.Reason.UNSUPPORTED, options.getGcStrategy(),
                    options.getJava().getVersion(), executable, null);
        command.addAllDefault(resolution.arguments());
        if (resolution.strategy() == GcStrategy.TUNED_G1) {
            command.addUnstableDefault("G1MixedGCCountTarget", "5");
            command.addUnstableDefault("G1NewSizePercent", "20");
            command.addUnstableDefault("G1ReservePercent", "20");
            command.addUnstableDefault("MaxGCPauseMillis", "50");
            command.addUnstableDefault("G1HeapRegionSize", "32m");
        }
        LOG.info("GC strategy: requested=" + options.getGcStrategy() + ", effective=" + resolution.strategy()
                + ", generational=" + resolution.generational() + ", Java=" + version + ", executable=" + executable);
        return resolution.strategy();
    }

    /// Selects the first supported candidate. Version checks prevent experimental/removed flags being emitted.
    /// The support predicate tests the target runtime, since vendor/platform availability can differ.
    static Resolution resolve(GcStrategy requested, int version, boolean is64bit,
                              Predicate<List<String>> supports) {
        if (requested == GcStrategy.ZGC) {
            if (is64bit && version >= 21) {
                // Java 23 defaults to generational ZGC; never emit its removed selector on Java 24+.
                @Unmodifiable List<String> generational = version < 23
                        ? List.of("-XX:+UseZGC", "-XX:+ZGenerational") : List.of("-XX:+UseZGC");
                if (supports.test(generational))
                    return new Resolution(GcStrategy.ZGC, generational, true);
            }
            if (is64bit && version >= 15 && version < 24) {
                // Java 23 still permits explicitly disabling generational mode; Java 24+ does not.
                @Unmodifiable List<String> nonGenerational = version == 23
                        ? List.of("-XX:+UseZGC", "-XX:-ZGenerational") : List.of("-XX:+UseZGC");
                if (supports.test(nonGenerational))
                    return new Resolution(GcStrategy.ZGC, nonGenerational, false);
            }
        } else if (requested == GcStrategy.SHENANDOAH) {
            if (is64bit && version >= 25) {
                // Generational Shenandoah became a production feature in Java 25; do not opt into Java 24's experiment.
                @Unmodifiable List<String> generational = List.of("-XX:+UseShenandoahGC", "-XX:ShenandoahGCMode=generational");
                if (supports.test(generational))
                    return new Resolution(GcStrategy.SHENANDOAH, generational, true);
            }
            if (is64bit && version >= 15) {
                @Unmodifiable List<String> regular = List.of("-XX:+UseShenandoahGC");
                if (supports.test(regular))
                    return new Resolution(GcStrategy.SHENANDOAH, regular, false);
            }
        } else if ((requested == GcStrategy.G1 || requested == GcStrategy.TUNED_G1) && version >= 8) {
            @Unmodifiable List<String> arguments = List.of("-XX:+UseG1GC");
            if (supports.test(arguments))
                return new Resolution(requested, arguments, false);
        }
        // apply() stops the launch when a specifically selected low-pause collector is unavailable.
        return new Resolution(GcStrategy.DEFAULT, List.of(), false);
    }

    /// Tests collector availability with a small, bounded JVM invocation, without starting the game.
    private static boolean supports(Path executable, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("-Xms16m");
        command.add("-Xmx64m");
        command.addAll(arguments);
        command.add("-version");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        // Test runtime capability independently of inherited JVM injection variables.
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        @Nullable Path output = null;
        try {
            // Redirect to a temporary log so a failed/noisy executable cannot block on a full output pipe.
            output = Files.createTempFile("hmcl-gc-probe-", ".log");
            builder.redirectOutput(output.toFile());
            Process process = builder.start();
            try {
                process.getOutputStream().close();
                if (!process.waitFor(3, TimeUnit.SECONDS))
                    throw new ProbeFailedException(GcStrategyException.Reason.DETECTION_TIMED_OUT,
                            new IOException("Java GC capability check timed out after 3 seconds"));
                if (process.exitValue() == 0)
                    return true;
                String diagnostic;
                try (InputStream input = Files.newInputStream(output)) {
                    diagnostic = new String(input.readNBytes(16384), StandardCharsets.UTF_8).trim();
                }
                if (isUnsupportedDiagnostic(diagnostic))
                    return false;
                throw new ProbeFailedException(GcStrategyException.Reason.DETECTION_FAILED,
                        new IOException("Java GC capability check exited with " + process.exitValue() + ": " + diagnostic));
            } finally {
                if (process.isAlive())
                    process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProbeInterruptedException(e);
        } catch (IOException e) {
            throw new ProbeFailedException(GcStrategyException.Reason.DETECTION_FAILED, e);
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException e) {
                    LOG.warning("Could not remove GC capability check log", e);
                }
            }
        }
    }

    /// Distinguishes rejected collector options from memory allocation, executable or other VM failures.
    static boolean isUnsupportedDiagnostic(String diagnostic) {
        String lower = diagnostic.toLowerCase(Locale.ROOT);
        boolean collector = lower.contains("usezgc") || lower.contains("zgenerational")
                || lower.contains("useg1gc") || lower.contains("zgc") || lower.contains("shenandoah")
                || lower.contains("garbage collector");
        return collector && (lower.contains("unrecognized") || lower.contains("unrecognised")
                || lower.contains("not supported") || lower.contains("unsupported") || lower.contains("not available")
                || (lower.contains("shenandoahgcmode") && lower.contains("unknown")));
    }

    /// Effective collector and immutable selection arguments, excluding the optional G1 tuning bundle.
    @NotNullByDefault
    record Resolution(GcStrategy strategy, @Unmodifiable List<String> arguments, boolean generational) {
        /// Defensively copies the arguments to keep the resolution immutable.
        Resolution {
            arguments = List.copyOf(arguments);
        }
    }

    /// Propagates inconclusive checks without silently treating them as unsupported collectors.
    @NotNullByDefault
    private static final class ProbeFailedException extends RuntimeException {
        /// Failure category retained for the translated launch error.
        private final GcStrategyException.Reason reason;

        /// Creates a capability-check failure with its original diagnostic cause.
        private ProbeFailedException(GcStrategyException.Reason reason, IOException cause) {
            super(cause);
            this.reason = reason;
        }
    }

    /// Carries an interruption through the capability predicate without treating cancellation as fallback.
    @NotNullByDefault
    private static final class ProbeInterruptedException extends RuntimeException {
        /// Wraps the interrupted capability check.
        private ProbeInterruptedException(InterruptedException cause) {
            super(cause);
        }
    }
}
