/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;

/**
 * Parser for mod_data.txt
 *
 * @see <a href="https://www.mcmod.cn">mcmod.cn</a>
 */
public enum ModTranslations {
    MOD("/assets/mod_data.txt") {
        @Override
        public String getMcmodUrl(Mod mod) {
            return String.format("https://www.mcmod.cn/class/%s.html", mod.getMcmod());
        }
    },
    MODPACK("/assets/modpack_data.txt") {
        @Override
        public String getMcmodUrl(Mod mod) {
            return String.format("https://www.mcmod.cn/modpack/%s.html", mod.getMcmod());
        }
    },
    EMPTY("") {
        @Override
        public String getMcmodUrl(Mod mod) {
            return "";
        }
    };

    public static ModTranslations getTranslationsByRepositoryType(RemoteModRepository.Type type) {
        return switch (type) {
            case MOD -> MOD;
            case MODPACK -> MODPACK;
            default -> EMPTY;
        };
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static String cleanSubname(String subname) {
        if (StringUtils.isBlank(subname))
            return "";

        StringBuilder builder = new StringBuilder(subname.length());
        for (int i = 0; i < subname.length(); ) {
            int ch = subname.codePointAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                    || ".+\\".indexOf(ch) >= 0) {
                builder.appendCodePoint(ch);
            } else if (Character.isWhitespace(ch)
                    || "':_-/&()[]{}|,!?~•".indexOf(ch) >= 0
                    || ch >= 0x1F300 && ch <= 0x1FAFF) {
                // Remove these unnecessary characters from subname
            } else {
                // The subname contains unsupported characters, so we do not use this subname to match the mod
                return "";
            }
            i += Character.charCount(ch);
        }
        return builder.length() == subname.length() ? subname : builder.toString();
    }

    private final String resourceName;
    private volatile List<Mod> mods;
    private volatile Map<String, Mod> modIdMap; // mod id -> mod
    private volatile Map<String, Mod> subnameMap;
    private volatile Map<String, Mod> curseForgeMap; // curseforge id -> mod
    private volatile List<Pair<String, Mod>> keywords;
    private volatile int maxKeywordLength = -1;

    ModTranslations(String resourceName) {
        this.resourceName = resourceName;
    }

    public @NotNull List<Mod> getMods() {
        List<Mod> mods = this.mods;
        if (mods != null)
            return mods;

        synchronized (this) {
            mods = this.mods;
            if (mods != null)
                return mods;

            if (StringUtils.isBlank(resourceName)) {
                return this.mods = List.of();
            }

            //noinspection DataFlowIssue
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            ModTranslations.class.getResourceAsStream(resourceName), StandardCharsets.UTF_8))) {
                return this.mods = reader.lines().filter(line -> !line.startsWith("#")).map(Mod::new).toList();
            } catch (Exception e) {
                LOG.warning("Failed to load " + resourceName, e);
                return this.mods = List.of();
            }
        }
    }

    private @NotNull Map<String, Mod> getModIdMap() {
        Map<String, Mod> modIdMap = this.modIdMap;
        if (modIdMap != null)
            return modIdMap;
        synchronized (this) {
            modIdMap = this.modIdMap;
            if (modIdMap != null)
                return modIdMap;

            List<Mod> mods = getMods();
            modIdMap = new HashMap<>(mods.size());
            for (Mod mod : mods) {
                for (String id : mod.getModIds()) {
                    if (StringUtils.isNotBlank(id)) {
                        modIdMap.putIfAbsent(id, mod);
                    }
                }
            }

            return this.modIdMap = modIdMap;
        }
    }

    private @NotNull Map<String, Mod> getSubnameMap() {
        Map<String, Mod> subnameMap = this.subnameMap;
        if (subnameMap != null)
            return subnameMap;
        synchronized (this) {
            subnameMap = this.subnameMap;
            if (subnameMap != null)
                return subnameMap;

            subnameMap = new HashMap<>();

            List<Mod> mods = getMods();
            for (Mod mod : mods) {
                String subname = cleanSubname(mod.getSubname());
                if (StringUtils.isNotBlank(subname)) {
                    subnameMap.putIfAbsent(subname, mod);
                }
            }

            return this.subnameMap = subnameMap;
        }
    }

    private @NotNull Map<String, Mod> getCurseForgeMap() {
        Map<String, Mod> curseForgeMap = this.curseForgeMap;
        if (curseForgeMap != null)
            return curseForgeMap;

        synchronized (this) {
            curseForgeMap = this.curseForgeMap;
            if (curseForgeMap != null)
                return curseForgeMap;

            List<Mod> mods = getMods();
            curseForgeMap = new HashMap<>(mods.size());
            for (Mod mod : mods) {
                if (StringUtils.isNotBlank(mod.getCurseforge())) {
                    curseForgeMap.putIfAbsent(mod.getCurseforge(), mod);
                }
            }

            return this.curseForgeMap = curseForgeMap;
        }
    }

    private @NotNull List<Pair<String, Mod>> getKeywords() {
        List<Pair<String, Mod>> keywords = this.keywords;
        if (keywords != null)
            return keywords;

        synchronized (this) {
            keywords = this.keywords;
            if (keywords != null)
                return keywords;

            List<Mod> mods = getMods();

            keywords = new ArrayList<>();
            int maxKeywordLength = -1;
            for (Mod mod : mods) {
                if (StringUtils.isNotBlank(mod.getName())) {
                    keywords.add(pair(mod.getName(), mod));
                    maxKeywordLength = Math.max(maxKeywordLength, mod.getName().length());
                }
                if (StringUtils.isNotBlank(mod.getSubname())) {
                    keywords.add(pair(mod.getSubname(), mod));
                    maxKeywordLength = Math.max(maxKeywordLength, mod.getSubname().length());
                }
                if (StringUtils.isNotBlank(mod.getAbbr())) {
                    keywords.add(pair(mod.getAbbr(), mod));
                    maxKeywordLength = Math.max(maxKeywordLength, mod.getAbbr().length());
                }
            }

            this.maxKeywordLength = maxKeywordLength;
            return this.keywords = keywords;
        }
    }

    private int getMaxKeywordLength() {
        int maxKeywordLength = this.maxKeywordLength;
        if (maxKeywordLength >= 0)
            return maxKeywordLength;

        // Ensure maxKeywordLength is initialized
        getKeywords();
        return this.maxKeywordLength;
    }

    @Nullable
    public Mod getModByCurseForgeId(String id) {
        if (StringUtils.isBlank(id)) return null;

        return getCurseForgeMap().get(id);
    }

    @Nullable
    public Mod getMod(String id, String subname) {
        subname = cleanSubname(subname);
        if (StringUtils.isNotBlank(subname)) {
            Mod mod = getSubnameMap().get(subname);
            if (mod != null && (StringUtils.isBlank(id) || mod.getModIds().contains(id)))
                return mod;
        }

        if (StringUtils.isNotBlank(id))
            return getModIdMap().get(id);

        return null;
    }

    public abstract String getMcmodUrl(Mod mod);

    public List<Mod> searchMod(String query) {
        StringBuilder newQuery = query.chars()
                .filter(ch -> !Character.isSpaceChar(ch))
                .collect(StringBuilder::new, (sb, value) -> sb.append((char) value), StringBuilder::append);
        query = newQuery.toString();

        StringUtils.LongestCommonSubsequence lcs = new StringUtils.LongestCommonSubsequence(query.length(), getMaxKeywordLength());
        List<Pair<Integer, Mod>> modList = new ArrayList<>();
        for (Pair<String, Mod> keyword : getKeywords()) {
            int value = lcs.calc(query, keyword.getKey());
            if (value >= Math.max(1, query.length() - 3)) {
                modList.add(pair(value, keyword.getValue()));
            }
        }
        return modList.stream()
                .sorted((a, b) -> -a.getKey().compareTo(b.getKey()))
                .map(Pair::getValue)
                .toList();
    }

    public static final class Mod {
        private final String curseforge;
        private final String mcmod;
        private final List<String> modIds;
        private final String name;
        private final String subname;
        private final String abbr;

        public Mod(String line) {
            String[] items = line.split(";", -1);
            if (items.length != 6) {
                throw new IllegalArgumentException("Illegal mod data line, 6 items expected " + line);
            }

            curseforge = items[0];
            mcmod = items[1];
            modIds = List.of(items[2].split(","));
            name = items[3];
            subname = items[4];
            abbr = items[5];
        }

        public Mod(String curseforge, String mcmod, List<String> modIds, String name, String subname, String abbr) {
            this.curseforge = curseforge;
            this.mcmod = mcmod;
            this.modIds = modIds;
            this.name = name;
            this.subname = subname;
            this.abbr = abbr;
        }

        public String getDisplayName() {
            StringBuilder builder = new StringBuilder();
            if (StringUtils.isNotBlank(abbr)) {
                builder.append("[").append(abbr.trim()).append("] ");
            }
            builder.append(name);
            if (StringUtils.isNotBlank(subname)) {
                builder.append(" (").append(subname).append(")");
            }
            return builder.toString();
        }

        public String getCurseforge() {
            return curseforge;
        }

        public String getMcmod() {
            return mcmod;
        }

        public List<String> getModIds() {
            return modIds;
        }

        public String getName() {
            return name;
        }

        public String getSubname() {
            return subname;
        }

        public String getAbbr() {
            return abbr;
        }
    }
}
