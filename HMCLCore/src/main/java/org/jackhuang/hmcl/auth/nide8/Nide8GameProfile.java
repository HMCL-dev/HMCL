package org.jackhuang.hmcl.auth.nide8;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.auth.yggdrasil.PropertyMapSerializer;
import org.jackhuang.hmcl.util.gson.UUIDTypeAdapter;
import org.jackhuang.hmcl.util.gson.Validation;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Nide8GameProfile implements Validation {
    @JsonAdapter(PropertyMapSerializer.class)
    private final Map<String, String> properties;
    @JsonAdapter(UUIDTypeAdapter.class)
    private final UUID id;
    private final String name;
    private final String serverID;

    public Nide8GameProfile(String serverID, UUID id, String name, Map<String, String> properties) {
        this.id = id;
        this.name = name;
        this.serverID = serverID;
        this.properties = Objects.requireNonNull(properties);
    }

    public Nide8GameProfile(Nide8GameProfile profile, Map<String, String> properties) {
        this(profile.getServerID(), profile.getId(), profile.getName(), properties);
    }

    public Nide8GameProfile(String serverID, UUID id, String name) {
        this.id = id;
        this.name = name;
        this.serverID = serverID;
        properties = null;
    }

    public String getServerID() {
        return serverID;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        Validation.requireNonNull(id, "Game profile id cannot be null");
        Validation.requireNonNull(name, "Game profile name cannot be null");
    }
}
