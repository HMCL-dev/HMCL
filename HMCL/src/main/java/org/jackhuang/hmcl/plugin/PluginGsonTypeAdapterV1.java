package org.jackhuang.hmcl.plugin;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.plugin.api.PluginInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PluginGsonTypeAdapterV1 extends TypeAdapter<PluginInfo> {
    private String pluginId = null;
    private String pluginName = null;
    private String pluginVersion = null;
    private List<String> pluginAuthors = new ArrayList<>();
    private Map<String, String> pluginEntrypoints = new HashMap<>();
    private int manifestVersion = -1;

    @Override
    public void write(JsonWriter out, PluginInfo value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PluginInfo read(JsonReader in) throws IOException {
        PluginGsonTypeAdapterV1 storage = new PluginGsonTypeAdapterV1();
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "manifest_version": {
                    storage.manifestVersion = in.nextInt();
                    if (storage.manifestVersion != 1) {
                        throw new IOException();
                    }
                    break;
                }
                case "name": {
                    storage.pluginName = in.nextString();
                    break;
                }
                case "id": {
                    storage.pluginId = in.nextString();
                    break;
                }
                case "version": {
                    storage.pluginVersion = in.nextString();
                    break;
                }
                case "author": {
                    in.beginArray();
                    while (in.hasNext()) {
                        storage.pluginAuthors.add(in.nextString());
                    }
                    in.endArray();
                    break;
                }
                case "entrypoints": {
                    in.beginObject();
                    while (in.hasNext()) {
                        storage.pluginEntrypoints.put(in.nextName(), in.nextString());
                    }
                    in.endObject();
                    break;
                }
            }
        }
        in.endObject();

        if (storage.manifestVersion == -1) {
            throw new IOException();
        }
        if (storage.pluginId == null) {
            throw new IOException();
        }
        if (storage.pluginName == null) {
            throw new IOException();
        }
        if (storage.pluginVersion == null) {
            throw new IOException();
        }
        if (storage.pluginAuthors.size() == 0) {
            throw new IOException();
        }

        return new PluginInfo(storage.pluginId, storage.pluginName, storage.pluginVersion, storage.pluginAuthors, storage.pluginEntrypoints);
    }
}
