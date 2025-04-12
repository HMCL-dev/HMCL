package org.jackhuang.hmcl.mod.multimc;

import org.jackhuang.hmcl.download.LibraryAnalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MultiMCComponents {
    private MultiMCComponents() {
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

    private static final List<Map.Entry<String, LibraryAnalyzer.LibraryType>> PAIRS = new ArrayList<>(ID_TYPE.entrySet());

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

    public static List<Map.Entry<String, LibraryAnalyzer.LibraryType>> getPairs() {
        return PAIRS;
    }
}
