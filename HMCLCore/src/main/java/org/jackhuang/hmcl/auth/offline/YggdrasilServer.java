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
package org.jackhuang.hmcl.auth.offline;

import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.auth.yggdrasil.CompleteGameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.auth.yggdrasil.TextureType;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.HttpServer;
import org.jackhuang.hmcl.util.io.IOUtils;

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public class YggdrasilServer extends HttpServer {

    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<UUID, Character> charactersByUuid = new HashMap<>();
    private final Map<String, Character> charactersByName = new HashMap<>();

    public YggdrasilServer(int port) {
        super(port);

        addRoute(Method.GET, Pattern.compile("^/$"), this::root);
        addRoute(Method.GET, Pattern.compile("/status"), this::status);
        addRoute(Method.POST, Pattern.compile("/api/profiles/minecraft"), this::profiles);
        addRoute(Method.GET, Pattern.compile("/sessionserver/session/minecraft/hasJoined"), this::hasJoined);
        addRoute(Method.POST, Pattern.compile("/sessionserver/session/minecraft/join"), this::joinServer);
        addRoute(Method.GET, Pattern.compile("/sessionserver/session/minecraft/profile/(?<uuid>[a-f0-9]{32})"), this::profile);
        addRoute(Method.GET, Pattern.compile("/textures/(?<hash>[a-f0-9]{64})"), this::texture);
    }

    private Response root(Request request) {
        return ok(mapOf(
                pair("skinDomains", Collections.emptyList()),
                pair("meta", mapOf(
                        pair("serverName", "HMCL Offline Account Skin/Cape Server"),
                        pair("implementationName", "HMCL"),
                        pair("implementationVersion", "1.0"),
                        pair("feature.non_email_login", true)
                ))
        ));
    }

    private Response status(Request request) {
        return ok(mapOf(
                pair("user.count", charactersByUuid.size()),
                pair("token.count", 0),
                pair("pendingAuthentication.count", 0)
        ));
    }

    private Response profiles(Request request) throws IOException {
        String body = IOUtils.readFullyAsString(request.getSession().getInputStream(), StandardCharsets.UTF_8);
        List<String> names = JsonUtils.fromNonNullJson(body, new TypeToken<List<String>>() {
        }.getType());
        return ok(names.stream().distinct()
                .map(this::findCharacterByName)
                .flatMap(Lang::toStream)
                .map(Character::toSimpleResponse)
                .collect(Collectors.toList()));
    }

    private Response hasJoined(Request request) {
        if (!request.getQuery().containsKey("username")) {
            return badRequest();
        }
        return findCharacterByName(request.getQuery().get("username"))
                .map(character -> ok(character.toCompleteResponse(getRootUrl())))
                .orElseGet(HttpServer::noContent);
    }

    private Response joinServer(Request request) {
        return noContent();
    }

    private Response profile(Request request) {
        String uuid = request.getPathVariables().group("uuid");

        return findCharacterByUuid(UUIDTypeAdapter.fromString(uuid))
                .map(character -> ok(character.toCompleteResponse(getRootUrl())))
                .orElseGet(HttpServer::noContent);
    }

    private Response texture(Request request) {
        String hash = request.getPathVariables().group("hash");

        if (textures.containsKey(hash)) {
            Texture texture = textures.get(hash);
            Response response = newFixedLengthResponse(Response.Status.OK, "image/png", texture.getInputStream(), texture.getLength());
            response.addHeader("Etag", String.format("\"%s\"", hash));
            response.addHeader("Cache-Control", "max-age=2592000, public");
            return response;
        } else {
            return notFound();
        }
    }

    private Optional<Character> findCharacterByUuid(UUID uuid) {
        return Optional.ofNullable(charactersByUuid.get(uuid));
    }

    private Optional<Character> findCharacterByName(String uuid) {
        return Optional.ofNullable(charactersByName.get(uuid));
    }

    private static String computeTextureHash(BufferedImage img) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        int width = img.getWidth();
        int height = img.getHeight();
        byte[] buf = new byte[4096];

        putInt(buf, 0, width);
        putInt(buf, 4, height);
        int pos = 8;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                putInt(buf, pos, img.getRGB(x, y));
                if (buf[pos + 0] == 0) {
                    buf[pos + 1] = buf[pos + 2] = buf[pos + 3] = 0;
                }
                pos += 4;
                if (pos == buf.length) {
                    pos = 0;
                    digest.update(buf, 0, buf.length);
                }
            }
        }
        if (pos > 0) {
            digest.update(buf, 0, pos);
        }

        byte[] sha256 = digest.digest();
        return String.format("%0" + (sha256.length << 1) + "x", new BigInteger(1, sha256));
    }

    private static void putInt(byte[] array, int offset, int x) {
        array[offset + 0] = (byte) (x >> 24 & 0xff);
        array[offset + 1] = (byte) (x >> 16 & 0xff);
        array[offset + 2] = (byte) (x >> 8 & 0xff);
        array[offset + 3] = (byte) (x >> 0 & 0xff);
    }

    private Texture loadTexture(InputStream in) throws IOException {
        if (in == null) return null;
        BufferedImage img = ImageIO.read(in);
        if (img == null) {
            throw new IIOException("No image found");
        }

        String hash = computeTextureHash(img);

        Texture existent = textures.get(hash);
        if (existent != null) {
            return existent;
        }

        String url = String.format("http://127.0.0.1:%d/textures/%s", getListeningPort(), hash);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ImageIO.write(img, "png", buf);
        Texture texture = new Texture(hash, buf.toByteArray(), url);

        existent = textures.putIfAbsent(hash, texture);

        if (existent != null) {
            return existent;
        }
        return texture;
    }

    public Texture loadTexture(String url) throws IOException {
        if (url == null) return null;
        return loadTexture(new URL(url).openStream());
    }

    public void addCharacter(Character character) {
        charactersByUuid.put(character.getUUID(), character);
        charactersByName.put(character.getName(), character);
    }

    public enum ModelType {
        STEVE("default"),
        ALEX("slim");

        private String modelName;

        ModelType(String modelName) {
            this.modelName = modelName;
        }

        public String getModelName() {
            return modelName;
        }
    }

    public static class Character {
        private final UUID uuid;
        private final String name;
        private final ModelType model;
        private final Map<TextureType, Texture> textures;

        public Character(UUID uuid, String name, ModelType model, Map<TextureType, Texture> textures) {
            this.uuid = uuid;
            this.name = name;
            this.model = model;
            this.textures = textures;
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public ModelType getModel() {
            return model;
        }

        public Map<TextureType, Texture> getTextures() {
            return textures;
        }

        private Map<String, Object> createKeyValue(String key, String value) {
            return mapOf(
                    pair("name", key),
                    pair("value", value)
            );
        }

        public GameProfile toSimpleResponse() {
            return new GameProfile(uuid, name);
        }

        public CompleteGameProfile toCompleteResponse(String rootUrl) {
            Map<TextureType, Object> realTextures = new HashMap<>();
            for (Map.Entry<TextureType, Texture> textureEntry : textures.entrySet()) {
                if (textureEntry.getValue() == null) continue;
                realTextures.put(textureEntry.getKey(), mapOf(pair("url", rootUrl + "/textures/" + textureEntry.getValue().hash)));
            }

            Map<String, Object> textureResponse = mapOf(
                    pair("timestamp", System.currentTimeMillis()),
                    pair("profileId", uuid),
                    pair("profileName", name),
                    pair("textures", realTextures)
            );

            return new CompleteGameProfile(uuid, name, mapOf(
                    pair("textures", new String(
                            Base64.getEncoder().encode(
                                    JsonUtils.GSON.toJson(textureResponse).getBytes(StandardCharsets.UTF_8)
                            ), StandardCharsets.UTF_8)
                    )
            ));
        }
    }

    private static class Texture {
        private final String hash;
        private final byte[] data;
        private final String url;

        public Texture(String hash, byte[] data, String url) {
            this.hash = requireNonNull(hash);
            this.data = requireNonNull(data);
            this.url = requireNonNull(url);
        }

        public String getUrl() {
            return url;
        }

        public InputStream getInputStream() {
            return new ByteArrayInputStream(data);
        }

        public int getLength() {
            return data.length;
        }
    }

}
