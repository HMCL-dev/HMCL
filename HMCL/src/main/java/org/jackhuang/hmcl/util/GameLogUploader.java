package org.jackhuang.hmcl.util;

import com.google.gson.Gson;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class GameLogUploader {

    public enum HostingPlatform {
        /*
         * HTTP: POST https://api.mclo.gs/1/log
         * Data Type: application/x-www-form-urlencoded
         * Field: content
         * Type: string
         * Description: The raw log file content as string. Maximum length is 10MiB and 25k lines, will be shortened if necessary.
         */
        MCLOGS("mclo.gs", "https://mclo.gs/", "https://docs.mclo.gs/api/v1/log"),
        /*
         * HTTP: POST https://file.io/
         * Data Type: multipart/form-data
         * Fields:
         *     file string($binary)
               expires
               maxDownloads integer
               autoDelete boolean
         */
        FILE_IO("file.io", "https://www.file.io/", "https://www.file.io/developers")



        ;

        private final String name;
        private final String url;
        private final String documents;

        HostingPlatform(String name, String url, String documents) {
            this.name = name;
            this.url = url;
            this.documents = documents;
        }
    }

    public static class UploadResult {
        private String url;
        private String id;
        private String raw;

        public UploadResult(String url, String id, String raw) {
            this.url = url;
            this.id = id;
            this.raw = raw;
        }

        public String getUrl() {
            return url;
        }

        public String getId() {
            return id;
        }

        public String getDocuments() {
            return raw;
        }
    }

    public static UploadResult upload(HostingPlatform platform, String content) {
        return upload(platform, content.getBytes(StandardCharsets.UTF_8));
    }

    public static UploadResult upload(HostingPlatform platform, byte[] content) {
        Gson gson = new Gson();
        try {
            switch (platform) {
                case MCLOGS:
                    HttpRequest.HttpPostRequest request = HttpRequest.POST("https://api.mclo.gs/1/log");
                    request.header("Content-Type", "application/x-www-form-urlencoded");
                    HashMap<String, String> payload = new HashMap<>();
                    //编码
                    payload.put("content", new String(content, StandardCharsets.UTF_8));
                    request.form(payload);

                    String response = request.getString();
                    Map<String, Object> json = gson.fromJson(response, Map.class);
                    if (!json.containsKey("success")) {
                        return null;
                    }
                    if ((boolean) json.get("success")) {
                        return new UploadResult(
                                (String) json.get("url"),
                                (String) json.get("id"),
                                (String) json.get("raw")
                        );
                    }
                    return null;
                default:
                    return null;
            }
        } catch (IOException ex) {
            Logger.LOG.error("Failed to upload game log", ex);
            return null;
        }
    }
}
