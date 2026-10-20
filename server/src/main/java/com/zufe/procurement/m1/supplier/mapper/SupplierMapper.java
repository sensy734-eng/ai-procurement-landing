package com.zufe.procurement.m1.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 供应商 Mapper，功能点 M1-2-1 至 M1-2-3。
 */
public interface SupplierMapper extends BaseMapper<Supplier> {

    /** 连带已删除记录查重：唯一键 uk_supplier_no 不含 is_deleted */
    @Select("SELECT COUNT(*) FROM supplier WHERE supplier_no = #{supplierNo}")
    long countBySupplierNoIncludeDeleted(@Param("supplierNo") String supplierNo);
}
