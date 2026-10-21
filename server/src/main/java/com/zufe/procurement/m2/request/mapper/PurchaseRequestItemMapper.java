package com.zufe.procurement.m2.request.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m2.request.entity.PurchaseRequestItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 采购申请明细行 Mapper。uk_request_item_no（request_id, item_no）不含 is_deleted，
 * 行号必须连同已删除行取最大分配——若只按未删行取最大，删掉最后一行后
 * 再新增会复用该行号撞唯一键（DuplicateKeyException）。轮次 5 修正：
 * 原实现过滤 is_deleted = 0，存在该隐患（冒烟未覆盖删最后行再加行路径）。
 */
public interface PurchaseRequestItemMapper extends BaseMapper<PurchaseRequestItem> {

    @Select("""
            SELECT * FROM purchase_request_item
            WHERE request_id = #{requestId} AND is_deleted = 0
            ORDER BY item_no
            """)
    List<PurchaseRequestItem> selectByRequestId(@Param("requestId") Long requestId);

    @Select("""
            SELECT COALESCE(MAX(item_no), 0) FROM purchase_request_item
            WHERE request_id = #{requestId}
            """)
    Integer maxItemNo(@Param("requestId") Long requestId);

    @Select("""
            <script>
            SELECT * FROM purchase_request_item
            WHERE request_id IN
            <foreach collection="requestIds" item="rid" open="(" separator="," close=")">#{rid}</foreach>
            AND is_deleted = 0
            ORDER BY request_id, item_no
            </script>
            """)
    List<PurchaseRequestItem> selectByRequestIds(@Param("requestIds") Collection<Long> requestIds);
}
