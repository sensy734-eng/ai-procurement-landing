package com.zufe.procurement.m1.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m1.supplier.entity.SupplierAttachment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 供应商资质附件 Mapper，功能点 M1-2-5。
 */
public interface SupplierAttachmentMapper extends BaseMapper<SupplierAttachment> {

    @Select("SELECT * FROM supplier_attachment WHERE supplier_id = #{supplierId} AND is_deleted = 0 ORDER BY id DESC")
    List<SupplierAttachment> selectBySupplierId(@Param("supplierId") Long supplierId);
}
