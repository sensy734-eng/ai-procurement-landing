package com.zufe.procurement.m10.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档切块（M10-2、M10-3-1），对应 kb_chunk。
 *
 * 口径：
 * - 切块不跨带编号条款切断；条款本体超长时按句界硬切，相邻片段重叠
 *   kb_chunk_overlap 个字（重叠只发生在条款内硬切场景，条款完整时块间不重叠，
 *   否则重叠文本会跨条款制造重复片段，与条款边界保护冲突）；
 * - embedding 为 float32 数组序列化（本地确定性向量器产出），
 *   查询端以余弦相似度召回；倒排索引另存 kb_sparse_index。
 */
@Data
@TableName("kb_chunk")
public class KbChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long docId;
    private Integer chunkIndex;
    private String chunkText;
    private String sectionNo;
    private String sectionTitle;
    private Integer charCount;
    /** 向量，float32 小端字节序列 */
    private byte[] embedding;
    private String vectorModel;
    private Integer vectorDim;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
