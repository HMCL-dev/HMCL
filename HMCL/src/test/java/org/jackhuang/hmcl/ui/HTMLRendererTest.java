package org.jackhuang.hmcl.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HTMLRendererTest {

    @Test
    public void isHTMLTest() {
        assertTrue(HTMLRenderer.isHTML("<b>Bold</b>"));
        assertTrue(HTMLRenderer.isHTML("Some text with <a href=\"link\">link</a>."));
        assertTrue(HTMLRenderer.isHTML("""
                A DIV
                <div>
                \t<p>
                \t\tParagraph
                \t</p>
                </div>"""));
        assertTrue(HTMLRenderer.isHTML("<img src=\"image.png\" alt=\"Image\">"));
        assertFalse(HTMLRenderer.isHTML(null));
        assertFalse(HTMLRenderer.isHTML(""));
        assertFalse(HTMLRenderer.isHTML("<>"));
        assertFalse(HTMLRenderer.isHTML("Just a plain text."));
    }

}
