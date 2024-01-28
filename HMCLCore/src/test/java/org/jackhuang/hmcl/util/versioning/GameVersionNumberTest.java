package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class GameVersionNumberTest {

    @Test
    public void testSortVersions() throws IOException {
        List<String> versions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/versions.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null && !line.isEmpty(); ) {
                versions.add(line);
            }
        }

        List<String> copied = new ArrayList<>(versions);
        Collections.shuffle(copied, new Random(0));
        copied.sort(Comparator.comparing(GameVersionNumber::asGameVersion));

        // fixme: assertIterableEquals(versions, copied);
    }
}
