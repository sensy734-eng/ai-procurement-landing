package com.zufe.procurement.m4.arrival.dto;

import lombok.Data;

/**
 * 让步接收标记请求，功能点 M4-2-4。
 * 让步是整单动作：一单多行时不能只让一行——要么整单让步接收，
 * 要么整单不接收，避免同一张单出现两种入库口径。
 * flag=true 时理由必填；整单结果随之重算为 3 让步接收。
 */
@Data
public class ConcessionRequest {

    /** true 打让步标记，false 取消 */
    private boolean flag;

    /** 让步接收理由，flag=true 时必填 */
    private String reason;
}
