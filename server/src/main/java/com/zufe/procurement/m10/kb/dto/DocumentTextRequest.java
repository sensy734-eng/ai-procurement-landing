package com.zufe.procurement.m10.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 正文补录/校对（M10-1-3） */
@Data
public class DocumentTextRequest {

    @NotBlank(message = "正文不能为空")
    @Size(max = 200000, message = "正文超过 20 万字上限")
    private String text;
}
