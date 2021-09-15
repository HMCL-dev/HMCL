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

    public Bits getPlatform() {
        return bits;
    }

    public static final String SYSTEM_ARCHITECTURE;
    public static final Architecture CURRENT;

    private static Architecture normalizeArch(String value) {
        value = normalize(value);
        if (value.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
            return X86_64;
        }
        if (value.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
            return X86;
        }
        if (value.matches("^(ia64w?|itanium64)$")) {
            return IA64;
        }
        if ("ia64n".equals(value)) {
            return IA32;
        }
        if (value.matches("^(sparc|sparc32)$")) {
            return SPARC32;
        }
        if (value.matches("^(sparcv9|sparc64)$")) {
            return SPARC64;
        }
        if (value.matches("^(arm|arm32)$")) {
            return ARM;
        }
        if ("aarch64".equals(value)) {
            return ARM64;
        }
        if (value.matches("^(mips|mips32)$")) {
            return MIPS;
        }
        if (value.matches("^(mipsel|mips32el)$")) {
            return MIPSEL32;
        }
        if ("mips64".equals(value)) {
            return MIPS64;
        }
        if ("mips64el".equals(value)) {
            return MIPSEL64;
        }
        if (value.matches("^(ppc|ppc32)$")) {
            return PPC;
        }
        if (value.matches("^(ppcle|ppc32le)$")) {
            return PPCLE;
        }
        if ("ppc64".equals(value)) {
            return PPC64;
        }
        if ("ppc64le".equals(value)) {
            return PPCLE64;
        }
        if ("s390".equals(value)) {
            return S390;
        }
        if ("s390x".equals(value)) {
            return S390X;
        }
        if ("riscv".equals(value)) {
            return RISCV;
        }
        return UNKNOWN;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
    }

    static {
        SYSTEM_ARCHITECTURE = System.getProperty("os.arch");

        CURRENT = normalizeArch(SYSTEM_ARCHITECTURE);
    }
}
