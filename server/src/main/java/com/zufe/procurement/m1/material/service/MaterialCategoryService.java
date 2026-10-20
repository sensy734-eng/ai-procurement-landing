package com.zufe.procurement.m1.material.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.common.BizException;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m1.material.dto.MaterialCategorySaveRequest;
import com.zufe.procurement.m1.material.entity.MaterialCategory;
import com.zufe.procurement.m1.material.mapper.MaterialCategoryMapper;
import com.zufe.procurement.m1.material.vo.MaterialCategoryVO;
import com.zufe.procurement.m11.system.service.OperationLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料分类服务，功能点 M1-1-7。
 * 分类树由 parent_id 组装，层级随父节点计算；更换父节点时
 * 防止成环并连带重算子树层级。
 */
@Slf4j
@Service
public class MaterialCategoryService {

    private final MaterialCategoryMapper categoryMapper;
    private final OperationLogService operationLogService;

    public MaterialCategoryService(MaterialCategoryMapper categoryMapper,
                                   OperationLogService operationLogService) {
        this.categoryMapper = categoryMapper;
        this.operationLogService = operationLogService;
    }

    /** M1-1-7 分类树，含停用分类（存量物料可能仍引用） */
    public List<MaterialCategoryVO> tree() {
        List<MaterialCategory> all = categoryMapper.selectAllForTree();
        Map<Long, MaterialCategoryVO> nodeById = new HashMap<>();
        for (MaterialCategory entity : all) {
            nodeById.put(entity.getId(), toVO(entity));
        }
        List<MaterialCategoryVO> roots = new ArrayList<>();
        for (MaterialCategory entity : all) {
            MaterialCategoryVO node = nodeById.get(entity.getId());
            MaterialCategoryVO parent = nodeById.get(entity.getParentId());
            if (entity.getParentId() != null && entity.getParentId() != 0 && parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        sortTree(roots);
        return roots;
    }

    /** M1-1-7 新增分类 */
    @Transactional
    public Long create(MaterialCategorySaveRequest request) {
        String categoryNo = requireText(request.getCategoryNo(), "分类编码");
        if (categoryMapper.countByCategoryNoIncludeDeleted(categoryNo) > 0) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "分类编码已存在：" + categoryNo);
        }
        long parentId = request.getParentId() == null ? 0 : request.getParentId();
        int levelNo = 1;
        if (parentId != 0) {
            MaterialCategory parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                throw new BizException(ResultCode.NOT_FOUND, "上级分类不存在");
            }
            levelNo = parent.getLevelNo() + 1;
        }
        MaterialCategory entity = new MaterialCategory();
        entity.setCategoryNo(categoryNo);
        entity.setCategoryName(requireText(request.getCategoryName(), "分类名称"));
        entity.setParentId(parentId);
        entity.setLevelNo(levelNo);
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
        categoryMapper.insert(entity);
        operationLogService.record("M1", "create", "material_category", entity.getId(), categoryNo,
                "新增物料分类 " + categoryNo);
        return entity.getId();
    }

    /**
     * M1-1-7 编辑分类。编码不可改；允许改父节点，
     * 但新父节点不能是自身或子孙，否则成环。
     */
    @Transactional
    public void update(Long id, MaterialCategorySaveRequest request) {
        MaterialCategory entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND, "物料分类不存在");
        }
        if (request.getCategoryNo() != null && !request.getCategoryNo().equals(entity.getCategoryNo())) {
            throw new BizException(ResultCode.INVALID_PARAMETER, "分类编码创建后不可修改");
        }
        long parentId = request.getParentId() == null ? entity.getParentId() : request.getParentId();
        if (parentId != entity.getParentId()) {
            if (parentId == id) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "上级分类不能是自身");
            }
            if (isDescendant(id, parentId)) {
                throw new BizException(ResultCode.INVALID_PARAMETER, "上级分类不能是自身的子孙节点");
            }
        }
        entity.setCategoryName(requireText(request.getCategoryName(), "分类名称"));
        entity.setParentId(parentId);
        entity.setSortNo(request.getSortNo() == null ? entity.getSortNo() : request.getSortNo());
        entity.setStatus(request.getStatus() == null ? entity.getStatus() : request.getStatus());
        entity.setRemark(request.getRemark());
        categoryMapper.updateById(entity);
        refreshLevels(id, parentId);
        operationLogService.record("M1", "update", "material_category", id, entity.getCategoryNo(),
                "编辑物料分类 " + entity.getCategoryNo());
    }

    /** parentId 是否为 id 的子孙节点：沿 parentId 上溯，遇到 id 即成环 */
    private boolean isDescendant(Long id, Long parentId) {
        Long current = parentId;
        int guard = 0;
        while (current != null && current != 0 && guard++ < 100) {
            if (current.equals(id)) {
                return true;
            }
            MaterialCategory node = categoryMapper.selectById(current);
            current = node == null ? null : node.getParentId();
        }
        return false;
    }

    /** 父节点变更后重算自身与子树的层级 */
    private void refreshLevels(Long id, long parentId) {
        MaterialCategory self = categoryMapper.selectById(id);
        int levelNo = 1;
        if (parentId != 0) {
            MaterialCategory parent = categoryMapper.selectById(parentId);
            if (parent != null) {
                levelNo = parent.getLevelNo() + 1;
            }
        }
        applyLevel(id, levelNo);
        if (self != null && self.getParentId() != parentId) {
            self.setParentId(parentId);
        }
    }

    private void applyLevel(Long id, int levelNo) {
        MaterialCategory node = categoryMapper.selectById(id);
        if (node == null || node.getLevelNo().equals(levelNo)) {
            if (node != null && !node.getLevelNo().equals(levelNo)) {
                node.setLevelNo(levelNo);
                categoryMapper.updateById(node);
            }
            return;
        }
        node.setLevelNo(levelNo);
        categoryMapper.updateById(node);
        List<MaterialCategory> children = categoryMapper.selectList(new LambdaQueryWrapper<MaterialCategory>()
                .eq(MaterialCategory::getParentId, id));
        for (MaterialCategory child : children) {
            applyLevel(child.getId(), levelNo + 1);
        }
    }

    private void sortTree(List<MaterialCategoryVO> nodes) {
        nodes.sort(Comparator.comparing(MaterialCategoryVO::getSortNo, Comparator.nullsFirst(Integer::compareTo))
                .thenComparing(MaterialCategoryVO::getId));
        for (MaterialCategoryVO node : nodes) {
            sortTree(node.getChildren());
        }
    }

    private MaterialCategoryVO toVO(MaterialCategory entity) {
        MaterialCategoryVO vo = new MaterialCategoryVO();
        vo.setId(entity.getId());
        vo.setCategoryNo(entity.getCategoryNo());
        vo.setCategoryName(entity.getCategoryName());
        vo.setParentId(entity.getParentId());
        vo.setLevelNo(entity.getLevelNo());
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setChildren(new ArrayList<>());
        return vo;
    }

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ResultCode.INVALID_PARAMETER, field + "不能为空");
        }
        return value.trim();
    }
}
