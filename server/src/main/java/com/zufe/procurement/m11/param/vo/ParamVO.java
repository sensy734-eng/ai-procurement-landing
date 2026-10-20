package com.zufe.procurement.m11.param.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 参数展示对象，功能点 M11-4-1、M11-4-2、M11-4-3。
 * 附带 editable 与 remark，界面据此决定是否可改以及口径说明。
 */
@Data
public class ParamVO {

    private Long id;
    private String paramCode;
    private String paramName;
    private String paramValue;
    private String paramType;
    private String paramGroup;
    private Integer editable;
    private String remark;
    private LocalDateTime updatedAt;
}
