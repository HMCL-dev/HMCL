// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.jackhuang.hmcl.game.GarbageCollector.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests garbage collector compatibility fallbacks and generated JVM options.
@NotNullByDefault
public final class GarbageCollectorTest {
    /// Tests that ZGC preferences retain the closest supported ZGC mode before using older collectors.
    @Test
    public void resolvesZgcFallbacks() {
        assertEquals(GENERATIONAL_ZGC, GENERATIONAL_ZGC.resolve(21));
        assertEquals(GENERATIONAL_ZGC, GENERATIONAL_ZGC.resolve(25));
        assertEquals(ZGC, GENERATIONAL_ZGC.resolve(20));
        assertEquals(ZGC, GENERATIONAL_ZGC.resolve(11));
        assertEquals(G1, GENERATIONAL_ZGC.resolve(10));
        assertEquals(G1, GENERATIONAL_ZGC.resolve(8));
        assertEquals(CMS, GENERATIONAL_ZGC.resolve(7));
        assertEquals(SERIAL, GENERATIONAL_ZGC.resolve(-1));

        assertEquals(ZGC, ZGC.resolve(11));
        assertEquals(ZGC, ZGC.resolve(23));
        assertEquals(GENERATIONAL_ZGC, ZGC.resolve(24));
        assertEquals(G1, ZGC.resolve(8));
    }

    /// Tests fallbacks for modern, legacy, and universally supported collectors.
    @Test
    public void resolvesOtherCollectorFallbacks() {
        assertEquals(SHENANDOAH, SHENANDOAH.resolve(12));
        assertEquals(G1, SHENANDOAH.resolve(11));
        assertEquals(CMS, SHENANDOAH.resolve(7));

        assertEquals(G1, G1.resolve(8));
        assertEquals(CMS, G1.resolve(7));
        assertEquals(CMS, CMS.resolve(13));
        assertEquals(G1, CMS.resolve(14));
        assertEquals(PAR_NEW, PAR_NEW.resolve(13));
        assertEquals(G1, PAR_NEW.resolve(14));
        assertEquals(SERIAL, CMS.resolve(5));

        assertEquals(PARALLEL, PARALLEL.resolve(-1));
        assertEquals(SERIAL, SERIAL.resolve(-1));
        assertEquals(DEFAULT, DEFAULT.resolve(25));
        assertTrue(DEFAULT.getJvmArguments(25).isEmpty());
    }

    /// Tests ZGC options across experimental, generational, default-generational, and non-generational-removal JDKs.
    @Test
    public void createsVersionSpecificZgcArguments() {
        assertEquals(List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"), ZGC.getJvmArguments(11));
        assertEquals(List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseZGC"), ZGC.getJvmArguments(14));
        assertEquals(List.of("-XX:+UseZGC"), ZGC.getJvmArguments(15));
        assertEquals(List.of("-XX:+UseZGC"), ZGC.getJvmArguments(20));

        assertEquals(List.of("-XX:+UseZGC", "-XX:+ZGenerational"), GENERATIONAL_ZGC.getJvmArguments(21));
        assertEquals(List.of("-XX:+UseZGC", "-XX:+ZGenerational"), GENERATIONAL_ZGC.getJvmArguments(22));
        assertEquals(List.of("-XX:+UseZGC"), GENERATIONAL_ZGC.getJvmArguments(23));

        assertEquals(List.of("-XX:+UseZGC", "-XX:-ZGenerational"), ZGC.getJvmArguments(21));
        assertEquals(List.of("-XX:+UseZGC", "-XX:-ZGenerational"), ZGC.getJvmArguments(23));
        assertEquals(List.of("-XX:+UseZGC"), ZGC.getJvmArguments(24));
    }

    /// Tests collector selection options and fallbacks outside the ZGC family.
    @Test
    public void createsArgumentsForOtherCollectors() {
        assertEquals(List.of("-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC"),
                SHENANDOAH.getJvmArguments(12));
        assertEquals(List.of("-XX:+UseShenandoahGC"), SHENANDOAH.getJvmArguments(15));
        assertEquals(List.of("-XX:+UseG1GC"), SHENANDOAH.getJvmArguments(8));

        assertEquals(List.of("-XX:+UseParNewGC"), PAR_NEW.getJvmArguments(8));
        assertEquals(List.of("-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC"), PAR_NEW.getJvmArguments(9));
        assertEquals(List.of("-XX:+UseParNewGC", "-XX:+UseConcMarkSweepGC"), PAR_NEW.getJvmArguments(13));
        assertEquals(List.of("-XX:+UseG1GC"), PAR_NEW.getJvmArguments(14));

        assertEquals(List.of("-XX:+UseConcMarkSweepGC"), CMS.getJvmArguments(13));
        assertEquals(List.of("-XX:+UseG1GC"), CMS.getJvmArguments(14));
        assertEquals(List.of("-XX:+UseConcMarkSweepGC"), G1.getJvmArguments(7));
        assertEquals(List.of("-XX:+UseParallelGC"), PARALLEL.getJvmArguments(25));
        assertEquals(List.of("-XX:+UseSerialGC"), SERIAL.getJvmArguments(25));
    }

    /// Tests that launch options retain the selected preference and default to the existing HMCL policy.
    @Test
    public void launchOptionsCarryGarbageCollectorPreference() {
        assertEquals(DEFAULT, new LaunchOptions.Builder().create().getGarbageCollector());

        LaunchOptions options = new LaunchOptions.Builder()
                .setGarbageCollector(GENERATIONAL_ZGC)
                .create();
        assertEquals(GENERATIONAL_ZGC, options.getGarbageCollector());
    }
}
