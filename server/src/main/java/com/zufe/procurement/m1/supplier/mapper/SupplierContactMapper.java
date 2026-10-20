package com.zufe.procurement.m1.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.supplier.entity.SupplierContact;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 供应商联系人 Mapper，功能点 M1-2-4。
 */
public interface SupplierContactMapper extends BaseMapper<SupplierContact> {

    @Select("SELECT * FROM supplier_contact WHERE supplier_id = #{supplierId} AND is_deleted = 0 ORDER BY is_default DESC, id")
    List<SupplierContact> selectBySupplierId(@Param("supplierId") Long supplierId);

    @Select("SELECT COUNT(*) FROM supplier_contact WHERE supplier_id = #{supplierId} AND is_default = 1 AND is_deleted = 0")
    long countDefaultBySupplierId(@Param("supplierId") Long supplierId);
}
