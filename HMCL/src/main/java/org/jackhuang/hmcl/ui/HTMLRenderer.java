/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui;

import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class HTMLRenderer {
    private static URI resolveLink(Node linkNode) {
        String href = linkNode.absUrl("href");
        if (href.isEmpty())
            return null;

        try {
            return new URI(href);
        } catch (Throwable e) {
            return null;
        }
    }

    private final List<javafx.scene.Node> children = new ArrayList<>();
    private final List<Node> stack = new ArrayList<>();

    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strike;
    private boolean highlight;
    private String headerLevel;
    private Node hyperlink;

    private final Consumer<URI> onClickHyperlink;

    public HTMLRenderer(Consumer<URI> onClickHyperlink) {
        this.onClickHyperlink = onClickHyperlink;
    }

    private void updateStyle() {
        bold = false;
        italic = false;
        underline = false;
        strike = false;
        highlight = false;
        headerLevel = null;
        hyperlink = null;

        for (Node node : stack) {
            String nodeName = node.nodeName();
            switch (nodeName) {
                case "b":
                case "strong":
                    bold = true;
                    break;
                case "i":
                case "em":
                    italic = true;
                    break;
                case "ins":
                    underline = true;
                    break;
                case "del":
                    strike = true;
                    break;
                case "mark":
                    highlight = true;
                    break;
                case "a":
                    hyperlink = node;
                    break;
                case "h1":
                case "h2":
                case "h3":
                case "h4":
                case "h5":
                case "h6":
                    headerLevel = nodeName;
                    break;
            }
        }
    }

    private void pushNode(Node node) {
        stack.add(node);
        updateStyle();
    }

    private void popNode() {
        stack.remove(stack.size() - 1);
        updateStyle();
    }

    private void applyStyle(Text text) {
        if (hyperlink != null) {
            URI target = resolveLink(hyperlink);
            if (target != null) {
                FXUtils.onClicked(text, () -> onClickHyperlink.accept(target));
                text.setCursor(Cursor.HAND);
            }
            text.getStyleClass().add("html-hyperlink");
        }

        if (hyperlink != null || underline)
            text.setUnderline(true);

        if (strike)
            text.setStrikethrough(true);

        if (bold || highlight)
            text.getStyleClass().add("html-bold");

        if (italic)
            text.getStyleClass().add("html-italic");

        if (headerLevel != null)
            text.getStyleClass().add("html-" + headerLevel);
    }

    private void appendText(String text) {
        Text textNode = new Text(text);
        applyStyle(textNode);
        children.add(textNode);
    }

    private void appendAutoLineBreak(String text) {
        AutoLineBreak textNode = new AutoLineBreak(text);
        applyStyle(textNode);
        children.add(textNode);
    }

    private void appendImage(Node node) {
        String src = node.absUrl("src");
        URI imageUri = null;
        try {
            if (!src.isEmpty())
                imageUri = URI.create(src);
        } catch (Exception ignored) {
        }

        String alt = node.attr("alt");

        if (imageUri != null) {
            URI uri = URI.create(src);

            String widthAttr = node.attr("width");
            String heightAttr = node.attr("height");

            double width = 0;
            double height = 0;

            if (!widthAttr.isEmpty() && !heightAttr.isEmpty()) {
                try {
                    width = Double.parseDouble(widthAttr);
                    height = Double.parseDouble(heightAttr);
                } catch (NumberFormatException ignored) {
                }

                if (width <= 0 || height <= 0) {
                    width = 0;
                    height = 0;
                }
            }

            try {
                Image image = FXUtils.getRemoteImageTask(uri.toString(), width, height, true, true)
                        .run();
                if (image == null)
                    throw new AssertionError("Image loading task returned null");

                ImageView imageView = new ImageView(image);
                if (hyperlink != null) {
                    URI target = resolveLink(hyperlink);
                    if (target != null) {
                        FXUtils.onClicked(imageView, () -> onClickHyperlink.accept(target));
                        imageView.setCursor(Cursor.HAND);
                    }
                }
                children.add(imageView);
                return;
            } catch (Throwable e) {
                LOG.warning("Failed to load image: " + uri, e);
            }
        }

        if (!alt.isEmpty())
            appendText(alt);
    }

    public void appendNode(Node node) {
        if (node instanceof TextNode) {
            appendText(((TextNode) node).text());
        }

        String name = node.nodeName();
        switch (name) {
            case "img":
                appendImage(node);
                break;
            case "li":
                appendText("\n \u2022 ");
                break;
            case "dt":
                appendText(" ");
                break;
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
            case "tr":
                if (!children.isEmpty())
                    appendAutoLineBreak("\n\n");
                break;
        }

        if (node.childNodeSize() > 0) {
            pushNode(node);
            for (Node childNode : node.childNodes()) {
                appendNode(childNode);
            }
            popNode();
        }

        switch (name) {
            case "br":
            case "dd":
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6":
                appendAutoLineBreak("\n");
                break;
        }
    }

    private static boolean isSpacing(String text) {
        if (text == null)
            return true;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch != ' ' && ch != '\t')
                return false;
        }
        return true;
    }

    public void mergeLineBreaks() {
        for (int i = 0; i < this.children.size(); i++) {
            javafx.scene.Node child = this.children.get(i);
            if (child instanceof AutoLineBreak) {
                int lastAutoLineBreak = -1;

                for (int j = i + 1; j < this.children.size(); j++) {
                    javafx.scene.Node otherChild = this.children.get(j);

                    if (otherChild instanceof AutoLineBreak) {
                        lastAutoLineBreak = j;
                    } else if (otherChild instanceof Text && isSpacing(((Text) otherChild).getText())) {
                        // do nothing
                    } else {
                        break;
                    }
                }

                if (lastAutoLineBreak > 0) {
                    this.children.subList(i + 1, lastAutoLineBreak + 1).clear();

                    if (((Text) child).getText().length() == 1) {
                        ((Text) child).setText("\n\n");
                    }
                }
            }
        }
    }

    public TextFlow render() {
        TextFlow textFlow = new TextFlow();
        textFlow.getStyleClass().add("html");
        textFlow.getChildren().setAll(children);
        return textFlow;
    }

    private static final class AutoLineBreak extends Text {
        public AutoLineBreak(String text) {
            super(text);
        }
    }
}
