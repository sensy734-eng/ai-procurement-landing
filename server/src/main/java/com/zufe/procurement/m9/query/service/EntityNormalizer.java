package com.zufe.procurement.m9.query.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m1.material.entity.Material;
import com.zufe.procurement.m1.material.mapper.MaterialMapper;
import com.zufe.procurement.m1.supplier.entity.Supplier;
import com.zufe.procurement.m1.supplier.mapper.SupplierMapper;
import com.zufe.procurement.m1.warehouse.entity.Warehouse;
import com.zufe.procurement.m1.warehouse.mapper.WarehouseMapper;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m9.query.entity.AiEntityAlias;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体归一与消歧（M9-3-1、M9-3-2）。
 *
 * 三级匹配，逐级放宽：
 * 1. 编号或全名命中（问句包含 material_no / material_name）；
 * 2. 别名命中（ai_entity_alias，人工维护的口语化名称）；
 * 3. 片段模糊命中（问句的 2~4 字片段被档案名称包含），只在精确与别名都没命中时启用。
 *
 * 同一类型命中多个实体即返回候选列表，由上层回 Ambiguous（M9-3-2），
 * 不允许静默取第一个——这正是「不臆造答案」在实体层的落点。
 * 停用档案不参与匹配（与页面下拉一致）。
 */
@Service
public class EntityNormalizer {

    /** 常见疑问词片段，避免「多少」「什么」这类片段被当成实体名 */
    private static final Set<String> STOP_FRAGMENTS = Set.of(
            "多少", "什么", "怎么", "这个", "那个", "哪个", "哪些", "一下", "还有", "现在",
            "可以", "是否", "请问", "帮我", "查查", "查询", "看看", "告诉", "目前",
            "最近", "上个月", "这个月", "本月的", "的是", "了吗", "没有");

    /**
     * 通用业务名词：不能当实体名用。
     *
     * 模糊匹配是从问句里切 2~4 字片段去撞档案名的，「材料」这种词几乎必然撞上
     * 「宁波盛达金属材料有限公司」里的「金属材料」——于是「供应商准入需要提供哪些
     * 资质材料」被归一成「宁波盛达」，再经实体先验把意图顶成供应商交付，最后拿这家
     * 公司的交付统计去回答一个问资质的问题。这类误命中在结构上比阈值更难发现，
     * 所以在这里显式挡住，而不是继续调相似度。
     */
    private static final Set<String> GENERIC_NOUNS = Set.of(
            "材料", "公司", "有限", "供应", "客户", "库存", "订单", "物料", "合同", "资质",
            "准入", "需要", "提供", "情况", "数据", "统计", "交付", "价格", "金额", "数量",
            "单据", "审批", "制度", "流程", "报告", "报表", "明细", "列表", "明细表");

    private static final int TYPE_MATERIAL = 1;
    private static final int TYPE_SUPPLIER = 2;
    private static final int TYPE_WAREHOUSE = 3;

    private final MaterialMapper materialMapper;
    private final SupplierMapper supplierMapper;
    private final WarehouseMapper warehouseMapper;
    private final AiIntentCatalog catalog;

    public EntityNormalizer(MaterialMapper materialMapper, SupplierMapper supplierMapper,
                            WarehouseMapper warehouseMapper, AiIntentCatalog catalog) {
        this.materialMapper = materialMapper;
        this.supplierMapper = supplierMapper;
        this.warehouseMapper = warehouseMapper;
        this.catalog = catalog;
    }

    /** 按类型分组命中的实体；每个类型一组，组内多候选即歧义 */
    public List<Group> detect(String question) {
        String normalized = question == null ? "" : question;
        Map<Integer, Group> groups = new LinkedHashMap<>();
        List<Archive> archives = loadArchives();
        Map<String, List<AiEntityAlias>> aliasIndex = aliasIndex();

        for (Archive archive : archives) {
            Match match = matchArchive(normalized, archive, aliasIndex.get(key(archive.type, archive.no)));
            if (match == null) {
                continue;
            }
            Group group = groups.computeIfAbsent(archive.type, Group::new);
            group.add(archive, match);
        }
        // 精确与别名都没命中时，才做片段模糊匹配（只为消歧，不为猜实体）
        for (Integer type : List.of(TYPE_MATERIAL, TYPE_SUPPLIER, TYPE_WAREHOUSE)) {
            Group group = groups.get(type);
            if (group != null && group.getCandidates().size() > 1) {
                continue;
            }
            if (group != null) {
                continue;
            }
            Group fuzzy = fuzzyGroup(type, archives, normalized);
            if (fuzzy != null) {
                groups.put(type, fuzzy);
            }
        }
        return new ArrayList<>(groups.values());
    }

    private Group fuzzyGroup(int type, List<Archive> archives, String question) {
        String best = null;
        List<Archive> hits = new ArrayList<>();
        for (int len = Math.min(4, question.length()); len >= 2; len--) {
            for (int i = 0; i + len <= question.length(); i++) {
                String fragment = question.substring(i, i + len);
                if (STOP_FRAGMENTS.contains(fragment) || GENERIC_NOUNS.contains(fragment)) {
                    continue;
                }
                List<Archive> matched = new ArrayList<>();
                for (Archive archive : archives) {
                    if (archive.type == type && archive.name.contains(fragment)) {
                        matched.add(archive);
                    }
                }
                if (!matched.isEmpty() && (best == null || fragment.length() > best.length())) {
                    best = fragment;
                    hits = matched;
                }
            }
            if (best != null) {
                break;
            }
        }
        if (best == null) {
            return null;
        }
        Group group = new Group(type);
        for (Archive archive : hits) {
            ChatVO.EntityRef ref = new ChatVO.EntityRef();
            ref.setType(archive.type);
            ref.setTypeName(typeName(archive.type));
            ref.setId(archive.id);
            ref.setNo(archive.no);
            ref.setName(archive.name);
            ref.setMatchedBy("fuzzy");
            group.getCandidates().add(ref);
        }
        group.setMention(best);
        return group;
    }

    private Match matchArchive(String question, Archive archive, List<AiEntityAlias> aliases) {
        String mention = null;
        String matchedBy = null;
        if (archive.no != null && question.toUpperCase().contains(archive.no.toUpperCase())) {
            mention = archive.no;
            matchedBy = "no";
        } else if (archive.name != null && question.contains(archive.name)) {
            mention = archive.name;
            matchedBy = "name";
        } else if (aliases != null) {
            for (AiEntityAlias alias : aliases) {
                if (alias.getAliasName() != null && question.contains(alias.getAliasName())) {
                    if (mention == null || alias.getAliasName().length() > mention.length()) {
                        mention = alias.getAliasName();
                        matchedBy = "alias";
                    }
                }
            }
        }
        return mention == null ? null : new Match(mention, matchedBy);
    }

    private List<Archive> loadArchives() {
        List<Archive> archives = new ArrayList<>();
        for (Material material : materialMapper.selectList(new LambdaQueryWrapper<Material>()
                .eq(Material::getStatus, 1))) {
            archives.add(new Archive(TYPE_MATERIAL, material.getId(), material.getMaterialNo(),
                    material.getMaterialName()));
        }
        for (Supplier supplier : supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getStatus, 1))) {
            archives.add(new Archive(TYPE_SUPPLIER, supplier.getId(), supplier.getSupplierNo(),
                    supplier.getSupplierName()));
        }
        for (Warehouse warehouse : warehouseMapper.selectList(
                new LambdaQueryWrapper<Warehouse>().isNotNull(Warehouse::getId))) {
            archives.add(new Archive(TYPE_WAREHOUSE, warehouse.getId(), warehouse.getWarehouseNo(),
                    warehouse.getWarehouseName()));
        }
        return archives;
    }

    private Map<String, List<AiEntityAlias>> aliasIndex() {
        Map<String, List<AiEntityAlias>> index = new LinkedHashMap<>();
        for (AiEntityAlias alias : catalog.aliases()) {
            index.computeIfAbsent(key(alias.getEntityType(), alias.getEntityNo()), k -> new ArrayList<>())
                    .add(alias);
        }
        return index;
    }

    private String key(Integer type, String name) {
        return type + "#" + name;
    }

    public String typeName(Integer type) {
        if (type == null) {
            return "未知";
        }
        return switch (type) {
            case TYPE_MATERIAL -> "物料";
            case TYPE_SUPPLIER -> "供应商";
            case TYPE_WAREHOUSE -> "仓库";
            default -> "实体";
        };
    }

    /**
     * 按类型 + 档案 id 反查实体引用（M9-3-2）。
     * 参数修正重跑时问句里的旧实体已被 override 覆盖，展示口径必须跟着换，
     * 否则会出现「答案说的是 A、卡片上还是 B」的自相矛盾。
     */
    public ChatVO.EntityRef refById(Integer type, Long id) {
        if (type == null || id == null) {
            return null;
        }
        for (Archive archive : loadArchives()) {
            if (archive.type() == type && id.equals(archive.id())) {
                ChatVO.EntityRef ref = new ChatVO.EntityRef();
                ref.setType(type);
                ref.setTypeName(typeName(type));
                ref.setId(archive.id());
                ref.setNo(archive.no());
                ref.setName(archive.name());
                return ref;
            }
        }
        return null;
    }

    private record Archive(int type, Long id, String no, String name) {
    }

    private record Match(String mention, String matchedBy) {
    }

    /** 同一类型的命中集合 */
    @Data
    public static class Group {
        private final Integer type;
        private String mention;
        private List<ChatVO.EntityRef> candidates = new ArrayList<>();

        public Group(Integer type) {
            this.type = type;
        }

        void add(Archive archive, Match match) {
            boolean exists = candidates.stream().anyMatch(c -> c.getId().equals(archive.id));
            if (exists) {
                return;
            }
            ChatVO.EntityRef ref = new ChatVO.EntityRef();
            ref.setType(archive.type);
            ref.setTypeName(archive.type == 1 ? "物料" : archive.type == 2 ? "供应商" : "仓库");
            ref.setId(archive.id);
            ref.setNo(archive.no);
            ref.setName(archive.name);
            ref.setMatchedBy(match.matchedBy());
            candidates.add(ref);
            if (mention == null || match.mention().length() > mention.length()) {
                mention = match.mention();
            }
            candidates.sort(Comparator.comparing(ChatVO.EntityRef::getNo));        }
    }
}
