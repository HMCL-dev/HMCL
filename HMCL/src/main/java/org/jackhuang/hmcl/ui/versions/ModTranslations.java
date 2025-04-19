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
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

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
        switch (type) {
            case MOD:
                return MOD;
            case MODPACK:
                return MODPACK;
            default:
                return EMPTY;
        }
    }

    private final String resourceName;
    private List<Mod> mods;
    private Map<String, Mod> modMap; // mod id and subname -> mod
    private Map<String, Mod> curseForgeMap; // curseforge id -> mod
    private List<Pair<String, Mod>> keywords;
    private int maxKeywordLength = -1;

    ModTranslations(String resourceName) {
        this.resourceName = resourceName;
    }

    @Nullable
    public Mod getModByCurseForgeId(String id) {
        if (StringUtils.isBlank(id) || !loadCurseForgeMap()) return null;

        return curseForgeMap.get(id);
    }

    @Nullable
    public Mod getMod(String subname, String id) {
        if (!loadModMap()) return null;

        if (StringUtils.isNotBlank(subname)) {
            String filteredSubName = "subname-" + subname.replaceAll("[^a-zA-Z]", "");
            return modMap.get(filteredSubName);
        }

        if (StringUtils.isNotBlank(id)) {
            return modMap.get(id);
        }

        return null;
    }

    public abstract String getMcmodUrl(Mod mod);

    public List<Mod> searchMod(String query) {
        if (!loadKeywords()) return Collections.emptyList();

        StringBuilder newQuery = ((CharSequence) query).chars()
                .filter(ch -> !Character.isSpaceChar(ch))
                .collect(StringBuilder::new, (sb, value) -> sb.append((char) value), StringBuilder::append);
        query = newQuery.toString();

        StringUtils.LongestCommonSubsequence lcs = new StringUtils.LongestCommonSubsequence(query.length(), maxKeywordLength);
        List<Pair<Integer, Mod>> modList = new ArrayList<>();
        for (Pair<String, Mod> keyword : keywords) {
            int value = lcs.calc(query, keyword.getKey());
            if (value >= Math.max(1, query.length() - 3)) {
                modList.add(pair(value, keyword.getValue()));
            }
        }
        return modList.stream()
                .sorted((a, b) -> -a.getKey().compareTo(b.getKey()))
                .map(Pair::getValue)
                .collect(Collectors.toList());
    }

    private boolean loadFromResource() {
        if (mods != null) return true;
        if (StringUtils.isBlank(resourceName)) {
            mods = Collections.emptyList();
            return true;
        }

        try {
            String modData = IOUtils.readFullyAsString(ModTranslations.class.getResourceAsStream(resourceName));
            mods = Arrays.stream(modData.split("\n")).filter(line -> !line.startsWith("#")).map(Mod::new).collect(Collectors.toList());
            return true;
        } catch (Exception e) {
            LOG.warning("Failed to load " + resourceName, e);
            return false;
        }
    }

    private boolean loadCurseForgeMap() {
        if (curseForgeMap != null) {
            return true;
        }

        if (mods == null) {
            if (!loadFromResource()) return false;
        }

        curseForgeMap = new HashMap<>();
        for (Mod mod : mods) {
            if (StringUtils.isNotBlank(mod.getCurseforge())) {
                curseForgeMap.put(mod.getCurseforge(), mod);
            }
        }
        return true;
    }

    private boolean loadModMap() {
        if (modMap != null) {
            return true;
        }

        if (mods == null) {
            if (!loadFromResource()) return false;
        }

        modMap = new HashMap<>();
        for (Mod mod : mods) {
            String subname = mod.getSubname();
            if (StringUtils.isNotBlank(subname) && !"examplemod".equals(subname)) {
                String filteredSubName = "subname-" + subname.replaceAll("[^a-zA-Z]", "");
                if (!filteredSubName.isEmpty()) {
                    modMap.put(filteredSubName, mod);
                }
            }
            for (String id : mod.getModIds()) {
                if (StringUtils.isNotBlank(id) && !"examplemod".equals(id)) {
                    modMap.put(id, mod);
                }
            }
        }
        return true;
    }

    private boolean loadKeywords() {
        if (keywords != null) {
            return true;
        }

        if (mods == null) {
            if (!loadFromResource()) return false;
        }

        keywords = new ArrayList<>();
        maxKeywordLength = -1;
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
        return true;
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
            modIds = Collections.unmodifiableList(Arrays.asList(items[2].split(",")));
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
