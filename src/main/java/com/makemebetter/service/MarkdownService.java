package com.makemebetter.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Service;

/**
 * Markdown → HTML。commonmark 默认不渲染裸 HTML，
 * 所以写进正文的 &lt;script&gt; 只会当成文字。
 */
@Service
public class MarkdownService {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            // 单个换行也当换行。Markdown 默认把软换行合并成一行，写笔记时太反直觉
            .softbreak("<br />\n")
            .build();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
