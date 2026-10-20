package com.zufe.procurement.m11.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增与编辑请求，功能点 M11-2-2，角色随用户一并提交对应 M11-2-6。
 * 编辑时账号不可改，避免历史日志与单据追溯断链。
 */
@Data
public class UserSaveRequest {

    /** 编辑时必填，新增为空 */
    private Long id;

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,49}$", message = "账号需字母开头，3 至 50 位字母数字下划线")
    private String username;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名不超过 50 字")
    private String realName;

    @NotNull(message = "部门不能为空")
    private Long deptId;

    @Size(max = 30, message = "联系电话不超过 30 位")
    private String phone;

    @Size(max = 100, message = "邮箱不超过 100 位")
    private String email;

    @Size(max = 200, message = "备注不超过 200 字")
    private String remark;

    /** 角色id列表，可多选，新增时至少一个 */
    private List<Long> roleIds;
}
