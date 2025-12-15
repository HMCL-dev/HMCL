/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.mod;

import com.google.gson.*;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Glavo
 */
public abstract class ParseModDataTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getInputFile();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    private static final Logger LOGGER = Logging.getLogger(ParseModDataTask.class);

    private static final String S = ";";
    private static final String MOD_SEPARATOR = ",";

    private static final Pattern[] CURSEFORGE_PATTERNS = {
            Pattern.compile("^/(minecraft|Minecraft|minecraft-bedrock)/(mc-mods|data-packs|modpacks|customization|mc-addons|texture-packs|customization/configuration|addons|scripts)/+(?<modid>[\\w-]+)(/(.*?))?$"),
            Pattern.compile("^/projects/(?<modid>[\\w-]+)(/(.*?))?$"),
            Pattern.compile("^/mc-mods/minecraft/(?<modid>[\\w-]+)(/(.*?))?$"),
            Pattern.compile("^/legacy/mc-mods/minecraft/(\\d+)-(?<modid>[\\w-]+)"),
    };

    private static String parseCurseforge(String url) {
        URI res = URI.create(url.replace(" ", "%20"));

        if (!"http".equals(res.getScheme()) && !"https".equals(res.getScheme())) {
            return "";
        }

        if ("edge.forgecdn.net".equals(res.getHost())) {
            return "";
        }

        for (Pattern pattern : CURSEFORGE_PATTERNS) {
            Matcher matcher = pattern.matcher(res.getPath());
            if (matcher.matches()) {
                return matcher.group("modid");
            }
        }

        return "";
    }

    private static final Pattern MCMOD_PATTERN =
            Pattern.compile("^https://www\\.mcmod\\.cn/(class|modpack)/(?<modid>\\d+)\\.html$");

    private static String parseMcMod(String url) {
        Matcher matcher = MCMOD_PATTERN.matcher(url);
        if (matcher.matches()) {
            return matcher.group("modid");
        }
        return "";
    }

    private static String cleanChineseName(String chineseName) {
        if (chineseName == null || chineseName.isBlank())
            return "";

        chineseName = chineseName.trim();

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

    private static final Set<String> SKIP = Set.of(
            "Minecraft",
            "The Building Game"
    );

    @TaskAction
    public void run() throws IOException {
        Path inputFile = getInputFile().get().getAsFile().toPath().toAbsolutePath();
        Path outputFile = getOutputFile().get().getAsFile().toPath().toAbsolutePath();

        Files.createDirectories(outputFile.getParent());

        List<ModData> modDatas;
        try (BufferedReader reader = Files.newBufferedReader(inputFile)) {
            modDatas = new Gson().fromJson(reader, TypeToken.getParameterized(List.class, ModData.class).getType());
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            writer.write("#\n" +
                    "# Hello Minecraft! Launcher\n" +
                    "# Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors\n" +
                    "#\n" +
                    "# mcmod.cn\n" +
                    "# Copyright (C) 2025. All Rights Reserved.\n" +
                    "#\n");
            for (ModData mod : modDatas) {
                String chineseName = mod.name.main;
                String subName = mod.name.sub;
                String abbr = mod.name.abbr;

                chineseName = chineseName == null ? "" : cleanChineseName(chineseName);
                if (subName == null)
                    subName = "";
                if (abbr == null)
                    abbr = "";

                if (SKIP.contains(subName)) {
                    continue;
                }

                if (chineseName.contains(S) || subName.contains(S)) {
                    throw new GradleException("Error: " + chineseName);
                }

                String curseforgeId = "";
                String mcmodId = "";

                Map<String, List<ModData.Link>> links = mod.links.list;
                List<ModData.Link> curseforgeLinks = links.get("curseforge");
                List<ModData.Link> mcmodLinks = links.get("mcmod");

                if (curseforgeLinks != null && !curseforgeLinks.isEmpty()) {
                    for (ModData.Link link : curseforgeLinks) {
                        curseforgeId = parseCurseforge(link.url);
                        if (!curseforgeId.isEmpty()) {
                            break;
                        }
                    }
                    if (curseforgeId.isEmpty()) {
                        LOGGER.warn("Error curseforge: {}", chineseName);
                    }
                }

                if (mcmodLinks != null && !mcmodLinks.isEmpty()) {
                    mcmodId = parseMcMod(mcmodLinks.get(0).url);
                    if (mcmodId.isEmpty()) {
                        throw new GradleException("Error mcmod: " + chineseName);
                    }
                }

                List<String> modId = new ArrayList<>();
                if (mod.modid != null) {
                    for (String id : mod.modid) {
                        if (id.contains(MOD_SEPARATOR)) {
                            throw new GradleException("Error modid: " + id);
                        }

                        modId.add(id);
                    }
                }

                String modIds = String.join(MOD_SEPARATOR, modId);

                writer.write(curseforgeId + S + mcmodId + S + modIds + S + chineseName + S + subName + S + abbr + "\n");
            }
        }
    }

    public static final class ModData {

        public Name name;

        @JsonAdapter(ModIdDeserializer.class)
        public List<String> modid;

        public Links links;

        public static final class Name {
            public String main;
            public String sub;
            public String abbr;
        }

        public static final class Link {
            public String url;
            public String content;
        }

        public static final class Links {
            public Map<String, List<Link>> list;
        }

        public static final class ModIdDeserializer implements JsonDeserializer<List<String>> {
            private static final Type STRING_LIST = TypeToken.getParameterized(List.class, String.class).getType();

            @Override
            public List<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
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
