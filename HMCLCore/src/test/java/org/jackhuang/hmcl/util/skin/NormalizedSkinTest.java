package org.jackhuang.hmcl.util.skin;

import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizedSkinTest {
    private static NormalizedSkin getSkin(String name, boolean slim) throws InvalidSkinException {
        String path = Paths.get(String.format("../HMCLCore/src/main/resources/assets/img/skin/%s/%s.png", slim ? "slim" : "wide", name)).normalize().toAbsolutePath().toUri().toString();
        return new NormalizedSkin(new Image(path));
    }

    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testIsSlim() throws Exception {
        String[] names = {"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"};

        for (String skin : names) {
            assertTrue(getSkin(skin, true).isSlim());
            assertFalse(getSkin(skin, false).isSlim());
        }
    }
}
