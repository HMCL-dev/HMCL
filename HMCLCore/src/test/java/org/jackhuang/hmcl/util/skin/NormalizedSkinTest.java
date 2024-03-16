package org.jackhuang.hmcl.util.skin;

import javafx.scene.image.Image;
import net.burningtnt.webp.jfx.WEBPImageLoaderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizedSkinTest {
    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testIsSlim() throws Exception {
        WEBPImageLoaderFactory.setupListener();

        assertFalse(new NormalizedSkin(new Image("/assets/img/skin/steve.webp")).isSlim());
        assertTrue(new NormalizedSkin(new Image("/assets/img/skin/alex.webp")).isSlim());
        assertTrue(new NormalizedSkin(new Image("/assets/img/skin/noor.webp")).isSlim());
        assertFalse(new NormalizedSkin(new Image("/assets/img/skin/sunny.webp")).isSlim());
        assertFalse(new NormalizedSkin(new Image("/assets/img/skin/ari.webp")).isSlim());
        assertFalse(new NormalizedSkin(new Image("/assets/img/skin/zuri.webp")).isSlim());
        assertTrue(new NormalizedSkin(new Image("/assets/img/skin/makena.webp")).isSlim());
        assertFalse(new NormalizedSkin(new Image("/assets/img/skin/kai.webp")).isSlim());
        assertTrue(new NormalizedSkin(new Image("/assets/img/skin/efe.webp")).isSlim());
    }
}
