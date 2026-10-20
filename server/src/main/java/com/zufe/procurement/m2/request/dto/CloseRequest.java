package com.zufe.procurement.m2.request.dto;

import lombok.Data;

/**
 * 整单关闭请求，功能点 M2-3-5。关闭原因必填。
 * 存在未转数量时前端按 M2-4-4 给出确认提示，服务端只校验状态，不拦截关闭。
 */
@Data
public class CloseRequest {

    private String reason;
}
