package com.zufe.procurement.m10.kb.mapper;

import com.zufe.procurement.m10.kb.entity.KbSemanticVector;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface KbSemanticVectorMapper {
    @Select("SELECT * FROM kb_semantic_vector WHERE config_fingerprint = #{fingerprint}")
    List<KbSemanticVector> byConfig(String fingerprint);

    @Insert("""
        INSERT INTO kb_semantic_vector
        (chunk_id, config_fingerprint, model, dimensions, preprocessing_version, content_hash, vector_json)
        VALUES (#{chunkId}, #{configFingerprint}, #{model}, #{dimensions}, #{preprocessingVersion}, #{contentHash}, #{vectorJson})
        ON DUPLICATE KEY UPDATE model=VALUES(model), dimensions=VALUES(dimensions),
          preprocessing_version=VALUES(preprocessing_version), content_hash=VALUES(content_hash), vector_json=VALUES(vector_json)
        """)
    int upsert(KbSemanticVector vector);
}
