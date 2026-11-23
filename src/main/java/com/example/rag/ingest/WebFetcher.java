package com.example.rag.ingest;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * 抓取网页并抽正文。
 *
 * <p>为什么不直接把整页 HTML 存起来？因为 HTML 里 90% 是导航栏、广告、脚本、样式，
 * 真正的正文往往只占很小一块。把这些噪声喂给 embedding 会严重污染检索结果。
 *
 * <p>这里的做法是：<b>先删掉脚本/样式/导航/页脚等明显无用的标签，再取 body 的文本</b>。
 * 这是最朴素的正文提取，对博客、文档站效果尚可；对强前端渲染的站点（内容靠 JS 生成）无能为力。
 */
@Component
public class WebFetcher {

    /** 抓取超时（毫秒）。太短容易失败，太长用户要干等。 */
    private static final int TIMEOUT_MS = 20_000;

    /** 抓取结果：标题 + 正文。 */
    public record Page(String title, String text) {
    }

    /**
     * 抓取网页。
     *
     * @param url http/https 地址
     * @return 标题和正文
     */
    public Page fetch(String url) {
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 (compatible; RagKnowledgeBot/1.0)")
                    .followRedirects(true)
                    .get();
        } catch (Exception e) {
            throw new IngestException("抓取网页失败：" + e.getMessage()
                    + "。请检查网址是否正确、是否需要登录或科学上网。", e);
        }

        // 删掉明显不是正文的标签
        doc.select("script, style, noscript, iframe, svg, nav, header, footer, aside, form").remove();
        // 常见广告/侧边栏类名，按类名兜底清理
        doc.select("[class~=(?i)(nav|menu|sidebar|footer|header|ad|banner|cookie|comment)]").remove();

        String title = doc.title();
        if (title == null || title.isBlank()) {
            title = url;
        }

        Element body = doc.body();
        String text = body == null ? "" : body.text();

        if (text.isBlank()) {
            throw new IngestException("网页没有抓到任何正文。可能是纯前端渲染的页面（内容由 JS 生成），"
                    + "或者需要登录才能看到。");
        }
        return new Page(title.trim(), text);
    }
}
