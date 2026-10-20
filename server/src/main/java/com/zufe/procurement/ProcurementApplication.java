package com.zufe.procurement;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 采购管理系统启动类。
 * 系统主体为采购业务，AI 助手为特色功能（M7~M9）。
 */
@SpringBootApplication
@MapperScan("com.zufe.procurement.**.mapper")
public class ProcurementApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcurementApplication.class, args);
    }
}
