package org.jackhuang.hmcl.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javafx.scene.text.Font;

import com.sun.javafx.font.FontFactory;
import com.sun.javafx.font.PrismFontFactory;
import com.sun.javafx.font.PrismFontLoader;
import com.sun.javafx.tk.FontLoader;
import com.sun.javafx.tk.Toolkit;

import static org.jackhuang.hmcl.util.Logging.LOG;

public class JavaFXFontHacker {

    public static void hack() {
        final List<String> families = Font.getFamilies();
        final List<String> targets = families.stream().filter(family -> !family.equals(new String(family.getBytes(StandardCharsets.US_ASCII)))).collect(Collectors.toList());
        if (!targets.isEmpty()) {
            try {
                final FontLoader loader = Toolkit.getToolkit().getFontLoader();
                if (loader instanceof PrismFontLoader) {
                    final PrismFontLoader prismFontLoader = (PrismFontLoader) loader;
                    final Method getFontFactoryFromPipeline = PrismFontLoader.class.getDeclaredMethod("getFontFactoryFromPipeline");
                    getFontFactoryFromPipeline.setAccessible(true);
                    final FontFactory pipeline = (FontFactory) getFontFactoryFromPipeline.invoke(prismFontLoader);
                    if (pipeline instanceof PrismFontFactory) {
                        final PrismFontFactory factory = (PrismFontFactory) pipeline;
                        final Field familyToFontListMap = PrismFontFactory.class.getDeclaredField("familyToFontListMap");
                        familyToFontListMap.setAccessible(true);
                        final Map<String, ArrayList<String>> map = (Map<String, ArrayList<String>>) familyToFontListMap.get(factory);
                        targets.forEach(target -> {
                            final ArrayList<String> value = map.get(target);
                            final String broken = new String(target.getBytes(StandardCharsets.UTF_8));
                            map.put(broken, value);
                        });
                    }
                }
            } catch (final Throwable e) {
                LOG.log(Level.WARNING, "Failed to hack font factory", e);
            }
        }
    }

}
