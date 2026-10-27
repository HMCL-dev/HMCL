// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java.disco;

import org.jackhuang.hmcl.download.java.JavaDistribution;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.util.*;

import static org.jackhuang.hmcl.download.java.JavaPackageType.*;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.platform.Architecture.*;
import static org.jackhuang.hmcl.util.platform.OperatingSystem.*;

/**
 * @author Glavo
 * @see <a href="https://github.com/foojayio/discoapi">discoapi</a>
 */
public enum DiscoJavaDistribution implements JavaDistribution<DiscoJavaRemoteVersion> {
    TEMURIN("Eclipse Temurin", "temurin", "Adoptium",
            EnumSet.of(JDK, JRE),
            pair(WINDOWS, EnumSet.of(X86_64, X86, ARM64)),
            pair(LINUX, EnumSet.of(X86_64, X86, ARM64, ARM32, RISCV64, PPC64, PPC64LE, S390X, SPARCV9)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))),
    // Fucking javac generates an anonymous inner class here, whose constructor takes no @SafeVarargs annotation, causing a compile-time warning.
    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    LIBERICA("BellSoft Liberica", "liberica", "BellSoft",
            EnumSet.of(JDK, JRE, JDKFX, JREFX),
            pair(WINDOWS, EnumSet.of(X86_64, X86, ARM64)),
            pair(LINUX, EnumSet.of(X86_64, X86, ARM64, ARM32, RISCV64, PPC64LE)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))) {
        @Override
        public boolean testVersion(DiscoJavaRemoteVersion version) {
            if (!super.testVersion(version))
                return false;

            String fileName = version.getFileName();
            return !fileName.endsWith("-lite.tar.gz") && !fileName.endsWith("-lite.zip");
        }
    },
    ZULU("Azul Zulu", "zulu", "Azul",
            EnumSet.of(JDK, JRE, JDKFX, JREFX),
            pair(WINDOWS, EnumSet.of(X86_64, X86, ARM64)),
            pair(LINUX, EnumSet.of(X86_64, X86, ARM64, ARM32, RISCV64, PPC64LE)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))),
    GRAALVM("Oracle GraalVM", "graalvm", "Oracle",
            EnumSet.of(JDK),
            pair(WINDOWS, EnumSet.of(X86_64)),
            pair(LINUX, EnumSet.of(X86_64, ARM64)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))),
    SEMERU("IBM Semeru (OpenJ9)", "semeru", "IBM",
            EnumSet.of(JDK, JRE),
            pair(WINDOWS, EnumSet.of(X86_64)),
            pair(LINUX, EnumSet.of(X86_64, ARM64, PPC64LE, S390X)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))
    ),
    CORRETTO("Amazon Corretto", "corretto", "Amazon",
            EnumSet.of(JDK),
            pair(WINDOWS, EnumSet.of(X86_64)),
            pair(LINUX, EnumSet.of(X86_64, ARM64)),
            pair(MACOS, EnumSet.of(X86_64, ARM64))
    );

    public static DiscoJavaDistribution of(String name) {
        for (DiscoJavaDistribution distribution : values()) {
            if (distribution.apiParameter.equalsIgnoreCase(name) || distribution.name().equalsIgnoreCase(name)) {
                return distribution;
            }
        }

        return null;
    }

    private final String displayName;
    private final String apiParameter;
    private final String vendor;
    private final Set<JavaPackageType> supportedPackageTypes;
    private final Map<OperatingSystem, EnumSet<Architecture>> supportedPlatforms = new EnumMap<>(OperatingSystem.class);

    @SafeVarargs
    DiscoJavaDistribution(String displayName, String apiParameter, String vendor, Set<JavaPackageType> supportedPackageTypes, Pair<OperatingSystem, EnumSet<Architecture>>... supportedPlatforms) {
        this.displayName = displayName;
        this.apiParameter = apiParameter;
        this.vendor = vendor;
        this.supportedPackageTypes = supportedPackageTypes;

        for (Pair<OperatingSystem, EnumSet<Architecture>> platform : supportedPlatforms) {
            this.supportedPlatforms.put(platform.getKey(), platform.getValue());
        }
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public String getApiParameter() {
        return apiParameter;
    }

    public String getVendor() {
        return vendor;
    }

    @Override
    public Set<JavaPackageType> getSupportedPackageTypes() {
        return supportedPackageTypes;
    }

    public boolean isSupport(Platform platform) {
        EnumSet<Architecture> architectures = supportedPlatforms.get(platform.getOperatingSystem());
        return architectures != null && architectures.contains(platform.getArchitecture());
    }

    public boolean testVersion(DiscoJavaRemoteVersion version) {
        return this.getApiParameter().equals(version.getDistribution());
    }
}
