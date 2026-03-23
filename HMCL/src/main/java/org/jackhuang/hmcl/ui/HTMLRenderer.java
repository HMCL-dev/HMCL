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

import javafx.beans.InvalidationListener;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
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

    public static HTMLRenderer openHyperlinkInBrowser() {
        return new HTMLRenderer(FXUtils::openUriInBrowser);
    }

    /// @see org.jsoup.internal.StringUtil#isWhitespace(int)
    public static boolean isWhitespace(int c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
    }

    /// @see org.jsoup.internal.StringUtil#isInvisibleChar(int)
    public static boolean isInvisibleChar(int c) {
        return c == 8203 || c == 173; // zero width sp, soft hyphen
        // previously also included zw non join, zw join - but removing those breaks semantic meaning of text
    }

    /// @see org.jsoup.internal.StringUtil#normaliseWhitespace(String)
    /// @see org.jsoup.internal.StringUtil#isActuallyWhitespace(int)
    public static String normaliseWhitespace(String str) {
        var accum = new StringBuilder();
        boolean lastWasWhite = false;
        int len = str.length();
        int c;
        for (int i = 0; i < len; i += Character.charCount(c)) {
            c = str.codePointAt(i);
            if (isWhitespace(c)) { // Ignore &nbsp;
                if (lastWasWhite)
                    continue;
                accum.append(' ');
                lastWasWhite = true;
            } else if (!isInvisibleChar(c)) {
                accum.appendCodePoint(c);
                lastWasWhite = false;
            }
        }
        return accum.toString();
    }

    private final List<javafx.scene.Node> children = new ArrayList<>();
    private final List<Node> stack = new ArrayList<>();

    private boolean bold;
    private boolean italic;
    private boolean underline;
    private boolean strike;
    private boolean highlight;
    private boolean preformatted;
    private boolean code;
    private int listDepth;
    private String headerLevel;
    private Node hyperlink;
    private String fxStyle;

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
        preformatted = false;
        code = false;
        listDepth = 0;
        headerLevel = null;
        hyperlink = null;
        fxStyle = null;

        for (Node node : stack) {
            String nodeName = node.nodeName();
            switch (nodeName) {
                case "b", "strong" -> bold = true;
                case "i", "em" -> italic = true;
                case "ins" -> underline = true;
                case "del" -> strike = true;
                case "mark" -> highlight = true;
                case "pre" -> preformatted = true;
                case "code" -> code = true;
                case "a" -> hyperlink = node;
                case "h1", "h2", "h3", "h4", "h5", "h6" -> headerLevel = nodeName;
                case "li" -> listDepth++;
            }

            String style = node.attr("style");
            if (StringUtils.isNotBlank(style)) {
                fxStyle = StringUtils.addSuffix(
                        style
                                .replace("color:", "-fx-fill:")
                                .replace("font-size:", "-fx-font-size:"), // And more
                        ";"
                );
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
        var styleBuilder = new StringBuilder();

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

        if (code) {
            text.getStyleClass().add("html-code");
            styleBuilder.append("-fx-font-family: \"%s\";".formatted(Lang.requireNonNullElse(config().getFontFamily(), FXUtils.DEFAULT_MONOSPACE_FONT)));
        }

        if (headerLevel != null)
            text.getStyleClass().add("html-" + headerLevel);

        if (fxStyle != null)
            styleBuilder.append(fxStyle);
        text.setStyle(styleBuilder.toString());
    }

    private void appendText(String text) {
        Text textNode = new Text(text);
        applyStyle(textNode);
        if (code) {
            var block = new VBox(textNode);
            block.setAlignment(Pos.CENTER);
            block.getStyleClass().add("html-code-block");
            children.add(block);
        } else {
            children.add(textNode);
        }
    }

    private void appendAutoLineBreak(String text) {
        AutoLineBreak textNode = new AutoLineBreak(text);
        applyStyle(textNode);
        children.add(textNode);
    }

    private void appendImage(Node node) {
        String src = node.absUrl("src");
        String alt = node.attr("alt");

        if (StringUtils.isNotBlank(src)) {
            String widthAttr = node.attr("width");
            String heightAttr = node.attr("height");

            int width = 0;
            int height = 0;

            if (!widthAttr.isEmpty() && !heightAttr.isEmpty()) {
                try {
                    width = (int) Double.parseDouble(widthAttr);
                    height = (int) Double.parseDouble(heightAttr);
                } catch (NumberFormatException ignored) {
                }

                if (width <= 0 || height <= 0) {
                    width = 0;
                    height = 0;
                }
            }

            try {
                ImageView imageView = new ImageView();

                FXUtils.getRemoteImageTask(
                        src, width, height, true, true
                ).whenComplete(Schedulers.javafx(), (res, e) -> {
                    if (e != null) {
                        LOG.warning("Failed to load image: " + src, e);
                        return;
                    }
                    if (res == null) {
                        LOG.warning("Failed to load image: " + src, new AssertionError("Image loading task returned null"));
                    }
                    imageView.setImage(res);
                }).start();

                if (hyperlink != null) {
                    URI target = resolveLink(hyperlink);
                    if (target != null) {
                        FXUtils.onClicked(imageView, () -> onClickHyperlink.accept(target));
                        imageView.setCursor(Cursor.HAND);
                    }
                }
                imageView.setPreserveRatio(true);
                children.add(imageView);
                return;
            } catch (Throwable e) {
                LOG.warning("Failed to load image: " + src, e);
            }
        }

        if (!alt.isEmpty())
            appendText(alt);
    }

    private void appendTable(Node table) {
        var childElements = ((Element) table).children();
        List<Element> captions = new ArrayList<>();

        List<String> head = new ArrayList<>();
        List<List<String>> body = new ArrayList<>();
        List<String> foot = new ArrayList<>();

        boolean hasHead = false;
        boolean hasBody = false;
        boolean hasFoot = false;
        int columnCount = 0;
        for (Element child : childElements) {
            switch (child.nodeName()) {
                case "caption" -> captions.add(child);
                case "thead" -> {
                    if (hasHead) continue;
                    hasHead = true;
                    for (Element e : child.children()) {
                        if (e.nameIs("tr")) {
                            head.clear();
                            head.addAll(
                                    e.children().stream()
                                            .filter(n -> n.nameIs("th") || n.nameIs("td"))
                                            .map(Element::text)
                                            .toList()
                            );
                            break;
                        }
                        if (e.nameIs("th") || e.nameIs("td")) {
                            head.add(e.text());
                        }
                    }
                    columnCount = Math.max(columnCount, head.size());
                }
                case "tbody" -> {
                    if (hasBody) continue;
                    hasBody = true;
                    body.clear();
                    for (Element e : child.children()) {
                        if (e.nameIs("tr")) {
                            List<String> row = e.children().stream()
                                    .filter(n -> n.nameIs("th") || n.nameIs("td"))
                                    .map(Element::text)
                                    .toList();
                            columnCount = Math.max(columnCount, row.size());
                            if (!row.isEmpty()) body.add(row);
                        }
                    }
                }
                case "tfoot" -> {
                    if (hasFoot) continue;
                    hasFoot = true;
                    for (Element e : child.children()) {
                        if (e.nameIs("tr")) {
                            foot.clear();
                            foot.addAll(
                                    e.children().stream()
                                            .filter(n -> n.nameIs("th") || n.nameIs("td"))
                                            .map(Element::text)
                                            .toList()
                            );
                            break;
                        }
                        if (e.nameIs("th") || e.nameIs("td")) {
                            foot.add(e.text());
                        }
                    }
                    columnCount = Math.max(columnCount, foot.size());
                }
                case "tr" -> {
                    if (hasBody) continue;
                    List<String> row = child.children().stream()
                            .filter(n -> n.nameIs("th") || n.nameIs("td"))
                            .map(Element::text)
                            .toList();
                    columnCount = Math.max(columnCount, row.size());
                    if (!row.isEmpty()) body.add(row);
                }
            }
        }

        List<List<String>> rows = new ArrayList<>(hasFoot ? body.size() + 1 : body.size());
        for (List<String> row : body)
            rows.add(Lang.copyWithSize(row, columnCount, ""));
        if (hasFoot)
            rows.add(Lang.copyWithSize(foot, columnCount, ""));

        TableView<List<String>> tableView = new TableView<>(FXCollections.observableList(rows));
        tableView.setFixedCellSize(25);
        tableView.setPrefHeight(25 * (rows.size() + 1) + 5);
        for (int i = 0; i < columnCount; i++) {
            int finalI = i;
            TableColumn<List<String>, String> c = new TableColumn<>(head.size() > i ? head.get(i) : "");
            c.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(finalI)));
            tableView.getColumns().add(c);
        }

        children.add(tableView);

        for (Element caption : captions) {
            appendAutoLineBreak("\n\n");
            appendChildren(caption);
            appendAutoLineBreak("\n");
        }
    }

    private void appendOrderedList(Node node) {
        pushNode(node);
        int ordinal = 0;
        for (Node childNode : node.childNodes()) {
            if (childNode.nameIs("li")) {
                appendText("\n " + "  ".repeat(listDepth) + ++ordinal + ". ");
                appendChildren(childNode);
                continue;
            }
            appendNode(childNode);
        }
        popNode();
    }

    private void appendChildren(Node node) {
        if (node.childNodeSize() > 0) {
            if (node.nameIs("table")) {
                appendTable(node);
            } else if (node.nameIs("ol")) {
                appendOrderedList(node);
            } else {
                pushNode(node);
                for (Node childNode : node.childNodes()) {
                    appendNode(childNode);
                }
                popNode();
            }
        }
    }

    public void appendNode(Node node) {
        if (node instanceof TextNode n) {
            appendText(preformatted ? n.getWholeText() : normaliseWhitespace(n.getWholeText()));
        }

        String name = node.nodeName();
        switch (name) {
            case "img" -> {
                if (!children.isEmpty())
                    appendAutoLineBreak("\n");
                appendImage(node);
                appendAutoLineBreak("\n");
            }
            case "li" -> appendText("\n " + "  ".repeat(listDepth) + "\u2022 ");
            case "dt" -> appendText(" ");
            case "p" -> {
                var n = node.parent();
                if (!children.isEmpty() && (n == null || !n.nameIs("li")))
                    appendAutoLineBreak("\n\n");
            }
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                if (!children.isEmpty())
                    appendAutoLineBreak("\n\n");
            }
        }

        appendChildren(node);

        switch (name) {
            case "br", "dd", "h1", "h2", "h3", "h4", "h5", "h6" -> appendAutoLineBreak("\n");
            case "p" -> {
                var n = node.parent();
                if (n == null || !n.nameIs("li"))
                    appendAutoLineBreak("\n");
            }
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
        for (javafx.scene.Node node : children) {
            if (node instanceof ImageView img) {
                InvalidationListener i = __ ->
                        img.setFitWidth(Math.min(textFlow.getWidth() - 20D, img.getImage() == null ? 0D : img.getImage().getWidth()));
                textFlow.widthProperty().addListener(i);
                img.imageProperty().addListener(i);
            } else if (node instanceof TableView<?> table) {
                table.prefWidthProperty().bind(textFlow.widthProperty().add(-20D));
            }
        }
        return textFlow;
    }

    private static final class AutoLineBreak extends Text {
        public AutoLineBreak(String text) {
            super(text);
        }
    }
}
