package com.zufe.procurement.common;

import java.nio.charset.StandardCharsets;

/**
 * CSV 导出工具，零新增依赖，导出文件以 UTF-8 BOM 开头，Excel 双击可直接打开不乱码。
 * 不用 Excel 二进制库的理由：规划书第三章未列 POI 一类组件，
 * 而本项目的导出场景只有审计与汇总两类平表，CSV 足够且不引入外部依赖。
 */
public final class CsvWriter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvWriter() {
    }

    /**
     * 拼装完整 CSV 文本，首行为表头。
     * 字段含逗号、双引号或换行时用双引号包裹，内部双引号翻倍转义。
     */
    public static byte[] toCsv(String[] headers, Iterable<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, headers);
        for (String[] row : rows) {
            appendRow(sb, row);
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, result, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, result, UTF8_BOM.length, body.length);
        return result;
    }

    private static void appendRow(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
    }

    private static String escape(String cell) {
        if (cell == null) {
            return "";
        }
        boolean needQuote = cell.indexOf(',') >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
        if (!needQuote) {
            return cell;
        }
        return "\"" + cell.replace("\"", "\"\"") + "\"";
    }
}
