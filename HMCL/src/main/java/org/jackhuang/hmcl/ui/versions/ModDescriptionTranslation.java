package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.task.CacheFileTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.I18n;

import java.util.Locale;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public final class ModDescriptionTranslation {
    public static final String API_ROOT = "https://mod.mcimirror.top/translate/";

    private ModDescriptionTranslation() {
    }

    public static Task<Response> translate(RemoteMod mod) {
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
        return new CacheFileTask(url).thenApplyAsync(path -> JsonUtils.fromJsonFile(path, Response.class));
    }

    public static boolean enabled() {
        return config().getmodDescriptionTranslation() && I18n.isUseChinese();
    }

    @JsonSerializable
    public static class Response {
        String translated;
    }
}