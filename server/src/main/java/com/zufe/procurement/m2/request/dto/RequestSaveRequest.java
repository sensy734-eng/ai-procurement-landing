package com.zufe.procurement.m2.request.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 申请单主表保存请求，功能点 M2-1-2 新建、M2-1-3 编辑。
 * 明细行经独立的行接口维护（M2-2-1/2），主表保存不触碰明细，
 * 与权限点 m2:request:create 和 m2:request_item:create 分离的口径一致。
 */
@Data
public class RequestSaveRequest {

    /** 申请部门，关联 dict_item.id（字典类型 dept） */
    private Long deptId;

    /** 申请日期，为空取当天 */
    private LocalDate requestDate;

    /** 需求日期，为空取明细行最早需求日期，服务层计算 */
    private LocalDate requireDate;

    private String remark;
}
