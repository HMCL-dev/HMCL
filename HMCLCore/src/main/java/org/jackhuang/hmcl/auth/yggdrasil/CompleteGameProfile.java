// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.auth.yggdrasil;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.jackhuang.hmcl.util.Immutable;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.JsonAdapter;

/**
 * @author yushijinhun
 */
@Immutable
public class CompleteGameProfile extends GameProfile {

    @JsonAdapter(PropertyMapSerializer.class)
    private final Map<String, String> properties;

    public CompleteGameProfile(UUID id, String name, Map<String, String> properties) {
        super(id, name);
        this.properties = Objects.requireNonNull(properties);
    }

    public CompleteGameProfile(GameProfile profile, Map<String, String> properties) {
        this(profile.getId(), profile.getName(), properties);
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    @Override
    public void validate() throws JsonParseException {
        super.validate();

        if (properties == null)
            throw new JsonParseException("Game profile properties cannot be null");
    }
}
