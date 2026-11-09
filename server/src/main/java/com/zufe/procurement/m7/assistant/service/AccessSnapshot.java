package com.zufe.procurement.m7.assistant.service;

import com.zufe.procurement.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeSet;

/** Invalidates saved AI context after any authority or scope change. No credentials are stored. */
public final class AccessSnapshot {
    private AccessSnapshot() {}
    public static String fingerprint(CurrentUser user) {
        var scope = user.getScope();
        String value = user.getUserId() + "|" + new TreeSet<>(user.getPermissions()) + "|"
                + scope.isAll() + "|" + new TreeSet<>(scope.getDeptIds()) + "|"
                + new TreeSet<>(scope.getWarehouseIds()) + "|" + new TreeSet<>(scope.getOwnerNos());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
