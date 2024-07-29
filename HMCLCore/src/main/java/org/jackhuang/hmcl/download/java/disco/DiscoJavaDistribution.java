/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java.disco;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.java.JavaDistribution;
import org.jackhuang.hmcl.download.java.JavaPackageType;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;

import java.util.*;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.platform.Architecture.*;
import static org.jackhuang.hmcl.util.platform.OperatingSystem.*;

/**
 * @author Glavo
 */
public enum DiscoJavaDistribution implements JavaDistribution<DiscoJavaRemoteVersion> {
    TEMURIN("Eclipse Temurin", "temurin", JavaPackageType.FULL, Lang.mapOf(
            pair(WINDOWS, EnumSet.of(X86, ARM64)),
            pair(LINUX, EnumSet.of(X86, ARM32, RISCV64, PPC64, PPC64LE, S390X, SPARCV9))
    )),
    LIBERICA("Liberica", "liberica", JavaPackageType.FULLFX, Lang.mapOf(
            pair(WINDOWS, EnumSet.of(X86, ARM64)),
            pair(LINUX, EnumSet.of(X86, ARM32, RISCV64, PPC64LE))
    )),
    ZULU("Zulu", "zulu", JavaPackageType.FULLFX, Lang.mapOf(
            pair(WINDOWS, EnumSet.of(X86, ARM64)),
            pair(LINUX, EnumSet.of(X86, ARM32, RISCV64, PPC64LE))
    )),
    GRAALVM("GraalVM", "graalvm", JavaPackageType.ONLY_JDK, Collections.emptyMap());

    private final String displayName;
    private final String apiParameter;
    private final Set<JavaPackageType> supportedPackageTypes;
    private final Map<OperatingSystem, EnumSet<Architecture>> additionalSupportedPlatforms;

    DiscoJavaDistribution(String displayName, String apiParameter, Set<JavaPackageType> supportedPackageTypes, Map<OperatingSystem, EnumSet<Architecture>> additionalSupportedPlatforms) {
        this.displayName = displayName;
        this.apiParameter = apiParameter;
        this.supportedPackageTypes = supportedPackageTypes;
        this.additionalSupportedPlatforms = additionalSupportedPlatforms;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    public String getApiParameter() {
        return apiParameter;
    }

    @Override
    public Set<JavaPackageType> getSupportedPackageTypes() {
        return supportedPackageTypes;
    }

    public boolean isSupport(Platform platform) {
        OperatingSystem os = platform.getOperatingSystem();
        Architecture arch = platform.getArchitecture();

        if (arch == Architecture.X86_64) {
            if (os == OperatingSystem.WINDOWS || os == LINUX || os == OperatingSystem.OSX) {
                return true;
            }
        } else if (arch == Architecture.ARM64) {
            if (os == LINUX || os == OperatingSystem.OSX) {
                return true;
            }
        }

        EnumSet<Architecture> architectures = additionalSupportedPlatforms.get(os);
        return architectures != null && architectures.contains(arch);
    }

    @Override
    public Task<TreeMap<Integer, DiscoJavaRemoteVersion>> getFetchJavaVersionsTask(DownloadProvider provider, Platform platform, JavaPackageType packageType) {
        return new DiscoFetchJavaListTask(provider, this, platform, packageType);
    }
}
