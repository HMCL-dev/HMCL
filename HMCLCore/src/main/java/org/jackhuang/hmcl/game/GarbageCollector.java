// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Garbage collector preferences for the Java runtime used by Minecraft.
///
/// A preference is resolved against the actual Java major version at launch time. The saved preference is not changed
/// when an unsupported collector falls back to a compatible one.
@NotNullByDefault
public enum GarbageCollector {
    /// Preserves HMCL's default behavior, which selects G1GC on Java 8 and later when JVM optimizations are enabled.
    DEFAULT,
    /// Generational Z Garbage Collector.
    GENERATIONAL_ZGC,
    /// Non-generational Z Garbage Collector.
    ZGC,
    /// Shenandoah Garbage Collector.
    SHENANDOAH,
    /// Garbage-First Garbage Collector.
    G1,
    /// Parallel Garbage Collector.
    PARALLEL,
    /// Parallel New Generation Garbage Collector.
    PAR_NEW,
    /// Concurrent Mark Sweep Garbage Collector.
    CMS,
    /// Serial Garbage Collector.
    SERIAL;

    /// Resolves this preference to a collector supported by the given Java major version.
    ///
    /// ZGC preferences retain the closest available ZGC mode before falling back to G1GC. Other unavailable modern
    /// collectors fall back to G1GC, then CMS on older runtimes, and finally SerialGC when the major version is older
    /// than Java 6 or unknown. `DEFAULT` remains unresolved so the launcher can preserve its existing default policy.
    ///
    /// @param javaVersion the Java major version, or a negative value when it is unknown
    /// @return the effective collector, or `DEFAULT` when this preference is `DEFAULT`
    public GarbageCollector resolve(int javaVersion) {
        return switch (this) {
            case DEFAULT -> DEFAULT;
            case GENERATIONAL_ZGC -> javaVersion >= 21 ? GENERATIONAL_ZGC : ZGC.resolve(javaVersion);
            case ZGC -> {
                if (javaVersion >= 24) {
                    yield GENERATIONAL_ZGC;
                }
                if (javaVersion >= 11) {
                    yield ZGC;
                }
                yield G1.resolve(javaVersion);
            }
            case SHENANDOAH -> javaVersion >= 12 ? SHENANDOAH : G1.resolve(javaVersion);
            case G1 -> javaVersion >= 8 ? G1 : CMS.resolve(javaVersion);
            case PAR_NEW, CMS -> {
                if (javaVersion >= 14) {
                    yield G1;
                }
                if (javaVersion >= 6) {
                    yield this;
                }
                yield SERIAL;
            }
            case PARALLEL, SERIAL -> this;
        };
    }

    /// Returns the JVM options needed to select the effective collector on the given Java major version.
    ///
    /// The options account for experimental collectors and changes to ZGC's generational mode. On Java 9 through 13,
    /// ParNewGC is selected together with CMS because the standalone ParNew and SerialOld combination was removed in
    /// Java 9. The returned list is immutable.
    ///
    /// @param javaVersion the Java major version, or a negative value when it is unknown
    /// @return immutable JVM options for the resolved collector
    public @Unmodifiable List<String> getJvmArguments(int javaVersion) {
        GarbageCollector resolved = resolve(javaVersion);
        return switch (resolved) {
            case DEFAULT -> List.of();
            case GENERATIONAL_ZGC -> javaVersion < 23
                    ? List.of("-XX:+UseZGC", "-XX:+ZGenerational")
                    : List.of("-XX:+UseZGC");
            case ZGC -> {
                if (javaVersion >= 21) {
                    yield List.of("-XX:+UseZGC", "-XX:-ZGenerational");
                }
                if (javaVersion < 15) {
                    yield List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC");
                }
                yield List.of("-XX:+UseZGC");
            }
            case SHENANDOAH -> javaVersion < 15
                    ? List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC")
                    : List.of("-XX:+UseShenandoahGC");
            case G1 -> List.of("-XX:+UseG1GC");
            case PARALLEL -> List.of("-XX:+UseParallelGC");
            case PAR_NEW -> javaVersion >= 9
                    ? List.of("-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC")
                    : List.of("-XX:+UseParNewGC");
            case CMS -> List.of("-XX:+UseConcMarkSweepGC");
            case SERIAL -> List.of("-XX:+UseSerialGC");
        };
    }
}
