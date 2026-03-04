package com.seewhy.syaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.seewhy.syaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * PDF 生成工具（支持中文：优先 classpath 字体，并对 Markdown 做简易转纯文）
 */
@Component
public class PDFGenerationTool {

    private static final String[] FONT_RESOURCE_CANDIDATES = {
            "/fonts/NotoSansCJKsc-Regular.otf",
            "/fonts/SimSun.ttf",
            "/fonts/msyh.ttf",
            "/fonts/SourceHanSansSC-Regular.otf"
    };

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";
        String filePath = fileDir + "/" + fileName;
        try {
            FileUtil.mkdir(fileDir);
            // 将 Markdown 风格转为纯文，避免 PDF 里出现 ##、- 等符号
            String plainContent = markdownToPlain(content);
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                PdfFont font = resolveChineseFont();
                document.setFont(font);
                // 按行写入，保证换行和段落清晰
                String[] lines = plainContent.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        document.add(new Paragraph(" "));
                        continue;
                    }
                    document.add(new Paragraph(trimmed).setFont(font));
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private PdfFont resolveChineseFont() throws IOException {
        // 1) 优先 classpath 下的中文字体（见 src/main/resources/fonts/README.txt）
        for (String resource : FONT_RESOURCE_CANDIDATES) {
            try (InputStream is = getClass().getResourceAsStream(resource)) {
                if (is != null) {
                    String suffix = resource.substring(resource.lastIndexOf('.'));
                    Path temp = Files.createTempFile("pdf-font-", suffix);
                    Files.copy(is, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    return PdfFontFactory.createFont(temp.toAbsolutePath().toString(),
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                }
            } catch (Exception ignore) { /* try next */ }
        }
        // 2) iText font-asian 内置中文字体（依赖已改为 runtime，可正确显示中文）
        for (String[] nameEncoding : new String[][]{
                {"STSongStd-Light", "UniGB-UCS2-H"},
                {"STSong-Light", "UniGB-UCS2-H"}
        }) {
            try {
                return PdfFontFactory.createFont(nameEncoding[0], nameEncoding[1]);
            } catch (Exception ignore) { /* try next */ }
        }
        // 3) Windows 系统字体目录
        String winRoot = System.getenv("SystemRoot");
        if (winRoot != null && !winRoot.isEmpty()) {
            String[] winCandidates = {
                    winRoot + "\\Fonts\\simsun.ttc",
                    winRoot + "\\Fonts\\msyh.ttc",
                    winRoot + "\\Fonts\\simhei.ttf",
                    winRoot + "\\Fonts\\simsun.ttf"
            };
            for (String path : winCandidates) {
                if (Files.isRegularFile(Path.of(path))) {
                    try {
                        String fontPath = path.endsWith(".ttc") ? path + ",0" : path;
                        return PdfFontFactory.createFont(fontPath, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    } catch (Exception ignore) { }
                }
            }
        }
        // 4) 退回到默认字体（中文会乱码，仅作兜底）
        return PdfFontFactory.createFont();
    }

    /** 简易 Markdown 转纯文：去掉 ##、###、列表符 -，保留换行与层次 */
    private String markdownToPlain(String content) {
        if (content == null) return "";
        String s = content
                .replaceAll("(?m)^#+\\s*", "")   // 行首 # ## ###
                .replaceAll("(?m)^[-*]\\s+", "  • ")  // 行首 - 或 *
                .replaceAll("\\*\\*\\*\\*\\s*", "")   // ****
                .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")  // **粗体** -> 粗体
                .replaceAll("\\|\\s*\\|", " ");
        return s.trim();
    }
}
