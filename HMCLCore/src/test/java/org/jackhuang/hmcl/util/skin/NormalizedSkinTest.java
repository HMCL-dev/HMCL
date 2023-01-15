package org.jackhuang.hmcl.util.skin;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizedSkinTest {
    private static NormalizedSkin getSkin(String name) throws InvalidSkinException {
        String path = Paths.get("../HMCL/src/main/resources/assets/img/skin/" + name + ".png").normalize().toAbsolutePath().toUri().toString();
        return new NormalizedSkin(new Image(path));
    }

    @Test
    @Disabled("Cannot run in headless mode")
    public void testIsSlim() throws Exception {
        JavaFXLauncher.start();
        assertFalse(getSkin("steve").isSlim());
        assertTrue(getSkin("alex").isSlim());
        assertTrue(getSkin("noor").isSlim());
        assertFalse(getSkin("sunny").isSlim());
        assertFalse(getSkin("ari").isSlim());
        assertFalse(getSkin("zuri").isSlim());
        assertTrue(getSkin("makena").isSlim());
        assertFalse(getSkin("kai").isSlim());
        assertTrue(getSkin("efe").isSlim());
    }
}
