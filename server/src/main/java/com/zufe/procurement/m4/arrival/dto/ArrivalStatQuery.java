package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 验收结果统计检索条件，功能点 M4-4-3。
 * 统计是跨仓库的质量视角（归采购主管），不注入个人数据范围——
 * 部门维度的授权对仓库维度的验收单无意义，注入会永远返回空。
 */
@Data
public class ArrivalStatQuery {

    private LocalDate startDate;

    private LocalDate endDate;

    private Long supplierId;
}
