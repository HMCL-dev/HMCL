// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.util.platform.Bits.BIT_32;
import static org.jackhuang.hmcl.util.platform.Bits.BIT_64;

public enum Architecture {
    X86(BIT_32, "x86"),
    X86_64(BIT_64, "x86-64"),
    IA32(BIT_32, "IA-32"),
    IA64(BIT_64, "IA-64"),
    SPARC(BIT_32),
    SPARCV9(BIT_64, "SPARC V9"),
    ARM32(BIT_32),
    ARM64(BIT_64),
    MIPS(BIT_32),
    MIPS64(BIT_64),
    MIPSEL(BIT_32, "MIPSel"),
    MIPS64EL(BIT_64, "MIPS64el"),
    PPC(BIT_32, "PowerPC"),
    PPC64(BIT_64, "PowerPC-64"),
    PPCLE(BIT_32, "PowerPC (Little-Endian)"),
    PPC64LE(BIT_64, "PowerPC-64 (Little-Endian)"),
    S390(BIT_32),
    S390X(BIT_64, "S390x"),
    RISCV32(BIT_32, "RISC-V 32"),
    RISCV64(BIT_64, "RISC-V 64"),
    LOONGARCH32(BIT_32, "LoongArch32"),
    LOONGARCH64_OW(BIT_64, "LoongArch64 (old world)"),
    LOONGARCH64(BIT_64, "LoongArch64"),
    UNKNOWN(Bits.UNKNOWN, "Unknown");

    private final String checkedName;
    private final String displayName;
    private final Bits bits;

    Architecture(Bits bits) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = this.toString();
        this.bits = bits;
    }

    Architecture(Bits bits, String displayName) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.bits = bits;
    }

    public Bits getBits() {
        return bits;
    }

    public String getCheckedName() {
        return checkedName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isX86() {
        return this == X86 || this == X86_64;
    }

    public boolean isArm() {
        return this == ARM32 || this == ARM64;
    }

    /// Parses an architecture name without consulting the current system.
    /// Unqualified PowerPC names are big-endian; `loongarch64` denotes the new-world ABI.
    /// Returns unknown for null or unrecognized input.
    public static Architecture parseArchName(@Nullable String value) {
        if (value == null) {
            return UNKNOWN;
        }
        value = value.trim().toLowerCase(Locale.ROOT);

        switch (value) {
            case "x8664":
            case "x86-64":
            case "x86_64":
            case "amd64":
            case "ia32e":
            case "em64t":
            case "x64":
            case "intel64":
                return X86_64;
            case "x8632":
            case "x86-32":
            case "x86_32":
            case "x86":
            case "i86pc":
            case "i386":
            case "i486":
            case "i586":
            case "i686":
            case "ia32":
            case "x32":
                return X86;
            case "arm64":
            case "aarch64":
                return ARM64;
            case "arm":
            case "arm32":
                return ARM32;
            case "mips64":
                return MIPS64;
            case "mips64el":
                return MIPS64EL;
            case "mips":
            case "mips32":
                return MIPS;
            case "mipsel":
            case "mips32el":
                return MIPSEL;
            case "riscv":
            case "risc-v":
            case "riscv64":
                return RISCV64;
            case "ia64":
            case "ia64w":
            case "itanium64":
                return IA64;
            case "ia64n":
                return IA32;
            case "sparcv9":
            case "sparc64":
                return SPARCV9;
            case "sparc":
            case "sparc32":
                return SPARC;
            case "ppc64":
            case "powerpc64":
                return PPC64;
            case "ppc64le":
            case "powerpc64le":
                return PPC64LE;
            case "ppc":
            case "ppc32":
            case "powerpc":
            case "powerpc32":
                return PPC;
            case "ppcle":
            case "ppc32le":
            case "powerpcle":
            case "powerpc32le":
                return PPCLE;
            case "s390":
                return S390;
            case "s390x":
                return S390X;
            case "loongarch32":
                return LOONGARCH32;
            case "loongarch64":
                return LOONGARCH64;
            case "loongarch64_ow":
                return LOONGARCH64_OW;
            default:
                if (value.startsWith("armv7")) {
                    return ARM32;
                }
                if (value.startsWith("armv8") || value.startsWith("armv9")) {
                    return ARM64;
                }
                return UNKNOWN;
        }
    }
}
