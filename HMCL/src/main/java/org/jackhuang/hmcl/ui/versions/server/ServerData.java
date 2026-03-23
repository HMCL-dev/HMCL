/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.versions.server;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.ui.image.ImageUtils;
import org.jackhuang.hmcl.util.Lazy;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ServerData {
    final boolean acceptTextures;
    final boolean hidden;
    final String icon;
    final String ip;
    final String name;

    final Lazy<Image> iconImage;

    public ServerData(
            boolean acceptTextures,
            boolean hidden,
            String icon,
            String ip,
            String name
    ) {
        this.acceptTextures = acceptTextures;
        this.hidden = hidden;
        this.icon = icon;
        this.ip = ip;
        this.name = name;

        this.iconImage = new Lazy<>(() -> parseImage(icon));
    }

    public static ServerData fromStorage(Map<Object, Object> storage) {
        return new ServerData(
                Boolean.TRUE.equals(storage.get("acceptTextures")),
                Boolean.TRUE.equals(storage.get("hidden")),
                storage.get("icon") instanceof String s ? s : null,
                storage.get("ip") instanceof String s ? s : null,
                storage.get("name") instanceof String s ? s : null
        );
    }

    public static ServerData fromCompoundTag(CompoundTag tag) {
        return new ServerData(
                tag.get("acceptTextures") instanceof ByteTag bt && bt.getValue() != 0,
                tag.get("hidden") instanceof ByteTag bt && bt.getValue() != 0,
                tag.get("icon") instanceof StringTag st ? st.getValue() : null,
                tag.get("ip") instanceof StringTag st ? st.getValue() : null,
                tag.get("name") instanceof StringTag st ? st.getValue() : null
        );
    }

    public static Image parseImage(String imageBase64String) {
        if (imageBase64String == null || imageBase64String.isEmpty()) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(imageBase64String))) {
            // png format.
            return ImageUtils.DEFAULT.load(bais, 0, 0, true, true);
        } catch (Exception e) {
            LOG.warning("Failed to decode server icon", e);
            return null;
        }
    }

    public static ServerData createServerData(String name, String ip) {
        return new ServerData(false, false, null, ip, name);
    }

    public void writeToCompoundTag(CompoundTag tag) {
        tag.put(new ByteTag("acceptTextures", (byte) (acceptTextures ? 1 : 0)));
        tag.put(new ByteTag("hidden", (byte) (hidden ? 1 : 0)));
        if (icon != null) tag.put(new StringTag("icon", icon));
        if (ip != null) tag.put(new StringTag("ip", ip));
        if (name != null) tag.put(new StringTag("name", name));
    }

    public Map<Object, Object> toSerializeMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put("acceptTextures", acceptTextures);
        if (icon != null) map.put("icon", icon);
        if (ip != null) map.put("ip", ip);
        if (name != null) map.put("name", name);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServerData that = (ServerData) o;
        return Objects.equals(ip, that.ip) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, name);
    }

    @Override
    public String toString() {
        return "ServerData[" +
                "acceptTextures=" + acceptTextures + ", " +
                "hidden=" + hidden + ", " +
                "icon=" + icon + ", " +
                "ip=" + ip + ", " +
                "name=" + name + ']';
    }

}
