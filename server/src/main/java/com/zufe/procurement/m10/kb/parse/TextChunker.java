package com.zufe.procurement.m10.kb.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 条款边界保护切块器（M10-2-1 / M10-2-2）。
 *
 * 口径：
 * - 块为最小单元，不跨带编号条款切断（第X条 / 3.2.1 / 1、 / 一、 / （一）等开头
 *   的段落视为条款起点，新条款强制开新块）；
 * - 目标块长 chunkSize（sys_param kb_chunk_size，300~600 字），条款完整时
 *   块间不重叠——重叠文本会跨条款制造重复片段，与条款边界保护冲突；
 * - 单条款超过 chunkSize*1.5 时按句界（。；！？）硬切，相邻片段重叠
 *   overlap 个字（kb_chunk_overlap），此时才需要重叠衔接；
 * - 单句仍超长时按 chunkSize 直接切，同样保留重叠。
 */
public class TextChunker {

    private static final Pattern CLAUSE_START = Pattern.compile(
            "^(第[一二三四五六七八九十百零]+条" +
            "|\\d+(\\.\\d+)+" +
            "|\\d+、" +
            "|[一二三四五六七八九十]+、" +
            "|（[一二三四五六七八九十]+）" +
            "|\\([0-9]+\\))");

    private static final Pattern SENTENCE_END = Pattern.compile("[。；！？!?;]");

    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        this.chunkSize = Math.max(100, chunkSize);
        this.overlap = Math.max(0, Math.min(overlap, this.chunkSize / 2));
    }

    public List<ChunkDraft> chunk(ParsedDoc doc) {
        List<ChunkDraft> result = new ArrayList<>();
        List<ParsedDoc.Block> current = new ArrayList<>();
        int currentLen = 0;
        String currentNo = null;
        String currentTitle = null;

        for (ParsedDoc.Block block : doc.getBlocks()) {
            boolean clauseStart = isClauseStart(block.getText());
            boolean sectionChanged = block.getSectionNo() != null
                    && !block.getSectionNo().equals(currentNo);
            if (!current.isEmpty() && (clauseStart || sectionChanged)
                    && currentLen + block.getText().length() > chunkSize / 2) {
                flush(result, current, currentNo, currentTitle);
                current = new ArrayList<>();
                currentLen = 0;
            }
            if (!current.isEmpty() && currentLen + block.getText().length() > chunkSize) {
                flush(result, current, currentNo, currentTitle);
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(block);
            currentLen += block.getText().length();
            if (block.getSectionNo() != null) {
                currentNo = block.getSectionNo();
                currentTitle = block.getSectionTitle();
            }
            if (currentLen >= chunkSize) {
                flush(result, current, currentNo, currentTitle);
                current = new ArrayList<>();
                currentLen = 0;
            }
        }
        flush(result, current, currentNo, currentTitle);

        List<ChunkDraft> expanded = new ArrayList<>();
        int index = 1;
        for (ChunkDraft draft : result) {
            if (draft.getText().length() <= (long) chunkSize * 3 / 2) {
                draft.setIndex(index++);
                expanded.add(draft);
            } else {
                for (ChunkDraft piece : hardSplit(draft.getText(), draft.getSectionNo(), draft.getSectionTitle())) {
                    piece.setIndex(index++);
                    expanded.add(piece);
                }
            }
        }
        return expanded;
    }

    private void flush(List<ChunkDraft> result, List<ParsedDoc.Block> blocks, String no, String title) {
        if (blocks.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (ParsedDoc.Block block : blocks) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(block.getText());
        }
        result.add(new ChunkDraft(text.toString(), no, title));
    }

    /** 条款内硬切：优先按句界，单句仍超长按长度切；相邻片段保留 overlap 字重叠 */
    private List<ChunkDraft> hardSplit(String text, String no, String title) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (SENTENCE_END.matcher(String.valueOf(text.charAt(i))).matches()) {
                sentences.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            sentences.add(text.substring(start));
        }

        List<ChunkDraft> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String sentence : sentences) {
            if (piece.length() > 0 && piece.length() + sentence.length() > chunkSize) {
                pieces.add(new ChunkDraft(piece.toString(), no, title));
                String tail = piece.length() > overlap ? piece.substring(piece.length() - overlap) : piece.toString();
                piece = new StringBuilder(tail);
            }
            piece.append(sentence);
            while (piece.length() > chunkSize * 3 / 2) {
                pieces.add(new ChunkDraft(piece.substring(0, chunkSize), no, title));
                piece = new StringBuilder(piece.substring(chunkSize - overlap));
            }
        }
        if (piece.length() > 0) {
            pieces.add(new ChunkDraft(piece.toString(), no, title));
        }
        return pieces;
    }

    private static boolean isClauseStart(String text) {
        return CLAUSE_START.matcher(text.trim()).find();
    }

    /** 切块草稿：序号在全部块打包完成后统一编 */
    public static class ChunkDraft {

        private int index;
        private final String text;
        private final String sectionNo;
        private final String sectionTitle;

        public ChunkDraft(String text, String sectionNo, String sectionTitle) {
            this.text = text;
            this.sectionNo = sectionNo;
            this.sectionTitle = sectionTitle;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }

        public String getText() {
            return text;
        }

        public String getSectionNo() {
            return sectionNo;
        }

        public String getSectionTitle() {
            return sectionTitle;
        }
    }
}
