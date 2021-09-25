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
import org.jackhuang.hmcl.auth.yggdrasil.GameProfile;
import org.jackhuang.hmcl.util.KeyUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.io.HttpServer;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;

public class YggdrasilServer extends HttpServer {

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
                pair("signaturePublickey", KeyUtils.toPEMPublicKey(getSignaturePublicKey())),
                pair("skinDomains", Arrays.asList(
                        "127.0.0.1",
                        "localhost"
                )),
                pair("meta", mapOf(
                        pair("serverName", "HMCL"),
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
        String body = IOUtils.readFullyAsString(request.getSession().getInputStream(), UTF_8);
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

        if (Texture.hasTexture(hash)) {
            Texture texture = Texture.getTexture(hash);
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

    public void addCharacter(Character character) {
        charactersByUuid.put(character.getUUID(), character);
        charactersByName.put(character.getName(), character);
    }

    public static class Character {
        private final UUID uuid;
        private final String name;
        private final Skin.LoadedSkin skin;

        public Character(UUID uuid, String name, Skin.LoadedSkin skin) {
            this.uuid = uuid;
            this.name = name;
            this.skin = Objects.requireNonNull(skin);
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getName() {
            return name;
        }

        public GameProfile toSimpleResponse() {
            return new GameProfile(uuid, name);
        }

        public Object toCompleteResponse(String rootUrl) {
            Map<String, Object> realTextures = new HashMap<>();
            if (skin.getSkin() != null) {
                realTextures.put("SKIN", mapOf(pair("url", rootUrl + "/textures/" + skin.getSkin().getHash())));
            }
            if (skin.getCape() != null) {
                realTextures.put("CAPE", mapOf(pair("url", rootUrl + "/textures/" + skin.getSkin().getHash())));
            }

            Map<String, Object> textureResponse = mapOf(
                    pair("timestamp", System.currentTimeMillis()),
                    pair("profileId", uuid),
                    pair("profileName", name),
                    pair("textures", realTextures)
            );

            return mapOf(
                    pair("id", uuid),
                    pair("name", name),
                    pair("properties", properties(true,
                            pair("textures", new String(
                                    Base64.getEncoder().encode(
                                            JsonUtils.GSON.toJson(textureResponse).getBytes(UTF_8)
                                    ), UTF_8))))
            );
        }
    }

    // === Signature ===

    private static final KeyPair keyPair = KeyUtils.generateKey();

    public static PublicKey getSignaturePublicKey() {
        return keyPair.getPublic();
    }

    private static String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(keyPair.getPrivate(), new SecureRandom());
            signature.update(data.getBytes(UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    // === properties ===

    @SafeVarargs
    public static List<?> properties(Map.Entry<String, String>... entries) {
        return properties(false, entries);
    }

    @SafeVarargs
    public static List<?> properties(boolean sign, Map.Entry<String, String>... entries) {
        return Stream.of(entries)
                .map(entry -> {
                    LinkedHashMap<String, String> property = new LinkedHashMap<>();
                    property.put("name", entry.getKey());
                    property.put("value", entry.getValue());
                    if (sign) {
                        property.put("signature", sign(entry.getValue()));
                    }
                    return property;
                })
                .collect(Collectors.toList());
    }

}
