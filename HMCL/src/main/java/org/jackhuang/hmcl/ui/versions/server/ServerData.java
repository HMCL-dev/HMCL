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
package org.jackhuang.hmcl.ui.versions.server;

import com.github.steveice10.opennbt.tag.builtin.ByteTag;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public record ServerData(
        boolean acceptTextures,
        boolean hidden,
        BufferedImage icon,
        String ip,
        String name
) {

    public static ServerData fromStorage(Map<Object, Object> storage) {
        return new ServerData(
                Boolean.TRUE.equals(storage.get("acceptTextures")),
                Boolean.TRUE.equals(storage.get("hidden")),
                storage.get("icon") instanceof String s ? parseImage(s) : null,
                storage.get("ip") instanceof String s ? s : null,
                storage.get("name") instanceof String s ? s : null
        );
    }

    public static ServerData fromCompoundTag(CompoundTag tag) {
        return new ServerData(
                tag.get("acceptTextures") instanceof ByteTag bt && bt.getValue() != 0,
                tag.get("hidden") instanceof ByteTag bt && bt.getValue() != 0,
                tag.get("icon") instanceof StringTag st ? parseImage(st.getValue()) : null,
                tag.get("ip") instanceof StringTag st ? st.getValue() : null,
                tag.get("name") instanceof StringTag st ? st.getValue() : null
        );
    }

    public static BufferedImage parseImage(String imageBase64String) {
        if (imageBase64String == null || imageBase64String.isEmpty()) {
            return null;
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(imageBase64String))) {
            return ImageIO.read(bais);
        } catch (IOException e) {
            LOG.warning("Failed to decode server icon", e);
            return null;
        }
    }

    public static String encodeImage(Image image) {
        if (image == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write((BufferedImage) image, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            LOG.warning("Failed to encode server icon", e);
            return null;
        }
    }

    public static ServerData createServerData(String name, String ip) {
        return new ServerData(false, false, null, ip, name);
    }

    public void writeToCompoundTag(CompoundTag tag) {
        tag.put(new ByteTag("acceptTextures", (byte) (acceptTextures ? 1 : 0)));
        tag.put(new ByteTag("hidden", (byte) (hidden ? 1 : 0)));
        if (icon != null) tag.put(new StringTag("icon", encodeImage(icon)));
        if (ip != null) tag.put(new StringTag("ip", ip));
        if (name != null) tag.put(new StringTag("name", name));
    }

    public Map<Object, Object> toSerializeMap() {
        Map<Object, Object> map = new HashMap<>();
        map.put("acceptTextures", acceptTextures);
        if (icon != null) map.put("icon", encodeImage(icon));
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
}