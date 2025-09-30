package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;

public final class ModDescriptionTranslatons {
    public static Task<Response> translate(RemoteMod mod) {
        return Task.supplyAsync(Schedulers.io(), () -> {
            HttpRequest request = null;
            if (mod.getData() instanceof CurseAddon) {
                request = HttpRequest.GET("https://mod.mcimirror.top/translate/curseforge/" + mod.getSlug());
            } else if (mod.getData() instanceof ModrinthRemoteModRepository.ProjectSearchResult project) {
                request = HttpRequest.GET("https://mod.mcimirror.top/translate/modrinth/" + project.getProjectId());
            } else if (mod.getData() instanceof ModrinthRemoteModRepository.Project project) {
                request = HttpRequest.GET("https://mod.mcimirror.top/translate/modrinth/" + project.getId());
            }
            assert request != null;
            String result = request.getString();
            return JsonUtils.fromNonNullJson(result, Response.class);
        });
    }

    @JsonSerializable
    public static class Response {
        String translated;
    }
}
