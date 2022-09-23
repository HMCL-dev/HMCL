package org.jackhuang.hmcl.auth.nide8;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;
import org.jackhuang.hmcl.auth.yggdrasil.PropertyMapSerializer;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class Nide8User implements Validation {

    private final String id;

    @Nullable
    @JsonAdapter(PropertyMapSerializer.class)
    private final Map<String, String> properties;

    public Nide8User(String id) {
        this(id, null);
    }

    public Nide8User(String id, @Nullable Map<String, String> properties) {
        this.id = id;
        this.properties = properties;
    }

    public String getId() {
        return id;
    }

    @Nullable
    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        if (StringUtils.isBlank(id))
            throw new JsonParseException("User id cannot be empty.");
    }
}
