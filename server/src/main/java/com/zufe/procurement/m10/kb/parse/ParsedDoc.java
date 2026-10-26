package com.zufe.procurement.m10.kb.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析中间产物：线性块列表（M10-1-2 提取正文与标题层级）。
 * 每个块是文档中的一个段落，携带所在章节的编号与标题；
 * 切块器以块为最小单元，保证不跨条款切断。
 */
public class ParsedDoc {

    private final List<Block> blocks = new ArrayList<>();

    public void add(Block block) {
        blocks.add(block);
    }

    public List<Block> getBlocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }

    public int charCount() {
        return blocks.stream().mapToInt(b -> b.getText().length()).sum();
    }

    /**
     * 粘贴正文（M10-1-3 补录/校对）转为块列表：空行分段，
     * 每段一个块；段内条款起点由切块器的条款边界保护识别。
     */
    public static ParsedDoc fromPlainText(String text) {
        ParsedDoc doc = new ParsedDoc();
        if (text == null || text.isBlank()) {
            return doc;
        }
        for (String paragraph : text.split("\\R\\s*\\R")) {
            String trimmed = paragraph.strip();
            if (!trimmed.isEmpty()) {
                doc.add(new Block(null, null, trimmed));
            }
        }
        return doc;
    }

    public static class Block {

        /** 章节号，如 3.2.1；无标题层级的文档为 null */
        private final String sectionNo;
        private final String sectionTitle;
        private final String text;

        public Block(String sectionNo, String sectionTitle, String text) {
            this.sectionNo = sectionNo;
            this.sectionTitle = sectionTitle;
            this.text = text;
        }

        public String getSectionNo() {
            return sectionNo;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }

        public String getText() {
            return text;
        }
    }
}
