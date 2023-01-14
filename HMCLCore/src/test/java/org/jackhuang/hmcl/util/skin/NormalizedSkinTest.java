package org.jackhuang.hmcl.util.skin;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class NormalizedSkinTest {
    private static NormalizedSkin getSkin(String name) throws IOException, InvalidSkinException {
        File path = new File("../HMCL/src/main/resources/assets/img/skin/" + name + ".png");
        return new NormalizedSkin(ImageIO.read(path));
    }

    @Test
    public void testIsSlim() throws Exception {
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
