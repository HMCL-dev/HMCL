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
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.text.BreakIterator;
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

/// Converts mcmod.cn JSON exports into UTF-8 metadata resources for the launcher's addon project registries.
@NotNullByDefault
public abstract class UpdateAddonProjectInfos extends DefaultTask {

    /// Reports records and optional metadata that cannot be converted.
    private static final Logger LOGGER = Logging.getLogger(UpdateAddonProjectInfos.class);

    /// Returns the input file property for a UTF-8 JSON array of project records.
    @InputFile
    public abstract RegularFileProperty getInputFile();

    /// Returns the output file property. Existing contents are replaced during execution.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Returns the type of projects to export.
    @Input
    public abstract Property<AddonProjectType> getProjectType();

    /// Reads the export and writes the selected project type's metadata.
    /// Invalid records are skipped where possible, and invalid optional metadata is omitted.
    /// A failure may leave a partially written output file.
    ///
    /// @throws Exception if the export cannot be read, converted or written
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

            Map<String, TreeSet<Integer>> modIdTomcmodId = new HashMap<>();

            for (AddonInfo info : infos) {
                int mcmodId;

                try {
                    mcmodId = projectType.getMcmodId(info);
                } catch (Exception e) {
                    LOGGER.warn("Failed to get the id of mcmod.cn for \"{}\"", info.name.main);
                    continue;
                }

                if (projectType.shouldSkip(mcmodId)) {
                    continue;
                }

                @Nullable String curseforgeId;
                try {
                    curseforgeId = projectType.getCurseForgeId(info);
                } catch (Exception e) {
                    LOGGER.warn("Failed to get the id of CurseForge for \"{}\"", info.name.main);
                    curseforgeId = null;
                }

                @Nullable String modrinthId;
                try {
                    modrinthId = projectType.getModrinthId(info);
                } catch (Exception e) {
                    LOGGER.warn("Failed to get the id of Modrinth for \"{}\"", info.name.main);
                    modrinthId = null;
                }

                String mainName = cleanChineseName(info.name.main);
                String subName = cleanName(info.name.sub);
                String abbr = cleanName(info.name.abbr);

                // https://www.mcmod.cn/class/4242.html
                if (projectType == AddonProjectType.MOD && mcmodId == 4242) {
                    mainName = "钓\uD83D\uDC1F\uD83D\uDC20";
                }

                if (mainName.isEmpty()) {
                    LOGGER.warn("Mod \"{}\" has empty main name", info.name.main);
                    continue;
                }

                String modids;
                if (projectType == AddonProjectType.MOD) {
                    try {
                        Collection<String> idList = cleanModIds(info, mcmodId);

                        for (String modid : idList) {
                            modIdTomcmodId.computeIfAbsent(modid, k -> new TreeSet<>()).add(mcmodId);
                        }

                        modids = String.join(";", idList);
                    } catch (Exception e) {
                        LOGGER.warn("Mod \"{}\" has invalid modid list: {}", info.name.main, info.modid);
                        modids = "";
                    }
                } else {
                    modids = null;
                }

                // Keep this field order aligned with the runtime project registry parser.
                writer.writeField(String.valueOf(mcmodId));
                writer.writeField(curseforgeId);
                writer.writeField(modrinthId);
                writer.writeField(mainName);
                writer.writeField(subName);
                writer.writeField(abbr);

                if (projectType == AddonProjectType.MOD) {
                    writer.writeField(modids);
                }

                writer.writeNewLine();
            }

            modIdTomcmodId.forEach((modid, mcmodIds) -> {
                if (mcmodIds.size() > 1) {
                    LOGGER.info("Mod id \"{}\" is used by multiple mods: {}", modid, mcmodIds);
                }
            });
        }
    }

    /// Writes comma-delimited records using backslash escapes instead of quoted CSV fields.
    /// Closing this writer closes its underlying writer.
    private static final class CSVWriter implements Closeable {
        /// Whether the next field omits the leading comma.
        private boolean firstField = true;
        /// Destination owned by this writer and closed by [#close()].
        private final BufferedWriter writer;

        /// Creates a record writer that takes ownership of the supplied destination.
        private CSVWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        /// Closes the underlying writer, flushing its buffered output.
        ///
        /// @throws IOException if flushing or closing the destination fails
        @Override
        public void close() throws IOException {
            writer.close();
        }

        /// Writes comment lines prefixed with `# `. Must be called at a record boundary.
        ///
        /// @throws IOException if writing to the destination fails
        public void writeComment(String comment) throws IOException {
            for (String line : comment.lines().toList()) {
                writer.write("# ");
                writer.write(line);
                writer.write('\n');
            }
            firstField = true;
        }

        /// Appends a backslash-escaped field with a delimiter when needed.
        /// Null values produce empty fields. The record is not terminated.
        ///
        /// @throws IOException if writing to the destination fails
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
                    case ',' -> writer.write("\\c");
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
                                // The reader decodes fixed-width UTF-16 escapes, so preserve both surrogate units.
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

        /// Terminates the current record with LF and starts a new record.
        ///
        /// @throws IOException if writing the separator fails
        public void writeNewLine() throws IOException {
            writer.write('\n');
            firstField = true;
        }
    }

    /// Cleans an exported name by trimming, removing zero-width spaces and decoding selected HTML entities.
    private static String cleanName(String name) {
        // Remove zero-width space (U+200B) characters.
        name = name.replace("\u200b", "");

        if (name.indexOf('&') >= 0) {
            // Most string values in the original JSON data contain HTML-escaped characters, such as `&amp;`, `&lt;`, and `&gt;`.
            name = name
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
        }

        name = name.trim();

        return name;
    }

    private static final Pattern MULTI_SPACE = Pattern.compile("  +");

    /// Cleans a primary name and normalizes fullwidth parentheses and their spacing.
    private static String cleanChineseName(String chineseName) {
        chineseName = cleanName(chineseName);

        if (chineseName.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder(chineseName.length());


        BreakIterator iterator = BreakIterator.getCharacterInstance();
        iterator.setText(chineseName);
        int start = iterator.first();

        @Nullable String prev = null;
        for (int end = iterator.next();
             end != BreakIterator.DONE;
             start = end, end = iterator.next()) {

            String ch = chineseName.substring(start, end);

            switch (ch) {
                case "（" -> {
                    if (prev != null && prev.isBlank() || "！".equals(prev) || "。".equals(prev))
                        builder.append('(');
                    else
                        builder.append(" (");
                }
                case "）" -> builder.append(')');
                case "\t" -> builder.append(' ');
                default -> {
                    if (!UCharacter.hasBinaryProperty(ch, UProperty.RGI_EMOJI)) {
                        builder.append(ch);
                    }
                }
            }

            prev = ch;
        }

        String result = builder.toString().trim();
        return MULTI_SPACE.matcher(result).replaceAll(" ");
    }

    /// Recognizes accepted mod ID syntax.
    private static final Pattern MODID_PATTERN = Pattern.compile("^[a-zA-Z0-9._|+' -]+$");

    /// Recognizes numeric mod IDs that are not accepted by [#MODID_PATTERN] but are explicitly whitelisted.
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]+$");

    /// Explicitly accepted IDs that do not match [#MODID_PATTERN].
    private static final Set<String> MODID_WHITELIST = Set.of(
            // https://www.mcmod.cn/class/866.html
            "<CoFH Tweak>",
            // https://www.mcmod.cn/class/1780.html
            "EFFLL (LiteLoader ObjectHolder fix)"
    );

    /// Returns cleaned, validated mod IDs without duplicates, preserving their original order.
    ///
    /// @throws GradleException if the mod ID list is null or a cleaned ID is not accepted
    private static Collection<String> cleanModIds(AddonInfo info, int mcmodId) {
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
                default -> cleanedModid;
            };

            // https://www.mcmod.cn/class/4.html
            if (mcmodId == 4 && cleanedModid.equals("kamenridercraft4th")) {
                continue;
            }

            if ((!MODID_PATTERN.matcher(cleanedModid).matches() || NUMERIC_PATTERN.matcher(cleanedModid).matches())
                    && !MODID_WHITELIST.contains(cleanedModid)) {
                throw new GradleException("Invalid modid: " + modid);
            }

            result.add(cleanedModid);
        }

        return result;
    }

    /// Selects project-specific link parsing, exclusion rules and output fields.
    public enum AddonProjectType {
        /// Mod projects, excluding Minecraft itself.
        MOD("class", "mc-mods", "mod") {
            /// Returns whether the ID identifies Minecraft rather than a mod.
            @Override
            public boolean shouldSkip(int mcmodId) {
                if (mcmodId == 1) {
                    // Should skip the "Minecraft" project, which is the Minecraft game itself, not a mod.
                    // https://www.mcmod.cn/class/1.html
                    return true;
                }

                return false;
            }
        },
        /// Modpack projects.
        MODPACK("modpack", "modpacks", "modpack");

        /// Recognizes project URLs on mcmod.cn.
        private final Pattern mcmodUrlPattern;
        /// CurseForge category for this project type.
        private final String curseforgeType;

        /// Modrinth category for this project type.
        private final String modrinthType;

        /// Creates a project type for the specified mcmod.cn path and hosting-platform categories.
        ///
        /// @param mcmodSlug      mcmod.cn path segment used in the canonical URL pattern
        /// @param curseforgeType CurseForge category accepted during slug extraction
        /// @param modrinthType   Modrinth category accepted during identifier extraction
        AddonProjectType(
                String mcmodSlug,
                String curseforgeType,
                String modrinthType
        ) {
            this.mcmodUrlPattern = Pattern.compile("https://www\\.mcmod\\.cn/" + mcmodSlug + "/(?<id>\\d+)\\.html");
            this.curseforgeType = curseforgeType;
            this.modrinthType = modrinthType;
        }

        /// Extracts the project ID from the first mcmod.cn link.
        ///
        /// @throws GradleException if the URL is not valid for this project type
        int getMcmodId(AddonInfo info) {
            String url = info.links.list.get("mcmod").get(0).url();
            var matcher = mcmodUrlPattern.matcher(url);
            if (!matcher.matches()) {
                throw new GradleException("Invalid mcmod.cn URL: " + url);
            }

            return Integer.parseInt(matcher.group("id"));
        }

        /// Recognizes CurseForge project paths.
        private static final Pattern CURSEFORGE_PATH_PATTERNS = Pattern.compile(
                "^/(?<game>[a-zA-Z-]+)/(?<type>[a-zA-Z0-9-]+)/+(?<id>[\\w-]+)(/(.*?))?$"
        );


        /// Returns a CurseForge project slug for Minecraft and this project type,
        /// or null if no applicable link is present. No network requests are made.
        ///
        /// @throws Exception if no suitable link is found and at least one link is invalid
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

        /// Recognizes Modrinth project paths.
        private static final Pattern MODRINTH_PATH_PATTERNS = Pattern.compile(
                "^/(?<type>[a-zA-Z0-9-]+)/(?<id>[^/]+)(/(.*?))?$"
        );

        /// Returns a Modrinth project identifier from a link for this project type,
        /// or null if no applicable link is present. The identifier may be a slug or project ID.
        /// No network requests are made.
        ///
        /// @throws Exception if no suitable link is found and at least one link is invalid
        @Nullable String getModrinthId(AddonInfo info) throws Exception {
            var links = info.links.list.get("modrinth");

            if (links == null || links.isEmpty()) {
                return null;
            }

            Exception cause = null;

            for (AddonInfo.Link link : links) {
                try {
                    WebURL url = WebURL.parse(link.url());

                    if (!url.getScheme().equals("https") && !url.getScheme().equals("http")) {
                        throw new GradleException("Invalid Modrinth URL: " + url);
                    }

                    switch (Objects.requireNonNull(url.getHost(), "host")) {
                        case "github.com",
                             "cdn.modrinth.com" -> {
                            continue;
                        }
                        case "modrinth.com",
                             "www.modrinth.com" -> {
                            String path = url.getPath();

                            var matcher = MODRINTH_PATH_PATTERNS.matcher(path);
                            if (!matcher.matches()) {
                                throw new GradleException("Invalid Modrinth URL: " + url);
                            }

                            String type = matcher.group("type");
                            if (!type.equals(modrinthType) && !(this == MOD && type.equals("project"))) {
                                continue;
                            }

                            return matcher.group("id");
                        }
                        default -> throw new GradleException("Invalid Modrinth URL: " + url);
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

        /// Returns whether the project identified by the mcmod.cn ID is excluded from output.
        ///
        /// @implSpec The default implementation returns false.
        public boolean shouldSkip(int mcmodId) {
            return false;
        }
    }

    /// Represents one project in the JSON export.
    ///
    /// @param name  primary, secondary and abbreviated project names
    /// @param modid optional mod IDs decoded from an array or a wrapper object's `list` field
    /// @param links links grouped by source name
    private record AddonInfo(
            AddonInfo.Name name,
            @JsonAdapter(AddonInfo.ModIdDeserializer.class)
            @Nullable List<String> modid,
            Links links
    ) {
        /// Contains the exported project names before cleanup.
        ///
        /// @param main primary name
        /// @param sub  secondary name
        /// @param abbr abbreviation
        record Name(String main, String sub, String abbr) {
        }

        /// Contains a source URL and its accompanying text.
        ///
        /// @param url     exported URL, not yet validated
        /// @param content accompanying text, unused by metadata generation
        record Link(String url, String content) {
        }

        /// Groups exported links by source.
        ///
        /// @param list source names mapped to link lists in export order
        record Links(@Unmodifiable Map<String, @Unmodifiable List<Link>> list) {
        }

        /// Decodes mod IDs from either a JSON array or an object's `list` member.
        public static final class ModIdDeserializer implements JsonDeserializer<List<String>> {
            /// Generic target type passed to Gson for mod ID arrays.
            private static final Type STRING_LIST = TypeToken.getParameterized(List.class, String.class).getType();

            /// Decodes a mod ID list, returning null if the list is absent or JSON null.
            ///
            /// @throws JsonParseException    if Gson cannot decode the selected value as a string list
            /// @throws IllegalStateException if a non-null, non-array value is not a JSON object
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
