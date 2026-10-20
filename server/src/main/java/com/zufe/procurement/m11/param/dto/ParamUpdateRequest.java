package com.zufe.procurement.m11.param.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 参数值更新请求，功能点 M11-4-1、M11-4-2、M11-4-3。
 * 值以字符串提交，服务端按 param_type 解析并校验范围。
 */
@Data
public class ParamUpdateRequest {

    @NotBlank(message = "参数值不能为空")
    @Size(max = 500, message = "参数值不超过 500 字")
    private String paramValue;
}
