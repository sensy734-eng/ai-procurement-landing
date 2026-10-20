package com.zufe.procurement.m1.dict.service;

import com.zufe.procurement.m1.dict.entity.DictItem;
import com.zufe.procurement.m1.dict.mapper.DictItemMapper;
import com.zufe.procurement.m1.dict.vo.DictItemVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典查询服务，功能点 M1-4-1、M1-4-2。
 * 下拉选项统一由后端提供，前端不硬编码枚举。
 */
@Service
public class DictService {

    private final DictItemMapper dictItemMapper;

    public DictService(DictItemMapper dictItemMapper) {
        this.dictItemMapper = dictItemMapper;
    }

    public List<DictItemVO> items(String typeCode) {
        return dictItemMapper.selectByTypeCode(typeCode).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    private DictItemVO toVO(DictItem item) {
        DictItemVO vo = new DictItemVO();
        vo.setId(item.getId());
        vo.setItemCode(item.getItemCode());
        vo.setItemName(item.getItemName());
        vo.setParentId(item.getParentId());
        vo.setSortNo(item.getSortNo());
        vo.setExtValue(item.getExtValue());
        return vo;
    }
}
