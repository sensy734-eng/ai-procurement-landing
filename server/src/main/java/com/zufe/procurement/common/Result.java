package com.zufe.procurement.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回体。code 取七类业务结果码之一。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private String code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static Result<Void> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        return new Result<>(code.getCode(), message, null);
    }

    /**
     * 非业务异常的兜底返回，不属于七类业务结果码，不用于业务分支判断。
     */
    public static <T> Result<T> systemError(String message) {
        return new Result<>("SystemError", message, null);
    }
}
