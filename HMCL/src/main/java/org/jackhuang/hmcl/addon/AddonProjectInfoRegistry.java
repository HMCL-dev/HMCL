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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
@NotNullByDefault
public abstract class AddonProjectInfoRegistry<I extends AddonProjectInfo> {
    public static final AddonProjectInfoRegistry<ModProjectInfo> MOD = new AddonProjectInfoRegistry<>(
            "/assets/addon/mods.csv",
            32768
    ) {
        @Override
        protected int getFieldsCount() {
            return MOD_IDS_INDEX + 1;
        }

        @Override
        protected ModProjectInfo parseAddonProjectInfo(String[] fields) throws Exception {
            return new ModProjectInfo(
                    Integer.parseInt(fields[MCMOD_ID_INDEX]),
                    fields[CURSEFORGE_ID_INDEX],
                    fields[MODRINTH_ID_INDEX],
                    fields[MAIN_NAME_INDEX],
                    fields[SUB_NAME_INDEX],
                    fields[ABBR_NAME_INDEX],
                    fields[MOD_IDS_INDEX]
            );
        }
    };

    public static @Nullable AddonProjectInfoRegistry<?> getByAddonType(@Nullable RemoteAddon.Type type) {
        if (type == null) return null;
        return switch (type) {
            case MOD -> MOD;
            case MODPACK -> null;
            default -> null;
        };
    }

    protected static final int MCMOD_ID_INDEX = 0;
    protected static final int CURSEFORGE_ID_INDEX = 1;
    protected static final int MODRINTH_ID_INDEX = 2;
    protected static final int MAIN_NAME_INDEX = 3;
    protected static final int SUB_NAME_INDEX = 4;
    protected static final int ABBR_NAME_INDEX = 5;
    protected static final int MOD_IDS_INDEX = 6;

    private final ReentrantLock lock = new ReentrantLock();
    private final String assetsPath;
    private final int initialCapacity;

    private volatile @Nullable SoftReference<@Unmodifiable List<I>> infosRef;

    private AddonProjectInfoRegistry(String assetsPath, int initialCapacity) {
        this.assetsPath = assetsPath;
        this.initialCapacity = initialCapacity;
    }

    protected abstract int getFieldsCount();

    protected abstract I parseAddonProjectInfo(String[] fields) throws Exception;

    private @Unmodifiable List<I> loadInfos() throws IOException {
        InputStream inputStream = AddonProjectInfoRegistry.class.getResourceAsStream(assetsPath);
        if (inputStream == null) {
            return List.of();
        }

        try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
            ArrayList<I> infos = new ArrayList<>(initialCapacity);
            List<I> result = Collections.unmodifiableList(infos);

            int fieldsCount = getFieldsCount();
            String[] fields = new String[fieldsCount];

            StringBuilder fieldBuilder = null;

            String line;
            loop:
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int offset = 0;
                for (int fieldIndex = 0; fieldIndex < fieldsCount; fieldIndex++) {
                    if (offset > line.length()) {
                        LOG.warning("Invalid line in " + assetsPath + ": " + line);
                        continue loop;
                    }

                    int end = line.indexOf(',', offset);
                    if (end < 0)
                        end = line.length();


                    String field;
                    if (offset == end) {
                        field = "";
                    } else {
                        int cursor = offset;
                        while (cursor < end) {
                            if (line.charAt(cursor) == '\\') {
                                break;
                            } else {
                                cursor++;
                            }
                        }

                        if (cursor == end) {
                            field = line.substring(offset, end);
                        } else {
                            if (fieldBuilder == null) {
                                fieldBuilder = new StringBuilder(32);
                            } else {
                                fieldBuilder.setLength(0);
                            }

                            fieldBuilder.append(line, offset, cursor);
                            while (cursor < end) {
                                char c = line.charAt(cursor);
                                if (c == '\\') {
                                    if (cursor < end - 1) {
                                        char next = line.charAt(cursor + 1);
                                        switch (next) {
                                            case '0' -> {
                                                fieldBuilder.append('\0');
                                                cursor += 2;
                                            }
                                            case 'n' -> {
                                                fieldBuilder.append('\n');
                                                cursor += 2;
                                            }
                                            case 'r' -> {
                                                fieldBuilder.append('\r');
                                                cursor += 2;
                                            }
                                            case 't' -> {
                                                fieldBuilder.append('\t');
                                                cursor += 2;
                                            }
                                            case '\\' -> {
                                                fieldBuilder.append('\\');
                                                cursor += 2;
                                            }
                                            case 'c' -> {
                                                fieldBuilder.append(',');
                                                cursor += 2;
                                            }
                                            case 'u' -> {
                                                if (cursor < end - 5) {
                                                    try {
                                                        int codePoint = Integer.parseInt(line, cursor + 2, cursor + 6, 16);
                                                        fieldBuilder.append((char) codePoint);
                                                        cursor += 6;
                                                    } catch (NumberFormatException e) {
                                                        LOG.warning("Invalid line in " + assetsPath + ": " + line);
                                                        continue loop;
                                                    }
                                                } else {
                                                    fieldBuilder.append(c);
                                                    cursor++;
                                                }
                                            }
                                            default -> {
                                                LOG.warning("Invalid line in " + assetsPath + ": " + line);
                                                continue loop;
                                            }
                                        }
                                    } else {
                                        fieldBuilder.append(c);
                                        cursor++;
                                    }


                                } else {
                                    fieldBuilder.append(c);
                                    cursor++;
                                }
                            }
                            field = fieldBuilder.toString();
                        }
                    }

                    fields[fieldIndex] = field;
                    offset = end + 1;
                }

                try {
                    I info = parseAddonProjectInfo(fields);
                    info.setKeepAlive(result);
                    infos.add(info);
                } catch (Exception e) {
                    LOG.warning("Failed to parse line in " + assetsPath + ": " + line, e);
                }
            }

            infos.trimToSize();
            return Collections.unmodifiableList(infos);
        }
    }

    public @Unmodifiable List<I> getInfos() {
        SoftReference<@Unmodifiable List<I>> infosRef = this.infosRef;
        List<I> infos = infosRef != null ? infosRef.get() : null;
        if (infos != null)
            return infos;

        lock.lock();
        try {
            infosRef = this.infosRef;
            infos = infosRef != null ? infosRef.get() : null;
            if (infos != null)
                return infos;

            try {
                infos = loadInfos();
            } catch (IOException e) {
                LOG.warning("Failed to load addon project info from " + assetsPath, e);
                infos = List.of();
            }
            this.infosRef = new SoftReference<>(infos);
            return infos;
        }  finally {
            lock.unlock();
        }
    }
}
