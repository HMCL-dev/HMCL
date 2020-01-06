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
package org.jackhuang.hmcl.event;

import org.jackhuang.hmcl.util.ToStringBuilder;

import java.io.File;

/**
 * This event gets fired when json of a game version is malformed. You can do something here.
 * auto making up for the missing json, don't forget to set result to {@link Event.Result#ALLOW}.
 * and even asking for removing the redundant version folder.
 *
 * The result ALLOW means you have corrected the json.
 */
public final class GameJsonParseFailedEvent extends Event {
    private final String version;
    private final File jsonFile;

    /**
     *
     * @param source {@link org.jackhuang.hmcl.game.DefaultGameRepository}
     * @param jsonFile the minecraft.json file.
     * @param version the version name
     */
    public GameJsonParseFailedEvent(Object source, File jsonFile, String version) {
        super(source);
        this.version = version;
        this.jsonFile = jsonFile;
    }

    public File getJsonFile() {
        return jsonFile;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("source", source)
                .append("jsonFile", jsonFile)
                .append("version", version)
                .toString();
    }
}
