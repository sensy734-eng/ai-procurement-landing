package com.zufe.procurement.m7.assistant.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zufe.procurement.auth.AuthContext;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Persist task evidence separately from legacy chat semantics; no business tables are written. */
@Repository
public class TaskRunStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TaskAccess access;
    public TaskRunStore(JdbcTemplate db, ObjectMapper json, TaskAccess access) {
        this.db = db; this.json = json; this.access = access;
    }
    public String json(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception error) { throw new IllegalStateException("无法保存任务记录", error); }
    }
    public String hash(Object value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    public void save(String id, String type, Long objectId, String fingerprint, Object result) {
        db.update("INSERT INTO ai_task_run(run_id,task_type,user_id,access_fingerprint,object_id,result_json) VALUES(?,?,?,?,?,?)",
                id, type, AuthContext.getOrThrow().getUserId(), fingerprint, objectId, json(result));
    }
    public <T> T load(String id, String type, Class<T> target) {
        var rows = db.queryForList("SELECT result_json,access_fingerprint FROM ai_task_run WHERE run_id=? AND task_type=? AND user_id=? AND created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)",
                id, type, AuthContext.getOrThrow().getUserId());
        if (rows.isEmpty()) throw new BizException(ResultCode.NOT_FOUND, "任务不存在或已过期，请重新生成");
        if (!access.fingerprint().equals(rows.get(0).get("access_fingerprint")))
            throw new BizException(ResultCode.NO_PERMISSION, "权限范围已变化，请重新生成");
        try { return json.readValue(String.valueOf(rows.get(0).get("result_json")), target); }
        catch (Exception error) { throw new IllegalStateException("任务记录无法读取", error); }
    }
    public void recordValidation(String id, Object value) {
        int changed = db.update("UPDATE ai_task_run SET confirmation_json=? WHERE run_id=? AND user_id=? AND access_fingerprint=? AND created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)",
                json(value), id, AuthContext.getOrThrow().getUserId(), access.fingerprint());
        if (changed != 1) throw new BizException(ResultCode.NO_PERMISSION, "任务已失效，请重新生成");
    }
}
