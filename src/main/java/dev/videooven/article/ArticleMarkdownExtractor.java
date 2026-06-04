package dev.videooven.article;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.net.URI;
import java.util.Locale;

public final class ArticleMarkdownExtractor {
    public String extract(String html, URI baseUri) {
        Document document = Jsoup.parse(html, baseUri.toString());
        Element root = contentRoot(document).clone();
        root.select("script,style,noscript,nav,header,footer,aside,form").remove();

        String markdown = renderChildren(root).trim();
        if (markdown.isBlank()) {
            throw new IllegalArgumentException("Article did not contain readable content: " + baseUri);
        }
        return markdown;
    }

    private static Element contentRoot(Document document) {
        for (String selector : new String[]{"article", "main", "[role=main]", "body"}) {
            Element element = document.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                return element;
            }
        }
        return document.body();
    }

    private static String renderChildren(Element element) {
        StringBuilder markdown = new StringBuilder();
        for (Node child : element.childNodes()) {
            if (child instanceof Element childElement) {
                renderBlock(childElement, markdown);
            }
        }
        return trimTrailingBlankLines(markdown.toString());
    }

    private static void renderBlock(Element element, StringBuilder markdown) {
        String tag = element.tagName().toLowerCase(Locale.ROOT);
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> appendBlock(
                    markdown,
                    "#".repeat(Integer.parseInt(tag.substring(1))) + " " + renderInlineChildren(element)
            );
            case "p" -> appendBlock(markdown, renderInlineChildren(element));
            case "pre" -> appendBlock(markdown, "```\n" + preformattedText(element) + "\n```");
            case "ul" -> appendBlock(markdown, renderList(element, false));
            case "ol" -> appendBlock(markdown, renderList(element, true));
            case "blockquote" -> appendBlock(markdown, renderBlockquote(element));
            case "hr" -> appendBlock(markdown, "---");
            case "table" -> appendBlock(markdown, renderInlineChildren(element));
            default -> {
                if (hasBlockChildren(element)) {
                    for (Element child : element.children()) {
                        renderBlock(child, markdown);
                    }
                } else {
                    appendBlock(markdown, renderInlineChildren(element));
                }
            }
        }
    }

    private static String renderList(Element element, boolean ordered) {
        StringBuilder rendered = new StringBuilder();
        int index = 1;
        for (Element item : element.children()) {
            if (!"li".equalsIgnoreCase(item.tagName())) {
                continue;
            }
            String marker = ordered ? index++ + ". " : "- ";
            String text = renderInlineChildren(item);
            if (!text.isBlank()) {
                rendered.append(marker).append(text).append('\n');
            }
        }
        return rendered.toString().trim();
    }

    private static String renderBlockquote(Element element) {
        String quoted = renderChildren(element);
        StringBuilder rendered = new StringBuilder();
        for (String line : quoted.lines().toList()) {
            rendered.append("> ").append(line).append('\n');
        }
        return rendered.toString().trim();
    }

    private static String renderInlineChildren(Element element) {
        StringBuilder rendered = new StringBuilder();
        for (Node child : element.childNodes()) {
            rendered.append(renderInline(child));
        }
        return normalizeInline(rendered.toString());
    }

    private static String renderInline(Node node) {
        if (node instanceof TextNode textNode) {
            return textNode.text();
        }
        if (!(node instanceof Element element)) {
            return "";
        }

        String tag = element.tagName().toLowerCase(Locale.ROOT);
        return switch (tag) {
            case "a" -> {
                String text = renderInlineChildren(element);
                String href = element.absUrl("href");
                yield href.isBlank() ? text : "[" + text + "](" + href + ")";
            }
            case "strong", "b" -> "**" + renderInlineChildren(element) + "**";
            case "em", "i" -> "*" + renderInlineChildren(element) + "*";
            case "code" -> "`" + element.wholeText().trim() + "`";
            case "br" -> "\n";
            case "img" -> {
                String src = element.absUrl("src");
                String alt = element.attr("alt");
                yield src.isBlank() ? "" : "![" + alt + "](" + src + ")";
            }
            default -> renderInlineChildren(element);
        };
    }

    private static String preformattedText(Element element) {
        Element code = element.selectFirst("code");
        String text = code == null ? element.wholeText() : code.wholeText();
        return text.strip();
    }

    private static boolean hasBlockChildren(Element element) {
        for (Element child : element.children()) {
            if (isBlockElement(child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockElement(Element element) {
        return switch (element.tagName().toLowerCase(Locale.ROOT)) {
            case "article", "main", "section", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "pre", "ul", "ol", "li", "blockquote", "hr", "table" -> true;
            default -> false;
        };
    }

    private static void appendBlock(StringBuilder markdown, String block) {
        String trimmed = block.trim();
        if (trimmed.isBlank()) {
            return;
        }
        if (!markdown.isEmpty()) {
            markdown.append("\n\n");
        }
        markdown.append(trimmed);
    }

    private static String normalizeInline(String value) {
        return value.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .trim();
    }

    private static String trimTrailingBlankLines(String value) {
        return value.replaceFirst("\\s+$", "");
    }
}
