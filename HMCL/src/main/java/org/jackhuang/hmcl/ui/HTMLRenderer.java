package org.jackhuang.hmcl.ui;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Glavo
 */
public final class HTMLRenderer {
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
            String href = hyperlink.absUrl("href");
            try {
                URI uri = new URI(href);
                text.setOnMouseClicked(event -> onClickHyperlink.accept(uri));
            } catch (Exception ignored) {
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

    public void appendNode(Node node) {
        if (node instanceof TextNode) {
            appendText(((TextNode) node).text());
        }

        String name = node.nodeName();
        switch (name) {
            case "li":
                appendText("\n * ");
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
                    appendText("\n\n");
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
                appendText("\n");
                break;
        }
    }

    public TextFlow render() {
        TextFlow textFlow = new TextFlow();
        textFlow.getChildren().setAll(children);
        return textFlow;
    }
}
