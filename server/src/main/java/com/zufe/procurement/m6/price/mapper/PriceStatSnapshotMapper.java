package com.zufe.procurement.m6.price.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m6.price.entity.PriceStatSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成交价统计快照 Mapper（M6-1-2、M6-1-3）。
 * 快照写入走 upsert（唯一键 material_id + supplier_id + stat_type + stat_window）。
 */
public interface PriceStatSnapshotMapper extends BaseMapper<PriceStatSnapshot> {

    /** 某物料某归属口径的全部统计行 */
    @Select("SELECT * FROM price_stat_snapshot WHERE material_id = #{materialId} "
            + "AND supplier_id = #{supplierId} AND is_deleted = 0 ORDER BY stat_type")
    List<PriceStatSnapshot> selectByMaterialSupplier(@Param("materialId") Long materialId,
                                                     @Param("supplierId") Long supplierId);

    /** 已算过快照的物料清单（刷新时按需跳过或覆盖） */
    @Select("SELECT DISTINCT material_id FROM price_stat_snapshot WHERE is_deleted = 0")
    List<Long> selectDistinctMaterialIds();

    /**
     * upsert：重复键（物料+归属+类型+窗口）时更新统计值，
     * 保证同一口径全局只有一行快照，刷新是覆盖不是堆积。
     */
    @Insert("INSERT INTO price_stat_snapshot (material_id, supplier_id, stat_type, stat_value, stat_window, "
            + "sample_count, source_bill_no, calc_time, created_by) VALUES "
            + "(#{materialId}, #{supplierId}, #{statType}, #{statValue}, #{statWindow}, #{sampleCount}, "
            + "#{sourceBillNo}, NOW(), #{operatorId}) "
            + "ON DUPLICATE KEY UPDATE stat_value = VALUES(stat_value), sample_count = VALUES(sample_count), "
            + "source_bill_no = VALUES(source_bill_no), calc_time = VALUES(calc_time), "
            + "updated_at = NOW()")
    int upsert(@Param("materialId") Long materialId,
               @Param("supplierId") Long supplierId,
               @Param("statType") Integer statType,
               @Param("statValue") java.math.BigDecimal statValue,
               @Param("statWindow") String statWindow,
               @Param("sampleCount") Integer sampleCount,
               @Param("sourceBillNo") String sourceBillNo,
               @Param("operatorId") Long operatorId);
}
