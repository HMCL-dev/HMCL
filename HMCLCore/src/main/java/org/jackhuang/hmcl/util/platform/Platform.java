package org.jackhuang.hmcl.util.platform;

import org.jackhuang.hmcl.util.ToStringBuilder;

import java.util.Objects;

public final class Platform {
    public static final Platform UNKNOWN = new Platform(OperatingSystem.UNKNOWN, Architecture.UNKNOWN);

    private final OperatingSystem os;
    private final Architecture arch;

    public Platform(OperatingSystem os, Architecture arch) {
        this.os = os;
        this.arch = arch;
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
