package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Objects;

public final class Platform {
    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);
    public static final Platform CURRENT = new Platform(OperatingSystem.CURRENT_OS, Architecture.CURRENT);

    public static final Platform WINDOWS_X86_64 = new Platform(OperatingSystem.WINDOWS, Architecture.X86_64);
    public static final Platform OSX_X86_64 = new Platform(OperatingSystem.OSX, Architecture.X86_64);
    public static final Platform LINUX_X86_64 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);

    private final OperatingSystem os;
    private final Architecture arch;

    private Platform(OperatingSystem os, Architecture arch) {
        this.os = os;
        this.arch = arch;
    }

    public static Platform getPlatform() {
        return CURRENT;
    }

    public static Platform getPlatform(OperatingSystem os, Architecture arch) {
        if (os == OperatingSystem.UNKNOWN && arch == Architecture.UNKNOWN) {
            return UNKNOWN;
        }

        if (arch == Architecture.X86_64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_X86_64;
                case OSX:
                    return OSX_X86_64;
                case LINUX:
                    return LINUX_X86_64;
            }
        }

        return new Platform(os, arch);
    }

    public OperatingSystem getOperatingSystem() {
        return os;
    }

    public Architecture getArchitecture() {
        return arch;
    }

    public Bits getBits() {
        return arch.getBits();
    }

    @Override
    public int hashCode() {
        return Objects.hash(os, arch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Platform)) return false;
        Platform platform = (Platform) o;
        return os == platform.os && arch == platform.arch;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("os", os).append("arch", arch).toString();
    }
}
