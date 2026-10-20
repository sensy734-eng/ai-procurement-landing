package com.zufe.procurement.common;

/**
 * 业务结果码，七类，定义见规划书 2.4 与受控执行边界。
 * Success / NotFound / Ambiguous / NoPermission / NoData / Unsupported / InvalidParameter。
 * NotFound 表示单据编号不存在，NoData 表示条件内无匹配数据，两者不得合并。
 */
public enum ResultCode {

    SUCCESS("Success", "操作成功"),
    NOT_FOUND("NotFound", "单据不存在"),
    AMBIGUOUS("Ambiguous", "问题存在歧义，请补充条件"),
    NO_PERMISSION("NoPermission", "无权限访问"),
    NO_DATA("NoData", "条件下无匹配数据"),
    UNSUPPORTED("Unsupported", "暂不支持该操作"),
    INVALID_PARAMETER("InvalidParameter", "参数不合法");

    private final String code;
    private final String message;

    ResultCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
