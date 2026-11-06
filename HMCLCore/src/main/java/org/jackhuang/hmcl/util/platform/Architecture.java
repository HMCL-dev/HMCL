// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.util.platform.Bits.BIT_32;
import static org.jackhuang.hmcl.util.platform.Bits.BIT_64;

/// Processor architecture and ABI variants, independent of the current machine.
@NotNullByDefault
public enum Architecture {
    /// 32-bit x86.
    X86(BIT_32, "x86"),

    /// 64-bit x86, also known as AMD64.
    X86_64(BIT_64, "x86-64"),

    /// The 32-bit variant identified by the `ia64n` alias, distinct from [#X86].
    IA32(BIT_32, "IA-32"),

    /// 64-bit Itanium.
    IA64(BIT_64, "IA-64"),

    /// 32-bit SPARC.
    SPARC(BIT_32),

    /// 64-bit SPARC V9.
    SPARCV9(BIT_64, "SPARC V9"),

    /// 32-bit ARM.
    ARM32(BIT_32),

    /// 64-bit ARM, also known as AArch64.
    ARM64(BIT_64),

    /// 32-bit MIPS.
    MIPS(BIT_32),

    /// 64-bit MIPS.
    MIPS64(BIT_64),

    /// Little-endian 32-bit MIPS.
    MIPSEL(BIT_32, "MIPSel"),

    /// Little-endian 64-bit MIPS.
    MIPS64EL(BIT_64, "MIPS64el"),

    /// Big-endian 32-bit PowerPC.
    PPC(BIT_32, "PowerPC"),

    /// Big-endian 64-bit PowerPC.
    PPC64(BIT_64, "PowerPC-64"),

    /// Little-endian 32-bit PowerPC.
    PPCLE(BIT_32, "PowerPC (Little-Endian)"),

    /// Little-endian 64-bit PowerPC.
    PPC64LE(BIT_64, "PowerPC-64 (Little-Endian)"),

    /// IBM System/390, represented as a 32-bit architecture.
    S390(BIT_32),

    /// The 64-bit IBM architecture identified by `s390x`.
    S390X(BIT_64, "S390x"),

    /// 32-bit RISC-V.
    RISCV32(BIT_32, "RISC-V 32"),

    /// 64-bit RISC-V.
    RISCV64(BIT_64, "RISC-V 64"),

    /// 32-bit LoongArch.
    LOONGARCH32(BIT_32, "LoongArch32"),

    /// 64-bit LoongArch with the old-world ABI.
    LOONGARCH64_OW(BIT_64, "LoongArch64 (old world)"),

    /// 64-bit LoongArch with the new-world ABI.
    LOONGARCH64(BIT_64, "LoongArch64"),

    /// Unrecognized or unspecified architecture with unknown bitness.
    UNKNOWN(Bits.UNKNOWN, "Unknown");

    /// Lowercase enum name used in HMCL platform identifiers.
    private final String checkedName;

    /// Human-readable architecture name.
    private final String displayName;

    /// Architecture bitness, or [Bits#UNKNOWN] for an unknown architecture.
    private final Bits bits;

    /// Creates an architecture whose display name is its enum name.
    Architecture(Bits bits) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = this.toString();
        this.bits = bits;
    }

    /// Creates an architecture with an explicit display name.
    Architecture(Bits bits, String displayName) {
        this.checkedName = this.toString().toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.bits = bits;
    }

    /// Returns the architecture bitness, or [Bits#UNKNOWN] for [#UNKNOWN].
    public Bits getBits() {
        return bits;
    }

    /// Returns the lowercase enum name, such as `x86_64` or `loongarch64_ow`.
    public String getCheckedName() {
        return checkedName;
    }

    /// Returns the human-readable architecture name.
    public String getDisplayName() {
        return displayName;
    }

    /// Returns whether this architecture is [#X86] or [#X86_64], excluding [#IA32].
    public boolean isX86() {
        return this == X86 || this == X86_64;
    }

    /// Returns whether this architecture is [#ARM32] or [#ARM64].
    public boolean isArm() {
        return this == ARM32 || this == ARM64;
    }

    /// Parses an architecture name case-insensitively, ignoring surrounding whitespace.
    /// Does not consult the current system or infer its byte order or ABI.
    /// Unqualified PowerPC names are big-endian; `loongarch64` denotes the new-world ABI.
    /// Returns [#UNKNOWN] for `null`, blank, or unrecognized input.
    public static Architecture parseArchName(@Nullable String value) {
        if (value == null) {
            return UNKNOWN;
        }
        value = value.trim().toLowerCase(Locale.ROOT);

        return switch (value) {
            case "x8664", "x86-64", "x86_64", "amd64", "ia32e", "em64t", "x64", "intel64" -> X86_64;
            case "x8632", "x86-32", "x86_32", "x86", "i86pc", "i386", "i486", "i586", "i686", "ia32", "x32" -> X86;
            case "arm64", "aarch64" -> ARM64;
            case "arm", "arm32" -> ARM32;
            case "mips64" -> MIPS64;
            case "mips64el" -> MIPS64EL;
            case "mips", "mips32" -> MIPS;
            case "mipsel", "mips32el" -> MIPSEL;
            case "riscv", "risc-v", "riscv64" -> RISCV64;
            case "ia64", "ia64w", "itanium64" -> IA64;
            case "ia64n" -> IA32;
            case "sparcv9", "sparc64" -> SPARCV9;
            case "sparc", "sparc32" -> SPARC;
            case "ppc64", "powerpc64" -> PPC64;
            case "ppc64le", "powerpc64le" -> PPC64LE;
            case "ppc", "ppc32", "powerpc", "powerpc32" -> PPC;
            case "ppcle", "ppc32le", "powerpcle", "powerpc32le" -> PPCLE;
            case "s390" -> S390;
            case "s390x" -> S390X;
            case "loongarch32" -> LOONGARCH32;
            case "loongarch64" -> LOONGARCH64;
            case "loongarch64_ow" -> LOONGARCH64_OW;
            default -> {
                if (value.startsWith("armv7")) {
                    yield ARM32;
                }
                if (value.startsWith("armv8") || value.startsWith("armv9")) {
                    yield ARM64;
                }
                yield UNKNOWN;
            }
        };
    }
}
