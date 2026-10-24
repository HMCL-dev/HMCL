/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects the machine's region.
@NotNullByDefault
public final class RegionUtils {

    /// The two-letter region code detected at class initialization, or `null` if none can be determined.
    /// Shared time zones require a matching locale region or Windows user GeoID.
    /// Later changes to system settings do not update this value.
    public static final @Nullable String MACHINE_REGION = detectMachineRegion();

    /// Detects the region from the system time zone, using [LocaleUtils#SYSTEM_DEFAULT]
    /// and the Windows user GeoID to resolve ambiguity or supply a missing mapping.
    ///
    /// @return the detected region code, or `null` if no region can be determined unambiguously
    private static @Nullable String detectMachineRegion() {
        @Unmodifiable List<String> candidates;
        try {
            candidates = findRegionsByTimeZone(ZoneId.systemDefault().getId());
        } catch (Exception e) {
            LOG.warning("Failed to load time zone region mapping", e);
            candidates = List.of();
        }

        if (candidates.size() == 1)
            return candidates.get(0);

        String localeRegion = LocaleUtils.SYSTEM_DEFAULT.getCountry();
        if (localeRegion.matches("[A-Z]{2}")) {
            if ((candidates.isEmpty() || candidates.contains(localeRegion)))
                return localeRegion;
        } else {
            localeRegion = null;
        }

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && NativeUtils.USE_JNA) {
            @Nullable Kernel32 kernel32 = Kernel32.INSTANCE;

            if (kernel32 != null) {
                @Nullable String geoidRegion = mapGeoIdToRegion(kernel32.GetUserGeoID(WinConstants.GEOCLASS_NATION));
                if (geoidRegion != null && (candidates.isEmpty() || candidates.contains(geoidRegion)))
                    return geoidRegion;
            }
        }

        if (!candidates.isEmpty())
            return candidates.get(0);

        return localeRegion;
    }

    /// Reads the bundled mapping and returns the requested time zone's candidate regions in source order.
    ///
    /// @param zoneId the time zone identifier to match against the first CSV field
    /// @return an unmodifiable candidate list, or an empty list if the time zone is not mapped
    /// @throws IOException if the resource is missing or cannot be read
    /// @see <a href="https://data.iana.org/time-zones/tzdb/zone1970.tab">tzdb timezone descriptions</a>
    @VisibleForTesting
    static @Unmodifiable List<String> findRegionsByTimeZone(String zoneId) throws IOException {
        try (@Nullable InputStream input = RegionUtils.class.getResourceAsStream("/assets/timezone_regions.csv")) {
            if (input == null) {
                throw new IOException("Missing time zone region mapping");
            }
            String prefix = zoneId + ",";
            for (String line : Lang.toIterable(new String(input.readAllBytes(), StandardCharsets.ISO_8859_1).lines())) {
                if (line.startsWith(prefix))
                    return List.of(line.substring(prefix.length()).split(","));
            }
            return List.of();
        }
    }

    /// Returns the Windows two-letter region code for a known geographical location identifier.
    /// Historical codes and the Windows code for Kosovo are retained. Locations for which
    /// Windows reports the placeholder `XX`, including geographical groupings, are not mapped.
    ///
    /// @param geoId the Windows geographical location identifier
    /// @return the uppercase region code, or `null` if the identifier has no mapping
    /// @see <a href="https://learn.microsoft.com/windows/win32/intl/table-of-geographical-locations">Table of Geographical Locations</a>
    private static @Nullable String mapGeoIdToRegion(int geoId) {
        return switch (geoId) {
            case 2 -> "AG";
            case 3 -> "AF";
            case 4 -> "DZ";
            case 5 -> "AZ";
            case 6 -> "AL";
            case 7 -> "AM";
            case 8 -> "AD";
            case 9 -> "AO";
            case 10 -> "AS";
            case 11 -> "AR";
            case 12 -> "AU";
            case 14 -> "AT";
            case 17 -> "BH";
            case 18 -> "BB";
            case 19 -> "BW";
            case 20 -> "BM";
            case 21 -> "BE";
            case 22 -> "BS";
            case 23 -> "BD";
            case 24 -> "BZ";
            case 25 -> "BA";
            case 26 -> "BO";
            case 27 -> "MM";
            case 28 -> "BJ";
            case 29 -> "BY";
            case 30 -> "SB";
            case 32 -> "BR";
            case 34 -> "BT";
            case 35 -> "BG";
            case 37 -> "BN";
            case 38 -> "BI";
            case 39 -> "CA";
            case 40 -> "KH";
            case 41 -> "TD";
            case 42 -> "LK";
            case 43 -> "CG";
            case 44 -> "CD";
            case 45 -> "CN";
            case 46 -> "CL";
            case 49 -> "CM";
            case 50 -> "KM";
            case 51 -> "CO";
            case 54 -> "CR";
            case 55 -> "CF";
            case 56 -> "CU";
            case 57 -> "CV";
            case 59 -> "CY";
            case 61 -> "DK";
            case 62 -> "DJ";
            case 63 -> "DM";
            case 65 -> "DO";
            case 66 -> "EC";
            case 67 -> "EG";
            case 68 -> "IE";
            case 69 -> "GQ";
            case 70 -> "EE";
            case 71 -> "ER";
            case 72 -> "SV";
            case 73 -> "ET";
            case 75 -> "CZ";
            case 77 -> "FI";
            case 78 -> "FJ";
            case 80 -> "FM";
            case 81 -> "FO";
            case 84 -> "FR";
            case 86 -> "GM";
            case 87 -> "GA";
            case 88 -> "GE";
            case 89 -> "GH";
            case 90 -> "GI";
            case 91 -> "GD";
            case 93 -> "GL";
            case 94 -> "DE";
            case 98 -> "GR";
            case 99 -> "GT";
            case 100 -> "GN";
            case 101 -> "GY";
            case 103 -> "HT";
            case 104 -> "HK";
            case 106 -> "HN";
            case 108 -> "HR";
            case 109 -> "HU";
            case 110 -> "IS";
            case 111 -> "ID";
            case 113 -> "IN";
            case 114 -> "IO";
            case 116 -> "IR";
            case 117 -> "IL";
            case 118 -> "IT";
            case 119 -> "CI";
            case 121 -> "IQ";
            case 122 -> "JP";
            case 124 -> "JM";
            case 125 -> "SJ";
            case 126 -> "JO";
            case 129 -> "KE";
            case 130 -> "KG";
            case 131 -> "KP";
            case 133 -> "KI";
            case 134 -> "KR";
            case 136 -> "KW";
            case 137 -> "KZ";
            case 138 -> "LA";
            case 139 -> "LB";
            case 140 -> "LV";
            case 141 -> "LT";
            case 142 -> "LR";
            case 143 -> "SK";
            case 145 -> "LI";
            case 146 -> "LS";
            case 147 -> "LU";
            case 148 -> "LY";
            case 149 -> "MG";
            case 151 -> "MO";
            case 152 -> "MD";
            case 154 -> "MN";
            case 156 -> "MW";
            case 157 -> "ML";
            case 158 -> "MC";
            case 159 -> "MA";
            case 160 -> "MU";
            case 162 -> "MR";
            case 163 -> "MT";
            case 164 -> "OM";
            case 165 -> "MV";
            case 166 -> "MX";
            case 167 -> "MY";
            case 168 -> "MZ";
            case 173 -> "NE";
            case 174 -> "VU";
            case 175 -> "NG";
            case 176 -> "NL";
            case 177 -> "NO";
            case 178 -> "NP";
            case 180 -> "NR";
            case 181 -> "SR";
            case 182 -> "NI";
            case 183 -> "NZ";
            case 184 -> "PS";
            case 185 -> "PY";
            case 187 -> "PE";
            case 190 -> "PK";
            case 191 -> "PL";
            case 192 -> "PA";
            case 193 -> "PT";
            case 194 -> "PG";
            case 195 -> "PW";
            case 196 -> "GW";
            case 197 -> "QA";
            case 198 -> "RE";
            case 199 -> "MH";
            case 200 -> "RO";
            case 201 -> "PH";
            case 202 -> "PR";
            case 203 -> "RU";
            case 204 -> "RW";
            case 205 -> "SA";
            case 206 -> "PM";
            case 207 -> "KN";
            case 208 -> "SC";
            case 209 -> "ZA";
            case 210 -> "SN";
            case 212 -> "SI";
            case 213 -> "SL";
            case 214 -> "SM";
            case 215 -> "SG";
            case 216 -> "SO";
            case 217 -> "ES";
            case 218 -> "LC";
            case 219 -> "SD";
            case 220 -> "SJ";
            case 221 -> "SE";
            case 222 -> "SY";
            case 223 -> "CH";
            case 224 -> "AE";
            case 225 -> "TT";
            case 227 -> "TH";
            case 228 -> "TJ";
            case 231 -> "TO";
            case 232 -> "TG";
            case 233 -> "ST";
            case 234 -> "TN";
            case 235 -> "TR";
            case 236 -> "TV";
            case 237 -> "TW";
            case 238 -> "TM";
            case 239 -> "TZ";
            case 240 -> "UG";
            case 241 -> "UA";
            case 242 -> "GB";
            case 244 -> "US";
            case 245 -> "BF";
            case 246 -> "UY";
            case 247 -> "UZ";
            case 248 -> "VC";
            case 249 -> "VE";
            case 251 -> "VN";
            case 252 -> "VI";
            case 253 -> "VA";
            case 254 -> "NA";
            case 259 -> "WS";
            case 260 -> "SZ";
            case 261 -> "YE";
            case 263 -> "ZM";
            case 264 -> "ZW";
            case 269 -> "CS";
            case 270 -> "ME";
            case 271 -> "RS";
            case 273 -> "CW";
            case 276 -> "SS";
            case 300 -> "AI";
            case 301 -> "AQ";
            case 302 -> "AW";
            case 306 -> "BV";
            case 307 -> "KY";
            case 309 -> "CX";
            case 311 -> "CC";
            case 312 -> "CK";
            case 315 -> "FK";
            case 317 -> "GF";
            case 318 -> "PF";
            case 319 -> "TF";
            case 321 -> "GP";
            case 322 -> "GU";
            case 324 -> "GG";
            case 325 -> "HM";
            case 328 -> "JE";
            case 330 -> "MQ";
            case 331 -> "YT";
            case 332 -> "MS";
            case 333 -> "AN";
            case 334 -> "NC";
            case 335 -> "NU";
            case 336 -> "NF";
            case 337 -> "MP";
            case 339 -> "PN";
            case 342 -> "GS";
            case 343 -> "SH";
            case 347 -> "TK";
            case 349 -> "TC";
            case 351 -> "VG";
            case 352 -> "WF";
            case 15126 -> "IM";
            case 19618 -> "MK";
            case 30967 -> "SX";
            case 31706 -> "MF";
            case 7299303 -> "TL";
            case 9914689 -> "XK";
            case 10028789 -> "AX";
            case 161832015 -> "BL";
            case 161832256 -> "UM";
            case 161832258 -> "BQ";
            default -> null;
        };
    }

    /// Prevents instantiation of this utility class.
    private RegionUtils() {
    }
}
