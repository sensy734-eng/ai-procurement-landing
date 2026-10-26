package com.zufe.procurement.m6.price.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m6.price.entity.PriceReference;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 价格参考 Mapper（M6-1-1）。
 * 注意：表无逻辑删除之外的删除语义，查询一律显式带 is_deleted=0。
 */
public interface PriceReferenceMapper extends BaseMapper<PriceReference> {

    /** 同物料当前最大版本号（新建版本时取 max+1） */
    @Select("SELECT COALESCE(MAX(price_version), 0) FROM price_reference WHERE material_id = #{materialId} AND is_deleted = 0")
    Integer maxVersionByMaterial(@Param("materialId") Long materialId);

    /**
     * 选价：基准日当天生效的参考价。
     * 同一物料允许多个生效版本并存（未来生效日预售），取生效日不超过基准日的最新一版。
     */
    @Select("SELECT * FROM price_reference WHERE material_id = #{materialId} AND status = 1 "
            + "AND effective_date <= #{onDate} AND is_deleted = 0 "
            + "ORDER BY effective_date DESC, price_version DESC LIMIT 1")
    PriceReference selectEffective(@Param("materialId") Long materialId, @Param("onDate") LocalDate onDate);

    /**
     * 还原选价：基准日当天的「最新版」，不限 status。
     * 用于证据面板还原下单时点——当时生效的版本现在可能已停用，
     * 但按生效日取最新仍能还原决策时点的参考价。
     */
    @Select("SELECT * FROM price_reference WHERE material_id = #{materialId} "
            + "AND effective_date <= #{onDate} AND is_deleted = 0 "
            + "ORDER BY effective_date DESC, price_version DESC LIMIT 1")
    PriceReference selectEffectiveAsOf(@Param("materialId") Long materialId, @Param("onDate") LocalDate onDate);

    /** 版本历史：同物料全版本，新版本在前 */
    @Select("SELECT * FROM price_reference WHERE material_id = #{materialId} AND is_deleted = 0 "
            + "ORDER BY price_version DESC")
    List<PriceReference> selectVersions(@Param("materialId") Long materialId);

    /** 新版本创建后，停用生效日不晚于新版本的旧生效版（未来版本可与旧版并存；排除新版本自身） */
    @Update("UPDATE price_reference SET status = 2, updated_by = #{operatorId}, updated_at = NOW() "
            + "WHERE material_id = #{materialId} AND id != #{newId} AND status = 1 AND is_deleted = 0 "
            + "AND effective_date <= #{newEffectiveDate}")
    int retireOlderVersions(@Param("materialId") Long materialId,
                            @Param("newId") Long newId,
                            @Param("newEffectiveDate") LocalDate newEffectiveDate,
                            @Param("operatorId") Long operatorId);
}
