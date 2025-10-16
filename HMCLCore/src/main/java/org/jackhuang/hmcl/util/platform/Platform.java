package org.jackhuang.hmcl.util.platform;

public record Platform(OperatingSystem os, Architecture arch) {
    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);

    public static final Platform WINDOWS_X86 = new Platform(OperatingSystem.WINDOWS, Architecture.X86);
    public static final Platform WINDOWS_X86_64 = new Platform(OperatingSystem.WINDOWS, Architecture.X86_64);
    public static final Platform WINDOWS_ARM64 = new Platform(OperatingSystem.WINDOWS, Architecture.ARM64);

    public static final Platform LINUX_X86 = new Platform(OperatingSystem.LINUX, Architecture.X86);
    public static final Platform LINUX_X86_64 = new Platform(OperatingSystem.LINUX, Architecture.X86_64);
    public static final Platform LINUX_ARM64 = new Platform(OperatingSystem.LINUX, Architecture.ARM64);
    public static final Platform LINUX_ARM32 = new Platform(OperatingSystem.LINUX, Architecture.ARM32);
    public static final Platform LINUX_RISCV64 = new Platform(OperatingSystem.LINUX, Architecture.RISCV64);
    public static final Platform LINUX_LOONGARCH64 = new Platform(OperatingSystem.LINUX, Architecture.LOONGARCH64);
    public static final Platform LINUX_LOONGARCH64_OW = new Platform(OperatingSystem.LINUX, Architecture.LOONGARCH64_OW);
    public static final Platform LINUX_MIPS64EL = new Platform(OperatingSystem.LINUX, Architecture.MIPS64EL);

    public static final Platform MACOS_X86_64 = new Platform(OperatingSystem.MACOS, Architecture.X86_64);
    public static final Platform MACOS_ARM64 = new Platform(OperatingSystem.MACOS, Architecture.ARM64);

    public static final Platform FREEBSD_X86_64 = new Platform(OperatingSystem.FREEBSD, Architecture.X86);

    public static final Platform CURRENT_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.CURRENT_ARCH);
    public static final Platform SYSTEM_PLATFORM = Platform.getPlatform(OperatingSystem.CURRENT_OS, Architecture.SYSTEM_ARCH);

    public static boolean isCompatibleWithX86Java() {
        return Architecture.SYSTEM_ARCH.isX86() || SYSTEM_PLATFORM.equals(MACOS_ARM64) || SYSTEM_PLATFORM.equals(WINDOWS_ARM64);
    }

    public static Platform getPlatform(OperatingSystem os, Architecture arch) {
        if (os == OperatingSystem.UNKNOWN && arch == Architecture.UNKNOWN) {
            return UNKNOWN;
        }

        if (arch == Architecture.X86_64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_X86_64;
                case MACOS:
                    return MACOS_X86_64;
                case LINUX:
                    return LINUX_X86_64;
            }
        } else if (arch == Architecture.ARM64) {
            switch (os) {
                case WINDOWS:
                    return WINDOWS_ARM64;
                case MACOS:
                    return MACOS_ARM64;
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

    public boolean equals(OperatingSystem os, Architecture arch) {
        return this.os == os && this.arch == arch;
    }

    @Override
    public String toString() {
        return os.getCheckedName() + "-" + arch.getCheckedName();
    }
}
