package com.example.rag.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * 把各种格式的文件抽成纯文本。
 *
 * <p>RAG 的第一步永远是「拿到文本」。不同格式要用不同的库：
 * <ul>
 *   <li>PDF → Apache PDFBox（纯 Java，不依赖外部程序）；</li>
 *   <li>Word(.docx) → Apache POI 的 XWPF；</li>
 *   <li>Markdown / TXT / CSV 等 → 本身就是文本，直接按 UTF-8 读。</li>
 * </ul>
 *
 * <p>注意 PDF 里的<b>图片和扫描件</b>抽不出文字 —— 那种需要 OCR（比如 Tesseract），本项目不做。
 */
@Component
public class TextExtractor {

    /** 支持的文件扩展名（小写，带点）。 */
    public static final java.util.Set<String> SUPPORTED_EXTENSIONS =
            java.util.Set.of(".pdf", ".docx", ".txt", ".md", ".markdown", ".csv", ".json", ".log");

    /**
     * 从文件流中抽取纯文本。
     *
     * @param fileName 原始文件名，用来判断格式
     * @param in       文件内容流（调用方负责关闭）
     * @return 抽出的文本，可能为空字符串（比如纯图片 PDF）
     */
    public String extract(String fileName, InputStream in) {
        String ext = extensionOf(fileName);

        try {
            return switch (ext) {
                case ".pdf" -> extractPdf(in);
                case ".docx" -> extractDocx(in);
                case ".txt", ".md", ".markdown", ".csv", ".json", ".log" ->
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                default -> throw new IngestException(
                        "不支持的文件类型：" + ext + "。目前支持 PDF、DOCX、TXT、Markdown、CSV、JSON。");
            };
        } catch (IOException e) {
            throw new IngestException("读取文件失败：" + e.getMessage(), e);
        }
    }

    /** 抽取 PDF 文本。 */
    private String extractPdf(InputStream in) throws IOException {
        try (PDDocument doc = Loader.loadPDF(in.readAllBytes())) {
            if (doc.isEncrypted()) {
                throw new IngestException("这个 PDF 加密了，无法抽取文本。请先解密再上传。");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // 按位置排序，尽量还原阅读顺序（默认按内容流顺序，有时会乱）
            stripper.setSortByPosition(true);
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new IngestException(
                        "这个 PDF 里没有可抽取的文字，可能是扫描件（图片）。本项目不支持 OCR。");
            }
            return text;
        }
    }

    /** 抽取 .docx 文本。 */
    private String extractDocx(InputStream in) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            if (text == null || text.isBlank()) {
                throw new IngestException("这个 Word 文档里没有文字内容。");
            }
            return text;
        }
    }

    /** 取文件扩展名并转小写，含点。没有扩展名时返回空串。 */
    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.ROOT);
    }

    /** 判断某文件名是否支持。 */
    public static boolean isSupported(String fileName) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(fileName));
    }
}
