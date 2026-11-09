package com.zufe.procurement.m10.kb.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SemanticText {
    public static final String VERSION = "trim-lf-v1";
    private SemanticText() { }
    public static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }
    public static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
    public static double cosine(double[] a, double[] b) {
        if (a.length != b.length) throw new IllegalStateException("embedding_dimension_mismatch");
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            if (!Double.isFinite(a[i]) || !Double.isFinite(b[i])) throw new IllegalStateException("embedding_non_finite");
            dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i];
        }
        if (aa <= 0 || bb <= 0) throw new IllegalStateException("embedding_zero_vector");
        return dot / Math.sqrt(aa * bb);
    }
}
