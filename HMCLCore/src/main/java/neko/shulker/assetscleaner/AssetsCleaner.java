package neko.shulker.assetscleaner;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AssetsCleaner {

    private AssetsCleaner() {
        throw new UnsupportedOperationException();
    }

    private static List<String> getIndexes(String versionsDirectory) {
        List<String> indexes = new ArrayList<>();
        File versionsDir = new File(versionsDirectory);

        // 遍历 versionsDirectory 中的所有文件夹
        if (versionsDir.exists() && versionsDir.isDirectory()) {
            File[] versionFolders = versionsDir.listFiles(File::isDirectory);
            if (versionFolders != null) {
                for (File versionFolder : versionFolders) {
                    String versionName = versionFolder.getName();
                    File jsonFile = new File(versionFolder, versionName + ".json");

                    if (jsonFile.exists() && jsonFile.isFile()) {
                        try (Reader reader = new FileReader(jsonFile)) {
                            // 解析 JSON 文件
                            Gson gson = new Gson();
                            Map<String, Object> versionData = gson.fromJson(reader, Map.class);

                            // 提取 "assets" 的值
                            Object assetsValue = versionData.get("assets");
                            if (assetsValue instanceof String) {
                                String assets = (String) assetsValue;
                                indexes.add(assets);
                            }
                        } catch (IOException e) {
                            System.err.println("[AssetsCleaner]: Error reading JSON file: " + jsonFile);
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        return indexes;
    }

    public static void clean(String repoDirectory) {
        // 构造路径
        String versionsDirectory = Paths.get(repoDirectory, "versions").toString();
        String indexesDirectory = Paths.get(repoDirectory, "assets", "indexes").toString();
        String objectsDirectory = Paths.get(repoDirectory, "assets", "objects").toString();

        // 获取 indexes
        List<String> requiredIndexes = getIndexes(versionsDirectory);

        // 用于存储所有 index 中 hash 键值的 Map
        Map<String, Map<String, Object>> hashMap = new HashMap<>();

        try {
            // 遍历 indexesDirectory 目录下的所有 JSON 文件
            File indexesDir = new File(indexesDirectory);
            if (!indexesDir.exists() || !indexesDir.isDirectory()) {
                System.err.println("[AssetsCleaner]: Indexes directory does not exist: " + indexesDir);
                return;
            }

            // 获取 indexesDirectory 中的所有 JSON 文件
            File[] indexFiles = indexesDir.listFiles((FilenameFilter) (dir, name) -> name.endsWith(".json"));
            if (indexFiles != null) {
                for (File indexFile : indexFiles) {
                    String indexFileName = indexFile.getName();
                    String indexName = indexFileName.substring(0, indexFileName.lastIndexOf('.'));

                    // 检查该 index 是否在 requiredIndexes 中
                    if (!requiredIndexes.contains(indexName)) {
                        // 如果不在 requiredIndexes 中，则删除该 index 文件
                        if (!indexFile.delete()) {
                            System.err.println("[AssetsCleaner]: Error deleting index file: " + indexFile);
                        } else {
                            System.out.println("[AssetsCleaner]: Deleted unnecessary index: " + indexFile);
                        }
                        continue;
                    }

                    // 解析 JSON 文件
                    try (Reader reader = new FileReader(indexFile)) {
                        Gson gson = new Gson();
                        Map<String, Map<String, Map<String, Object>>> jsonData = gson.fromJson(reader, new TypeToken<Map<String, Map<String, Map<String, Object>>>>() {}.getType());

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
                        System.err.println("[AssetsCleaner]: Error reading JSON file: " + indexFile);
                        e.printStackTrace();
                    }
                }
            }

            // 输出 hashMap 中保存的所有 hash 值
            System.out.println("[AssetsCleaner]: 所有 hash 值:");
            for (String hash : hashMap.keySet()) {
                System.out.println(hash);
            }

            // 遍历目标处理目录文件
            File objectsDir = new File(objectsDirectory);
            if (!objectsDir.exists() || !objectsDir.isDirectory()) {
                System.err.println("[AssetsCleaner]: Objects directory does not exist: " + objectsDir);
                return;
            }

            // 递归遍历 objectsDirectory
            traverseAndCleanDirectory(objectsDir, hashMap);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void traverseAndCleanDirectory(File directory, Map<String, Map<String, Object>> hashMap) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    traverseAndCleanDirectory(file, hashMap);
                } else {
                    String fileName = file.getName();
                    // 判断文件是否在 hashMap 中
                    if (!hashMap.containsKey(fileName)) {
                        // 如果不在则删除
                        if (!file.delete()) {
                            System.err.println("[AssetsCleaner]: Error deleting file: " + file);
                        } else {
                            System.out.println("[AssetsCleaner]: Deleted file: " + file);
                        }
                    }
                }
            }
        }
    }
}