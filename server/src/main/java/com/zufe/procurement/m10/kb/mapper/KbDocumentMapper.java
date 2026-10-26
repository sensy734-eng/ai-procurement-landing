package com.zufe.procurement.m10.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zufe.procurement.m10.kb.entity.KbDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentMapper extends BaseMapper<KbDocument> {

    /**
     * 物理删除文档（M10-4-5）。
     * 全局配置了 logic-delete-field: isDeleted，BaseMapper.deleteById 会降级为
     * 逻辑删除（UPDATE is_deleted=1），而知识库文档删除的口径是物理删除并同步
     * 清理索引（规划书 5.1 第 10 条例外），因此这里走原生 DELETE 绕过拦截。
     */
    @Delete("DELETE FROM kb_document WHERE id = #{id}")
    int deleteByIdPhysical(Long id);
}
