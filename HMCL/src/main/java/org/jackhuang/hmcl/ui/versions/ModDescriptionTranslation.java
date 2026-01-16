package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public final class ModDescriptionTranslation {
    public static final String API_ROOT = "https://mod.mcimirror.top/translate/";

    private static final Map<RemoteMod, Response> CACHE = new ConcurrentHashMap<>();

    private ModDescriptionTranslation() {
    }

    public static Task<Response> translate(RemoteMod mod) {
        if (CACHE.containsKey(mod)) {
            return Task.completed(CACHE.get(mod));
        }
        String url;
        if (mod.getData() instanceof CurseAddon) {
            url = API_ROOT + "curseforge/" + mod.getSlug();
        } else if (mod.getData() instanceof ModrinthRemoteModRepository.ProjectSearchResult project) {
            url = API_ROOT + "modrinth/" + project.getProjectId();
        } else if (mod.getData() instanceof ModrinthRemoteModRepository.Project project) {
            url = API_ROOT + "modrinth/" + project.getId();
        } else {
            throw new AssertionError();
        }
        return new GetTask(url).thenGetJsonAsync(Response.class).thenApplyAsync(response -> {
            if (response != null) CACHE.put(mod, response);
            return response;
        });
    }

    public static boolean enabled() {
        return config().getmodDescriptionTranslation() && I18n.isUseChinese();
    }

    @JsonSerializable
    public static class Response {
        String translated;
    }
}
