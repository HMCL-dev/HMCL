/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.gson.Validation;

/**
 *
 * @author huangyuhui
 */
@Immutable
public class DownloadInfo implements Validation {

    @SerializedName("url")
    private final String url;
    @SerializedName("sha1")
    private final String sha1;
    @SerializedName("size")
    private final int size;

    public DownloadInfo() {
        this("");
    }

    public DownloadInfo(String url) {
        this(url, null);
    }

    public DownloadInfo(String url, String sha1) {
        this(url, sha1, 0);
    }

    public DownloadInfo(String url, String sha1, int size) {
        this.url = url;
        this.sha1 = sha1;
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public String getSha1() {
        return sha1;
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("url", url).append("sha1", sha1).append("size", size).toString();
    }

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(url))
            throw new JsonParseException("DownloadInfo url can not be null");
    }
}
