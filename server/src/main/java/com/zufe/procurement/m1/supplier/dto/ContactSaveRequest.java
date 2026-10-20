package com.zufe.procurement.m1.supplier.dto;

import lombok.Data;

import java.util.List;

/**
 * 供应商联系人整体保存请求，功能点 M1-2-4。
 * 一对多列表整体替换：传入全量联系人，未传入的即删除。
 */
@Data
public class ContactSaveRequest {

    private List<Item> contacts;

    @Data
    public static class Item {
        private Long id;
        private String contactName;
        private String position;
        private String phone;
        private String email;
        private Integer isDefault;
        private Integer status;
        private String remark;
    }
}
