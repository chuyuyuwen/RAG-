package com.example.rag.ingest;

import com.example.rag.config.RagProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文本切块器：把一篇长文档切成若干个「片段」（chunk）。
 *
 * <p>这是 RAG 里最影响效果、又最容易被忽视的一步。切得太碎会丢上下文，切得太大会稀释语义。
 *
 * <p>这里的策略是 <b>按自然边界优先、超长才硬切、相邻块重叠</b>：
 * <ol>
 *   <li>先按段落（连续两个换行）切开，段落是最自然的语义单元；</li>
 *   <li>单个段落超过 size 时，再按句号/问号/感叹号等句末标点切成句子；</li>
 *   <li>单个句子还超长（比如没有标点的长串），才按字符硬切；</li>
 *   <li>把句子贪心地塞进块里，塞不下就开新块；</li>
 *   <li>新块开头带上上一块末尾的 overlap 个字符，让跨块的语义不断裂。</li>
 * </ol>
 *
 * <p>为什么重叠（overlap）有用？假设一句话正好被切在两块之间，没有重叠的话两块都读不懂这句话；
 * 有重叠则至少有一块包含完整句子，检索时能被召回。
 */
@Component
public class TextChunker {

    /** 句末标点：中文的。！？ 和英文的 .!? ，以及换行。 */
    private static final String SENTENCE_ENDINGS = "。！？!?；;…\n";

    private final RagProperties.Chunk cfg;

    public TextChunker(RagProperties props) {
        this.cfg = props.getChunk();
    }

    /**
     * 把文本切成块。
     *
     * @param text 原始文本
     * @return 块列表；文本为空时返回空列表
     */
    public List<String> split(String text) {
        if (text == null) {
            return List.of();
        }
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }

        int size = Math.max(50, cfg.getSize());
        // overlap 不能大于等于 size，否则每次只前进 0 个字符会死循环
        int overlap = Math.min(Math.max(0, cfg.getOverlap()), size - 1);

        // 第一步：拆成「句子」序列
        List<String> units = splitIntoSentences(normalized, size);

        // 第二步：贪心装箱
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String unit : units) {
            // 装得下就继续装
            if (current.length() + unit.length() <= size) {
                current.append(unit);
                continue;
            }

            // 装不下了：先把手上的存下来
            if (current.length() > 0) {
                chunks.add(current.toString().trim());
            }

            // 新块以「上一块的尾部 overlap 个字符」开头
            String carry = tail(current.toString(), overlap);
            current.setLength(0);
            current.append(carry);

            // 带上 carry 之后可能还是装不下这个 unit（unit 本身接近 size），
            // 那就把 carry 丢掉单独成块，保证块不超过 size 太多
            if (current.length() + unit.length() > size && !carry.isEmpty()) {
                current.setLength(0);
            }
            current.append(unit);
        }

        if (!current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }

        // 去掉纯空白块
        return chunks.stream().filter(s -> !s.isBlank()).toList();
    }

    /**
     * 统一换行符、压缩多余空白。
     *
     * <p>PDF 抽出来的文本经常有大量连续空格和奇怪的空行，先清理能让后续切块稳定得多。
     */
    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                // 把 3 个以上连续换行压成 2 个，保留段落分隔
                .replaceAll("\n{3,}", "\n\n")
                // 行内连续空格/制表符压成一个
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .trim();
    }

    /**
     * 把文本拆成「不超过 size 的句子单元」。
     *
     * <p>先按段落切，再按句末标点切，最后对超长无标点串做硬切。
     */
    private List<String> splitIntoSentences(String text, int size) {
        List<String> units = new ArrayList<>();

        for (String paragraph : text.split("\n\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= size) {
                units.add(p + "\n");
                continue;
            }
            // 段落太长，按句末标点切句
            StringBuilder sentence = new StringBuilder();
            for (int i = 0; i < p.length(); i++) {
                char c = p.charAt(i);
                sentence.append(c);
                boolean isEnd = SENTENCE_ENDINGS.indexOf(c) >= 0;
                if (isEnd || sentence.length() >= size) {
                    String s = sentence.toString();
                    // 单句仍超长 → 硬切
                    units.addAll(hardSplit(s, size));
                    sentence.setLength(0);
                }
            }
            if (sentence.length() > 0) {
                units.addAll(hardSplit(sentence.toString(), size));
            }
        }
        return units;
    }

    /** 按字符硬切，用于没有标点的超长串（比如一大段代码或数字）。 */
    private List<String> hardSplit(String s, int size) {
        List<String> out = new ArrayList<>();
        if (s.length() <= size) {
            out.add(s);
            return out;
        }
        for (int i = 0; i < s.length(); i += size) {
            out.add(s.substring(i, Math.min(i + size, s.length())));
        }
        return out;
    }

    /** 取字符串末尾 n 个字符。 */
    private String tail(String s, int n) {
        if (n <= 0 || s.isEmpty()) {
            return "";
        }
        return s.length() <= n ? s : s.substring(s.length() - n);
    }
}
