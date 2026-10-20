package com.zufe.procurement.m11.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增与编辑请求，功能点 M11-2-5。
 * 内置四角色允许改名与改状态，不允许删除，编码不可改。
 */
@Data
public class RoleSaveRequest {

    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$", message = "角色编码需小写字母开头，2 至 50 位小写字母数字下划线")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不超过 50 字")
    private String roleName;

    /** 1 表示该角色数据范围为全量，仅系统管理员应为 1 */
    private Integer dataScopeAll;

    private Integer status;

    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;
}
