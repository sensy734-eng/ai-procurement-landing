package com.zufe.procurement.m10.kb.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 知识文档（M10-1、M10-4），对应 kb_document。
 *
 * 口径：
 * - 同一制度多版本并存（多行），检索时按生效日期取现行有效版本优先（M8 融合排序用）；
 * - 文档删除走物理删除并同步清理切块与倒排索引（规划书 5.1 第 10 条例外），
 *   因此本实体不加 @TableLogic，is_deleted 列保留仅为结构统一；
 * - parse_status：1 待解析、2 解析中、3 解析成功、4 解析失败。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String docName;
    /** 文档类型：1 制度、2 规范、3 操作手册、4 流程说明 */
    private Integer docType;
    private String version;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    /** 状态：1 启用、2 停用、3 失效；停用与失效不参与默认召回 */
    private Integer status;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private Integer parseStatus;
    private Integer chunkCount;
    private String tags;
    private Long uploadBy;
    private LocalDateTime uploadAt;
    private String remark;
    private Integer isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
