package com.zufe.procurement.m10.kb.dto;

import lombok.Data;

import java.time.LocalDate;

/** 命中率统计查询（M10-5-2）：按测试批次或时间范围 */
@Data
public class TestStatQuery {

    private String testBatch;
    private String strategy;
    private LocalDate from;
    private LocalDate to;
}
