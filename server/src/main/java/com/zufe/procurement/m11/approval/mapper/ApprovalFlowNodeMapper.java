package com.zufe.procurement.m11.approval.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m11.approval.entity.ApprovalFlowNode;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 审批流程节点配置 Mapper，功能点 M11-5-1、M11-5-2。
 * 保存按单据类型整体重建，物理删除，避免逻辑删除的行在多次保存后累积。
 */
public interface ApprovalFlowNodeMapper extends BaseMapper<ApprovalFlowNode> {

    @Delete("DELETE FROM approval_flow_node WHERE bill_type = #{billType}")
    int deletePhysicallyByBillType(@Param("billType") Integer billType);
}
