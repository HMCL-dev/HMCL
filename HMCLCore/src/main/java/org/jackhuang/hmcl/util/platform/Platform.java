package org.jackhuang.hmcl.util.platform;

import java.util.Objects;

public final class Platform {
    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);

    public static final Platform WINDOWS_X86_64 = new Platform(OperatingSystem.WINDOWS, Architecture.X86_64);
    public static final Platform WINDOWS_ARM64 = new Platform(OperatingSystem.WINDOWS, Architecture.ARM64);

    public static final Platform LINUX_X86_64 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);
    public static final Platform LINUX_ARM64 = new Platform(OperatingSystem.LINUX, Architecture.ARM64);

    public static final Platform OSX_X86_64 = new Platform(OperatingSystem.OSX, Architecture.X86_64);
    public static final Platform OSX_ARM64 = new Platform(OperatingSystem.OSX, Architecture.ARM64);

    public static final Platform CURRENT_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.CURRENT_ARCH);
    public static final Platform SYSTEM_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.SYSTEM_ARCH);

    public static boolean isCompatibleWithX86Java() {
        return Architecture.SYSTEM_ARCH.isX86() || SYSTEM_PLATFORM == OSX_ARM64 || SYSTEM_PLATFORM == WINDOWS_ARM64;
    }

    private final OperatingSystem os;
    private final Architecture arch;

    private Platform(OperatingSystem os, Architecture arch) {
        this.os = os;
        this.arch = arch;
    }

    public static Platform getPlatform() {
        return CURRENT_PLATFORM;
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
        } else if (arch == Architecture.ARM64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_ARM64;
                case OSX:
                    return OSX_ARM64;
                case LINUX:
                    return LINUX_ARM64;
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
        return os.getCheckedName() + "-" + arch.getCheckedName();
    }
}
