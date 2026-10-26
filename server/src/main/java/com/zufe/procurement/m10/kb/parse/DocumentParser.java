package com.zufe.procurement.m10.kb.parse;

import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * 文档正文解析（M10-1-2）：md 与 docx 服务端零依赖解析，pdf 暂不支持自动提取。
 *
 * 零依赖口径（同 M1 导入导出不引 POI 的先例）：规划书第三章技术栈未列任何
 * 文档解析库，中文 PDF 的 CID 字体与 ToUnicode 映射解析成本高且脆弱，
 * 不为单一来源引入规划书外依赖。pdf 上传后可正常存储与下载，正文走
 * M10-1-3 解析结果校对入口粘贴补录，parse_status 记 4（解析失败）。
 *
 * docx 解析用 JDK 自带 zip + DOM（word/document.xml 的 w:p / w:t），
 * 标题层级取 w:pStyle 的 Heading1~6；解析器关闭 DTD 与外部实体防 XXE。
 */
@Service
public class DocumentParser {

    /** 解析结果：正文文本 + 标题层级（块列表） */
    public ParsedDoc parse(String fileType, InputStream input) {
        String type = fileType == null ? "" : fileType.toLowerCase();
        switch (type) {
            case "md":
            case "markdown":
                return parseMarkdown(input);
            case "docx":
                return parseDocx(input);
            default:
                throw new BizException(ResultCode.UNSUPPORTED,
                        "暂不支持自动解析 " + type + " 文件正文，请通过正文补录粘贴文本");
        }
    }

    /** md 解析：# 为标题层级，连续非空行合并为一个段落块 */
    ParsedDoc parseMarkdown(InputStream input) {
        ParsedDoc doc = new ParsedDoc();
        int[] headingCount = new int[6];
        String sectionNo = null;
        String sectionTitle = null;
        StringBuilder paragraph = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    flushParagraph(doc, paragraph, sectionNo, sectionTitle);
                    continue;
                }
                int level = headingLevel(trimmed);
                if (level > 0) {
                    flushParagraph(doc, paragraph, sectionNo, sectionTitle);
                    headingCount[level - 1]++;
                    for (int i = level; i < headingCount.length; i++) {
                        headingCount[i] = 0;
                    }
                    sectionNo = buildSectionNo(headingCount, level);
                    sectionTitle = trimmed.substring(level).trim();
                } else {
                    paragraph.append(trimmed).append('\n');
                }
            }
            flushParagraph(doc, paragraph, sectionNo, sectionTitle);
        } catch (IOException e) {
            throw new BizException(ResultCode.UNSUPPORTED, "文档读取失败：" + e.getMessage());
        }
        if (doc.isEmpty()) {
            throw new BizException(ResultCode.NO_DATA, "文档未解析到正文内容");
        }
        return doc;
    }

    /** docx 解析：zip 内 word/document.xml，按 w:p 取段落，Heading 样式为标题 */
    ParsedDoc parseDocx(InputStream input) {
        ParsedDoc doc = new ParsedDoc();
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"word/document.xml".equals(entry.getName())) {
                    continue;
                }
                Document xml = newDocumentBuilder().parse(zip);
                NodeList paragraphs = xml.getElementsByTagName("w:p");
                int[] headingCount = new int[6];
                String sectionNo = null;
                String sectionTitle = null;
                for (int i = 0; i < paragraphs.getLength(); i++) {
                    Element paragraph = (Element) paragraphs.item(i);
                    String text = paragraphText(paragraph).trim();
                    if (text.isEmpty()) {
                        continue;
                    }
                    int level = docxHeadingLevel(paragraph);
                    if (level > 0) {
                        headingCount[level - 1]++;
                        for (int j = level; j < headingCount.length; j++) {
                            headingCount[j] = 0;
                        }
                        sectionNo = buildSectionNo(headingCount, level);
                        sectionTitle = text;
                        continue;
                    }
                    doc.add(new ParsedDoc.Block(sectionNo, sectionTitle, text));
                }
                break;
            }
        } catch (Exception e) {
            throw new BizException(ResultCode.UNSUPPORTED, "docx 解析失败：" + e.getMessage());
        }
        if (doc.isEmpty()) {
            throw new BizException(ResultCode.NO_DATA, "文档未解析到正文内容");
        }
        return doc;
    }

    private static DocumentBuilder newDocumentBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 防 XXE：关闭 DTD 与外部实体
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new BizException(ResultCode.UNSUPPORTED, "XML 解析器初始化失败");
        }
    }

    private static String paragraphText(Element paragraph) {
        StringBuilder text = new StringBuilder();
        NodeList nodes = paragraph.getElementsByTagName("w:t");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            text.append(node.getTextContent());
        }
        return text.toString();
    }

    /** w:pPr/w:pStyle 的 Heading1~6 映射为层级，Title 视为一级 */
    private static int docxHeadingLevel(Element paragraph) {
        NodeList styles = paragraph.getElementsByTagName("w:pStyle");
        if (styles.getLength() == 0) {
            return 0;
        }
        String value = styles.item(0).getAttributes().getNamedItem("w:val") == null
                ? null : styles.item(0).getAttributes().getNamedItem("w:val").getNodeValue();
        if (value == null) {
            return 0;
        }
        String lower = value.toLowerCase();
        if (lower.equals("title")) {
            return 1;
        }
        if (lower.startsWith("heading")) {
            String digits = lower.substring("heading".length());
            try {
                int level = Integer.parseInt(digits);
                return level >= 1 && level <= 6 ? level : 0;
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static void flushParagraph(ParsedDoc doc, StringBuilder paragraph, String sectionNo, String sectionTitle) {
        if (paragraph.length() > 0) {
            String text = paragraph.toString().strip();
            if (!text.isEmpty()) {
                doc.add(new ParsedDoc.Block(sectionNo, sectionTitle, text));
            }
            paragraph.setLength(0);
        }
    }

    private static int headingLevel(String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        return level >= 1 && level <= 6 && level < line.length() && line.charAt(level) == ' ' ? level : 0;
    }

    private static String buildSectionNo(int[] headingCount, int level) {
        StringBuilder no = new StringBuilder();
        for (int i = 0; i < level; i++) {
            if (i > 0) {
                no.append('.');
            }
            no.append(headingCount[i]);
        }
        return no.toString();
    }
}
