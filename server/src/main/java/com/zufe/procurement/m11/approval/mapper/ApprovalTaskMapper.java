package com.zufe.procurement.m11.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.approval.entity.ApprovalTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审批任务 Mapper。任务编号 TASK-{yyyyMMdd}-{seq:4}，
 * uk_task_no 不含 is_deleted，续号连同已删除记录一起取最大值。
 */
public interface ApprovalTaskMapper extends BaseMapper<ApprovalTask> {

    @Select("""
            SELECT * FROM approval_task
            WHERE bill_type = #{billType} AND bill_id = #{billId} AND is_deleted = 0
            ORDER BY node_seq, id
            """)
    List<ApprovalTask> selectByBill(@Param("billType") Integer billType,
                                    @Param("billId") Long billId);

    @Select("""
            SELECT * FROM approval_task
            WHERE bill_type = #{billType} AND bill_id = #{billId} AND node_seq = #{nodeSeq}
              AND is_deleted = 0
            ORDER BY id
            """)
    List<ApprovalTask> selectByBillAndNode(@Param("billType") Integer billType,
                                           @Param("billId") Long billId,
                                           @Param("nodeSeq") Integer nodeSeq);

    @Select("""
            SELECT COUNT(*) FROM approval_task
            WHERE bill_type = #{billType} AND bill_id = #{billId} AND node_seq = #{nodeSeq}
              AND task_status = 1 AND is_deleted = 0
            """)
    int countPendingByNode(@Param("billType") Integer billType,
                           @Param("billId") Long billId,
                           @Param("nodeSeq") Integer nodeSeq);

    @Select("""
            SELECT MAX(CAST(SUBSTRING(task_no, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED))
            FROM approval_task
            WHERE task_no LIKE CONCAT(#{prefix}, '%')
            """)
    Integer maxSeqByPrefix(@Param("prefix") String prefix);
}
