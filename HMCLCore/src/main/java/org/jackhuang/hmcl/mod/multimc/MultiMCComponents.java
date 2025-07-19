package org.jackhuang.hmcl.mod.multimc;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class MultiMCComponents {

    private MultiMCComponents() {
    }

    private static final Map<String, String> INSTALLER_PROFILE = new ConcurrentHashMap<>();

    static {
        INSTALLER_PROFILE.put("Patches", "reclusive install, fabric & quit intermediary");

        if (new String(
                // Base64 of 'org.jackhuang.hmcl.mod.multimc.MultiMCComponents'
                Base64.getDecoder().decode("b3JnLmphY2todWFuZy5obWNsLm1vZC5tdWx0aW1jLk11bHRpTUNDb21wb25lbnRz"),
                StandardCharsets.UTF_8
        ).equals(MultiMCComponents.class.getName())) {
            INSTALLER_PROFILE.put("Implementation", "Probably vanilla. Class location is not modified (org.jackhuang.hmcl.mod.multimc.MultiMCComponents).");
        } else {
            INSTALLER_PROFILE.put("Implementation", "Not vanilla. Class location is " + MultiMCComponents.class.getName());
        }
    }

    public static void setImplementation(String implementation) {
        INSTALLER_PROFILE.put("Implementation", implementation);
    }

    public static String getInstallerProfile() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : INSTALLER_PROFILE.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        if (builder.length() != 0) {
            builder.setLength(builder.length() - 1);
        }

        return builder.toString();
    }

    private static final Map<String, LibraryAnalyzer.LibraryType> ID_TYPE = new HashMap<>();

    static {
        ID_TYPE.put("net.minecraft", LibraryAnalyzer.LibraryType.MINECRAFT);
        ID_TYPE.put("net.minecraftforge", LibraryAnalyzer.LibraryType.FORGE);
        ID_TYPE.put("net.neoforged", LibraryAnalyzer.LibraryType.NEO_FORGE);
        ID_TYPE.put("com.mumfrey.liteloader", LibraryAnalyzer.LibraryType.LITELOADER);
        ID_TYPE.put("net.fabricmc.fabric-loader", LibraryAnalyzer.LibraryType.FABRIC);
        ID_TYPE.put("org.quiltmc.quilt-loader", LibraryAnalyzer.LibraryType.QUILT);
    }

    private static final Map<LibraryAnalyzer.LibraryType, String> TYPE_ID =
            ID_TYPE.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private static final Collection<Map.Entry<String, LibraryAnalyzer.LibraryType>> PAIRS = Collections.unmodifiableCollection(ID_TYPE.entrySet());

    static {
        if (TYPE_ID.isEmpty()) {
            throw new AssertionError("Please make sure TYPE_ID and PAIRS is initialized after ID_TYPE!");
        }
    }

    public static String getComponent(LibraryAnalyzer.LibraryType type) {
        return TYPE_ID.get(type);
    }

    public static LibraryAnalyzer.LibraryType getComponent(String type) {
        return ID_TYPE.get(type);
    }

    public static Collection<Map.Entry<String, LibraryAnalyzer.LibraryType>> getPairs() {
        return PAIRS;
    }

    public static URL getMetaURL(String componentID, String version, String mcVersion) {
        if (version == null) {
            switch (componentID) {
                case "org.lwjgl": {
                    version = "2.9.1";
                    break;
                }
                case "org.lwjgl3": {
                    version = "3.1.2";
                    break;
                }
                case "net.fabricmc.intermediary":
                case "org.quiltmc.hashed": {
                    version = mcVersion;
                    break;
                }
            }
        }

        return NetworkUtils.toURL(String.format("https://meta.multimc.org/v1/%s/%s.json", componentID, version));
    }
}
