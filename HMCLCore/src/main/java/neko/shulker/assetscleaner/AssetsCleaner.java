package neko.shulker.assetscleaner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.lang.reflect.Type;

public class AssetsCleaner {
    public static void clean(String indexesDirectory, String assetsDirectory) {
        // 用于存储所有 index 中 hash 键值的 Map
        Map<String, Map<String, Object>> hashMap = new HashMap<>();

        try {
            // 遍历 indexesDirectory 目录下的所有 JSON 文件
            Path dirPath = Paths.get(indexesDirectory);
            Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(jsonFile -> {
                        try (FileReader reader = new FileReader(jsonFile.toString())) {
                            // 解析每个 JSON 文件
                            Gson gson = new Gson();
                            Type type = new TypeToken<Map<String, Map<String, Map<String, Object>>>>() {}.getType();
                            Map<String, Map<String, Map<String, Object>>> jsonData = gson.fromJson(reader, type);

                            // 提取 hash 的键值
                            Map<String, Map<String, Object>> objects = jsonData.get("objects");
                            if (objects != null) {
                                for (Map.Entry<String, Map<String, Object>> entry : objects.entrySet()) {
                                    String filePath = entry.getKey();
                                    Map<String, Object> fileData = entry.getValue();
                                    String hash = (String) fileData.get("hash");
                                    if (hash != null && !hashMap.containsKey(hash)) {
                                        hashMap.put(hash, fileData);
                                    }
                                }
                            }
                        } catch (IOException e) {
                            System.err.println("[AssetsCleaner]: Error reading JSON file: " + jsonFile);
                            e.printStackTrace();
                        }
                    });

            // 输出 hashMap 中保存的所有 hash 值
            System.out.println("[AssetsCleaner]: 所有 hash 值:");
            for (String hash : hashMap.keySet()) {
                System.out.println(hash);
            }

            // 遍历目标处理目录文件
            Path assetsDirPath = Paths.get(assetsDirectory);
            Files.walk(assetsDirPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        // 判断文件是否在 hashMap 中
                        if (!hashMap.containsKey(fileName)) {
                            // 如果不在则删除
                            try {
                                Files.delete(file);
                                System.out.println("[AssetsCleaner]: Deleted file: " + file);
                            } catch (IOException e) {
                                System.err.println("[AssetsCleaner]: Error deleting file: " + file);
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}