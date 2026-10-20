package com.zufe.procurement.m2.request.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m2.request.entity.PurchaseRequest;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 采购申请单 Mapper。uk_request_no 不含 is_deleted，
 * 单号由编号规则生成，并发下可能撞唯一键，生成处按"查最大流水 + 插入重试"处理。
 */
public interface PurchaseRequestMapper extends BaseMapper<PurchaseRequest> {

    /**
     * 按单号前缀查当日最大流水后缀，用于编号续号。连已删除记录一起查，
     * 单号一旦生成不回收，避免演示数据清理后单号复用造成混淆。
     */
    @Select("""
            SELECT MAX(CAST(SUBSTRING(request_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM purchase_request
            WHERE request_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxSeqByPrefix(@Param("prefix") String prefix);
}
