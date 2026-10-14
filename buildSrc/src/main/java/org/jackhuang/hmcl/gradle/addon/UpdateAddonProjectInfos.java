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
package org.jackhuang.hmcl.gradle.addon;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UCharacterCategory;
import com.ibm.icu.util.ULocale;
import org.glavo.url.WebURL;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@NotNullByDefault
public abstract class UpdateAddonProjectInfos extends DefaultTask {

    private static final Logger LOGGER = Logging.getLogger(UpdateAddonProjectInfos.class);

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @Input
    public abstract Property<AddonProjectType> getProjectType();

    @TaskAction
    public void run() throws Exception {
        Path inputFile = getInputFile().getAsFile().get().toPath();
        Path outputFile = getOutputFile().getAsFile().get().toPath();

        AddonProjectType projectType = getProjectType().get();

        List<AddonInfo> infos;
        try (var reader = Files.newBufferedReader(inputFile)) {
            Gson gson = new Gson();
            infos = gson.fromJson(reader, TypeToken.getParameterized(List.class, AddonInfo.class).getType());
        }

        Files.createDirectories(outputFile.getParent());
        try (var writer = new CSVWriter(Files.newBufferedWriter(outputFile))) {
            writer.writeComment("""
                    
                    Hello Minecraft! Launcher
                    Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
                    
                    mcmod.cn
                    Copyright (C) 2026. All Rights Reserved.
                    
                    """);

            for (AddonInfo info : infos) {
                int mcmodId;

                try {
                    mcmodId = projectType.getMcmodId(info);
                } catch (Exception e) {
                    LOGGER.warn("Failed to get the id of mcmod.cn for \"{}\"", info.name.main);
                    continue;
                }

                @Nullable String curseforgeId;
                try {
                      curseforgeId = projectType.getCurseForgeId(info);
                 } catch (Exception e) {
                      LOGGER.warn("Failed to get the id of CurseForge for \"{}\"", info.name.main);
                      curseforgeId = null;
                 }

                String mainName = cleanChineseName(cleanName(info.name.main));
                String subName = cleanName(info.name.sub);
                String abbr = cleanName(info.name.abbr);

                String modids;
                if (projectType == AddonProjectType.MOD) {
                    try {
                        modids = String.join(";", cleanModIds(info));
                    } catch (Exception e) {
                        LOGGER.warn("Mod \"{}\" has invalid modid list: {}", info.name.main, info.modid);
                        modids = "";
                    }
                } else {
                    modids = null;
                }

                // Write the fields to the CSV file
                writer.writeField(String.valueOf(mcmodId));
                writer.writeField(curseforgeId);
                writer.writeField(mainName);
                writer.writeField(subName);
                writer.writeField(abbr);

                if (projectType == AddonProjectType.MOD) {
                    writer.writeField(modids);
                }

                writer.writeNewLine();
            }
        }
    }

    private static final class CSVWriter implements Closeable {
        private boolean firstField = true;
        private final BufferedWriter writer;

        private CSVWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        @Override
        public void close() throws IOException {
            writer.close();
        }

        public void writeComment(String comment) throws IOException {
            for (String line : comment.lines().toList()) {
                writer.write("# ");
                writer.write(line);
                writer.write('\n');
            }
            firstField = true;
        }

        public void writeField(@Nullable String field) throws IOException {
            if (firstField) {
                firstField = false;
            } else {
                writer.write(',');
            }

            if (field == null || field.isEmpty()) {
                return;
            }

            for (int i = 0; i < field.length(); ) {
                int ch = field.codePointAt(i);

                switch (ch) {
                    case '\0' -> writer.write("\\0");
                    case '\\' -> writer.write("\\\\");
                    case '\n' -> writer.write("\\n");
                    case '\r' -> writer.write("\\r");
                    case '\t' -> writer.write("\\t");
                    case ',' -> writer.write("\\u002c");
                    default -> {

                        boolean needEscape = switch (UCharacter.getType(ch)) {
                            case UCharacterCategory.CONTROL,
                                 UCharacterCategory.FORMAT,
                                 UCharacterCategory.UNASSIGNED,
                                 UCharacterCategory.SURROGATE,
                                 UCharacterCategory.PRIVATE_USE,
                                 UCharacterCategory.LINE_SEPARATOR,
                                 UCharacterCategory.PARAGRAPH_SEPARATOR -> true;
                            default -> false;
                        };

                        if (Character.isBmpCodePoint(ch)) {
                            if (needEscape) {
                                writer.write(String.format("\\u%04x", ch));
                            } else {
                                writer.write(ch);
                            }
                        } else {
                            char ch0 = Character.highSurrogate(ch);
                            char ch1 = Character.lowSurrogate(ch);
                            if (needEscape) {
                                writer.write(String.format("\\u%04x\\u%04x", (int) ch0, (int) ch1));
                            } else {
                                writer.write(ch0);
                                writer.write(ch1);
                            }
                        }
                    }
                }

                i += Character.charCount(ch);
            }
        }

        public void writeNewLine() throws IOException {
            writer.write('\n');
            firstField = true;
        }
    }

    private static String cleanName(String name) {
        if (name.indexOf('&') < 0) {
            return name;
        }

        return name
                // Most string values in the original JSON data contain HTML-escaped characters, such as `&amp;`, `&lt;`, and `&gt;`.
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                // Remove zero-width space (U+200B) characters.
                .replace("\u200b", "");
    }

    private static String cleanChineseName(String chineseName) {
        chineseName = chineseName.trim();

        if (chineseName.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(chineseName.length());
        int[] codePoints = chineseName.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            int ch = codePoints[i];
            int prev = i > 0 ? codePoints[i - 1] : 0;

            switch (ch) {
                case '（' -> {
                    if (Character.isWhitespace(prev) || prev == '！' || prev == '。')
                        builder.append('(');
                    else
                        builder.append(" (");
                }
                case '）' -> builder.append(')');
                default -> builder.appendCodePoint(ch);
            }
        }
        return builder.toString().trim();
    }

    private static final Pattern MODID_PATTERN = Pattern.compile("^[a-zA-Z0-9._|+' -]+$");
    private static final Set<String> MODID_WHITELIST = Set.of(
            // https://www.mcmod.cn/class/866.html
            "<CoFH Tweak>",
            // https://www.mcmod.cn/class/1780.html
            "EFFLL (LiteLoader ObjectHolder fix)"
    );

    private static Collection<String> cleanModIds(AddonInfo info) {
        if (info.modid() == null) {
            throw new GradleException("Modid list is null");
        }

        var result = new LinkedHashSet<String>(info.modid.size());

        for (String modid : info.modid) {
            String cleanedModid = cleanName(modid.trim());

            // TODO: Temporary workaround, remove this after the modid is fixed
            cleanedModid = switch (cleanedModid) {
                // replace non-breaking hyphen (U+2011) with normal hyphen
                // https://www.mcmod.cn/class/31211.html
                case "godfall‑trinkets" -> "godfall-trinkets";
                // https://www.mcmod.cn/class/30575.html
                case "enchantment-table\"" -> "enchantment-table";
                // https://www.mcmod.cn/class/28657.html
                case "斗蛐蛐" -> "mob_battle_plus";
                // https://www.mcmod.cn/class/4158.html
                case "morecuriostotemsofundying:" -> "mtca";
                // https://www.mcmod.cn/class/12820.html
                case "caves_&_canyons" -> "caves_canyons";
                // https://www.mcmod.cn/class/30957.html
                case "minecraft:" -> "moderncontentbackport";
                default -> cleanedModid;
            };

            if (!MODID_PATTERN.matcher(cleanedModid).matches() && !MODID_WHITELIST.contains(cleanedModid)) {
                throw new GradleException("Invalid modid: " + modid);
            }

            result.add(cleanedModid);
        }

        return result;
    }

    public enum AddonProjectType {
        MOD("class", "mc-mods"),
        MODPACK("modpack", "modpacks");

        private final Pattern mcmodUrlPattern;
        private final String curseforgeType;

        AddonProjectType(
                String mcmodSlug,
                String curseforgeType
        ) {
            this.mcmodUrlPattern = Pattern.compile("https://www\\.mcmod\\.cn/" + mcmodSlug + "/(?<id>\\d+)\\.html");
            this.curseforgeType = curseforgeType;
        }

        int getMcmodId(AddonInfo info) {
            String url = info.links.list.get("mcmod").get(0).url();
            var matcher = mcmodUrlPattern.matcher(url);
            if (!matcher.matches()) {
                throw new GradleException("Invalid mcmod.cn URL: " + url);
            }

            return Integer.parseInt(matcher.group("id"));
        }

        private static final Pattern CURSEFORGE_PATH_PATTERNS = Pattern.compile(
                "^/(?<game>[a-zA-Z-]+)/(?<type>[a-zA-Z0-9-]+)/+(?<id>[\\w-]+)(/(.*?))?$"
        );


        @Nullable String getCurseForgeId(AddonInfo info) throws Exception {
            var links = info.links.list.get("curseforge");

            if (links == null || links.isEmpty()) {
                return null;
            }

            Exception cause = null;

            for (AddonInfo.Link link : links) {
                try {
                    WebURL url = WebURL.parse(link.url());

                    if (!url.getScheme().equals("https") && !url.getScheme().equals("http")) {
                        throw new GradleException("Invalid CurseForge URL: " + url);
                    }

                    switch (Objects.requireNonNull(url.getHost(), "host")) {
                        case "files.xmdhs.com",
                             "edge.forgecdn.net",
                             "minecraft.curseforge.com",
                             "mediafilez.forgecdn.net",
                             // Deprecated domain names
                             "www.curse.com",
                             "mods.curse.com" -> {
                            continue;
                        }
                        case "curseforge.com",
                             "www.curseforge.com",
                             "legacy.curseforge.com",
                             "beta.curseforge.com" -> {
                            String path = url.getPath();

                            var matcher = CURSEFORGE_PATH_PATTERNS.matcher(path);
                            if (!matcher.matches()) {
                                throw new GradleException("Invalid CurseForge URL: " + url);
                            }

                            String game = matcher.group("game");
                            if (!game.equalsIgnoreCase("minecraft")) {
                                // Ignore minecraft-bedrock and other non-minecraft projects
                                continue;
                            }

                            String type = matcher.group("type");
                            if (type.equals(curseforgeType)) {
                                return matcher.group("id");
                            } else {
                                continue;
                            }
                        }
                        default -> throw new GradleException("Invalid CurseForge URL: " + url);
                    }
                } catch (Exception e) {
                    if (cause == null) {
                        cause = e;
                    } else {
                        cause.addSuppressed(e);
                    }
                }
            }

            if (cause != null)
                throw cause;

            return null;
        }
    }

    // Json Model

    private record AddonInfo(
            AddonInfo.Name name,
            @JsonAdapter(AddonInfo.ModIdDeserializer.class)
            @Nullable List<String> modid,
            Links links
    ) {
        record Name(String main, String sub, String abbr) {
        }

        record Link(String url, String content) {
        }

        record Links(@Unmodifiable Map<String, @Unmodifiable List<Link>> list) {
        }

        public static final class ModIdDeserializer implements JsonDeserializer<List<String>> {
            private static final Type STRING_LIST = TypeToken.getParameterized(List.class, String.class).getType();

            @Override
            public @Nullable List<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json.isJsonNull()) {
                    return null;
                }

                if (json.isJsonArray()) {
                    return context.deserialize(json, STRING_LIST);
                } else {
                    JsonObject jsonObject = json.getAsJsonObject();
                    JsonElement list = jsonObject.get("list");
                    if (list == null) {
                        return null;
                    } else {
                        return context.deserialize(list, STRING_LIST);
                    }
                }
            }
        }
    }
}
