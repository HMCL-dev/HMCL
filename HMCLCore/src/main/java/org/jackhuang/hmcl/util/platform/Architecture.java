/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.platform.Bits.BIT_32;
import static org.jackhuang.hmcl.util.platform.Bits.BIT_64;

public enum Architecture {
    X86(BIT_32),
    X86_64(BIT_64),
    IA32(BIT_32),
    IA64(BIT_64),
    SPARC32(BIT_32),
    SPARC64(BIT_64),
    ARM(BIT_32),
    ARM64(BIT_64),
    MIPS(BIT_32),
    MIPS64(BIT_64),
    MIPSEL32(BIT_32),
    MIPSEL64(BIT_64),
    PPC(BIT_32),
    PPC64(BIT_64),
    PPCLE(BIT_32),
    PPCLE64(BIT_64),
    S390(BIT_32),
    S390X(BIT_64),
    RISCV(BIT_64),
    UNKNOWN(Bits.UNKNOWN);

    private final Bits bits;

    Architecture(Bits bits) {
        this.bits = bits;
    }

    public Bits getBits() {
        return bits;
    }

    public static final String SYSTEM_ARCHITECTURE;
    public static final Architecture CURRENT;


    private static final Pattern NORMALIZER = Pattern.compile("[^a-z0-9]+");

    public static Architecture parseArch(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        value = NORMALIZER.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("");

        switch (value) {
            case "x8664":
            case "amd64":
            case "ia32e":
            case "em64t":
            case "x64":
                return X86_64;
            case "x8632":
            case "x86":
            case "i386":
            case "i486":
            case "i586":
            case "i686":
            case "ia32":
            case "x32":
                return X86;
            case "aarch64":
                return ARM64;
            case "arm":
            case "arm32":
                return ARM;
            case "mips64":
                return MIPS64;
            case "mips64el":
                return MIPSEL64;
            case "mips":
            case "mips32":
                return MIPS;
            case "mipsel":
            case "mips32el":
                return MIPSEL32;
            case "riscv":
                return RISCV;
            case "ia64":
            case "ia64w":
            case "itanium64":
                return IA64;
            case "ia64n":
                return IA32;
            case "sparcv9":
            case "sparc64":
                return SPARC64;
            case "sparc":
            case "sparc32":
                return SPARC32;
            case "ppc64":
                return PPC64;
            case "ppc64le":
                return PPCLE64;
            case "ppc":
            case "ppc32":
                return PPC;
            case "ppcle":
            case "ppc32le":
                return PPCLE;
            case "s390":
                return S390;
            case "s390x":
                return S390X;
            default:
                return UNKNOWN;
        }
    }

    static {
        SYSTEM_ARCHITECTURE = System.getProperty("os.arch");

        CURRENT = parseArch(SYSTEM_ARCHITECTURE);
    }
}
