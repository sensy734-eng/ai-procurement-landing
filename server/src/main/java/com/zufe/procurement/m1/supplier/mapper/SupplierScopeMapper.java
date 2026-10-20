package com.zufe.procurement.m1.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.supplier.entity.SupplierScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 供应商供货范围 Mapper，功能点 M1-2-6。
 * 唯一键 uk_scope 不含 is_deleted，保存时物理删除后重建。
 */
public interface SupplierScopeMapper extends BaseMapper<SupplierScope> {

    @Select("SELECT * FROM supplier_scope WHERE supplier_id = #{supplierId} AND is_deleted = 0 ORDER BY id")
    List<SupplierScope> selectBySupplierId(@Param("supplierId") Long supplierId);

    @Delete("DELETE FROM supplier_scope WHERE supplier_id = #{supplierId}")
    int physicalDeleteBySupplierId(@Param("supplierId") Long supplierId);
}
