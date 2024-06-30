package org.jackhuang.hmcl.util;

import com.google.gson.Gson;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        public String getRaw() {
            return raw;
        }
    }

    public static UploadResult upload(HostingPlatform platform, Path filepath, String content) {
        return upload(platform, filepath, content.getBytes(StandardCharsets.UTF_8));
    }

    public static UploadResult upload(HostingPlatform platform, Path filepath, byte[] content) {
        Gson gson = new Gson();
        try {
            switch (platform) {
                case MCLOGS: {
                    HttpRequest.HttpPostRequest request = HttpRequest.POST("https://api.mclo.gs/1/log");
                    request.header("Content-Type", "application/x-www-form-urlencoded");
                    HashMap<String, String> payload = new HashMap<>();

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
                }
                case FILE_IO: {
                    String boundary = Long.toHexString(System.currentTimeMillis()); // 创建一个唯一的边界字符串
                    String LINE_FEED = "\r\n";
                    URL url = new URL("https://file.io/");

                    HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();

                    httpConn.setDoOutput(true);
                    httpConn.setDoInput(true);
                    httpConn.setRequestMethod("POST");
                    httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    httpConn.setRequestProperty("Accept", "application/json");

                    FileInputStream fileInputStream = new FileInputStream(filepath.toFile());
                    OutputStream outputStream = httpConn.getOutputStream();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true);

                    writer.append("--").append(boundary).append(LINE_FEED);
                    writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filepath.getFileName().toString()).append("\"").append(LINE_FEED);
                    writer.append("Content-Type: application/octet-stream").append(LINE_FEED);
                    writer.append(LINE_FEED);
                    writer.flush();
                    // outputStream.write(content);
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.flush();
                    writer.append(LINE_FEED);

                    writer.append("--" + boundary).append(LINE_FEED);
                    writer.append("Content-Disposition: form-data; name=\"autoDelete\"").append(LINE_FEED);
                    writer.append(LINE_FEED);
                    writer.append("true").append(LINE_FEED);

                    writer.append("--" + boundary).append(LINE_FEED);
                    writer.append("Content-Disposition: form-data; name=\"expires\"").append(LINE_FEED);
                    writer.append(LINE_FEED);
                    writer.append("7d").append(LINE_FEED);

                    writer.append("--").append(boundary).append("--").append(LINE_FEED).append(LINE_FEED);
                    writer.flush();

                    int responseCode = httpConn.getResponseCode();
                    Logger.LOG.info("Http Response Code: " + responseCode);
                    if(responseCode != 200){
                        BufferedReader err = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()));
                        String errLine;
                        StringBuilder errResponse = new StringBuilder();
                        while ((errLine = err.readLine()) != null) {
                            errResponse.append(errLine);
                        }
                        Logger.LOG.error("Error response from file.io: " + errResponse);
                        return null;
                    }


                    BufferedReader in = new BufferedReader(new InputStreamReader(httpConn.getInputStream()));
                    String inputLine;
                    StringBuilder response = new StringBuilder();

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                    Logger.LOG.info("Response from file.io: " + response);
                    in.close();

                    Map<String, Object> json = gson.fromJson(response.toString(), Map.class);
                    if (!json.containsKey("success")) {
                        return null;
                    }
                    if ((boolean) json.get("success")) {
                        return new UploadResult(
                                (String) json.get("link"),
                                (String) json.get("key"),
                                (String) json.get("expires")
                        );
                    }
                    return null;
                }
                default:
                    return null;
            }
        } catch (Exception ex) {
            Logger.LOG.error("Failed to upload game log", ex);
            return null;
        }
    }
}
