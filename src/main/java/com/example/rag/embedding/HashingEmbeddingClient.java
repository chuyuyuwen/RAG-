package com.example.rag.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯本地的「假」向量实现，不联网、不需要 API Key。
 *
 * <p>它做的是 <b>哈希词袋（hashing trick）</b>：把文本切成 token，把每个 token 哈希到一个固定维度的下标上，
 * 累加计数后做 L2 归一化。这样：
 * <ul>
 *   <li>字面越相近的两段文本，向量越接近 —— 足以把整套检索流程跑通、看到效果；</li>
 *   <li>但它<b>不理解语义</b>：「电脑」和「计算机」不会被认为是相近的。真实语义检索必须换成 real 模式。</li>
 * </ul>
 *
 * <p>中文没有空格分词，这里用 <b>字符 bigram</b>（相邻两字为一组）作为 token，
 * 这是信息检索里对中文最简单也还算有效的做法；英文按单词切分并转小写。
 */
public class HashingEmbeddingClient implements EmbeddingClient {

    /**
     * 把文本切成「中文连续段」「英文单词」「数字串」三种 token 单元。
     *
     * <p>必须一次性按顺序扫描，不能先把中文和英文分别抽出来再处理 ——
     * 那样 {@code "我用Spring做RAG"} 里的 Spring 和 RAG 会被拼成 "SpringRAG"，
     * 变成一个不存在的词。
     */
    private static final Pattern TOKEN_UNIT =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z]+|\\d+");

    private final int dimension;

    public HashingEmbeddingClient(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须为正数，实际为 " + dimension);
        }
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[dimension];
        if (text == null || text.isBlank()) {
            return vec;
        }

        for (String token : tokenize(text)) {
            // 用 token 自身的 hashCode 决定落到哪个桶
            int h = token.hashCode();
            int index = Math.floorMod(h, dimension);
            // 符号位：一半桶加、一半桶减。这样不同 token 碰撞时相互抵消的概率更高，
            // 比全部相加更不容易产生系统性偏差（这是 hashing trick 的标准做法）。
            float sign = ((h >>> 16) & 1) == 0 ? 1.0f : -1.0f;
            vec[index] += sign;
        }

        return VectorUtils.l2Normalize(vec);
    }

    /**
     * 把文本切成 token：中文连续段按字符 bigram 切，英文单词和数字整体成一个 token。
     *
     * <p>例："我用Spring做RAG" → ["我用", "做", "spring", "rag"]
     *
     * <p>为什么中文用 bigram 而不是单字？单个汉字信息量太低（"的""是""了"遍地都是），
     * 两字组合能带上一点搭配信息，是中文信息检索里成本最低的可用方案。
     */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        Matcher m = TOKEN_UNIT.matcher(text);
        while (m.find()) {
            String unit = m.group();
            char first = unit.charAt(0);

            if (first >= '\u4e00' && first <= '\u9fa5') {
                // 中文连续段：bigram；只有单字时退化成单字
                if (unit.length() == 1) {
                    tokens.add(unit);
                } else {
                    for (int i = 0; i + 1 < unit.length(); i++) {
                        tokens.add(unit.substring(i, i + 2));
                    }
                }
            } else {
                // 英文单词 / 数字：统一小写，让 "Spring" 和 "spring" 落到同一个桶
                tokens.add(unit.toLowerCase());
            }
        }
        return tokens;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String providerName() {
        return "fake-hash-" + dimension;
    }
}
