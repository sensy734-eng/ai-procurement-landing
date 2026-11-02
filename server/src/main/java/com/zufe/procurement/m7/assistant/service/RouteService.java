package com.zufe.procurement.m7.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zufe.procurement.m11.param.entity.SysParam;
import com.zufe.procurement.m11.param.mapper.SysParamMapper;
import com.zufe.procurement.m7.assistant.support.RouteLexicon;
import com.zufe.procurement.m7.assistant.vo.AiContext;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 三类问题路由（M7-2-1、M7-2-2、M7-2-4）。
 *
 * 分工是「理解归模型、裁决归代码」：
 * 配置了模型就由模型先判三类归属，规则只在模型未配置、调用失败、自评不自信，
 * 或判定「三类都不属于」时兜底。反过来做（规则先筛、判不准才问模型）会把模型
 * 锁在规则的盲区里——规则一旦判准，哪怕只有 51% 的把握，模型也没机会纠正，
 * 而「不合格品能入库吗」这类主体词落在数据域、实际问的是规则的句子，
 * 恰恰是规则最容易判错的（实测三条 C3 用例全挂在这里）。
 *
 * 两条边界不随模型变化，仍在模型之前：
 * 1. 写操作拒绝——只读是铁律，模型说什么都不改。判定看语气不看词：
 *    「帮我把订单作废」是指令，「这张订单审批通过没有」是问状态，
 *    写操作词表里的「审批通过」两个都沾，只能靠命令标记/问信息标记区分；
 * 2. 超长问句截断——5000 字垃圾文本不整段喂给模型。
 *
 * 置信度用「证据占比」：最高分类得分 ÷ 三类得分之和（仅规则兜底时计算）；
 * 低于 ai_intent_confidence_threshold 时返回澄清，让用户自己选方向（M7-2-2）。
 * 含糊与越权不是第四、第五条通道，而是叠加在三类之上的异常分支。
 */
@Service
public class RouteService {

    private static final String PARAM_THRESHOLD = "ai_intent_confidence_threshold";
    private static final List<String> STRONG_TUTORIAL = List.of("怎么", "如何", "怎样", "步骤", "在哪", "入口", "怎么弄");

    /** 模型自评低于这个值就不接管，避免用一次含糊的判断替换另一次含糊的判断 */
    private static final double MODEL_MIN_CONFIDENCE = 0.5;

    private final SysParamMapper sysParamMapper;
    private final LlmRouteAdvisor routeAdvisor;

    public RouteService(SysParamMapper sysParamMapper, LlmRouteAdvisor routeAdvisor) {
        this.sysParamMapper = sysParamMapper;
        this.routeAdvisor = routeAdvisor;
    }

    public Decision decide(String question) {
        return decide(question, null);
    }

    public Decision decide(String question, AiContext context) {
        Decision decision = new Decision();
        String text = question == null ? "" : question.trim();
        if (text.isEmpty()) {
            decision.setStatus(Status.UNSUPPORTED);
            decision.setMessage("请输入具体问题。");
            return decision;
        }
        int tutorial = RouteLexicon.score(routeText(text), RouteLexicon.TUTORIAL);
        int knowledge = RouteLexicon.score(text, RouteLexicon.KNOWLEDGE);
        int data = RouteLexicon.score(text, RouteLexicon.DATA);
        boolean strongTutorial = STRONG_TUTORIAL.stream().anyMatch(text::contains);

        // 只读边界：命中写操作词，且这句话确实是在「下指令」而不是在「问状态」。
        // 这一条在模型之前判定，模型说什么都不改变「助手只读」这条铁律。
        if (!strongTutorial && RouteLexicon.isWriteInstruction(text)) {
            decision.setStatus(Status.READONLY_REFUSED);
            decision.setMessage("智能助手只提供查询与问答，不执行新增、修改、审批、删除等业务操作。"
                    + "如果你是想了解怎么操作，可以问「怎么提交采购申请」这类问题。");
            return decision;
        }

        // 理解归模型：配了模型就由它先判，不再先让规则筛一遍。
        Decision byModel = routeByModel(text, context);
        if (byModel != null) {
            return byModel;
        }

        // 规则兜底：模型未配置、调用失败、自评不自信，或判定「三类都不属于」时才走到这里。
        if (tutorial == 0 && knowledge == 0 && data == 0) {
            decision.setStatus(Status.UNSUPPORTED);
            decision.setMessage("这个问题不属于制度问答、操作教程、业务数据查询中的任何一类，我不能臆造答案。"
                    + "可以换成「审批时限是几天」「怎么提交采购申请」「CG-DD-20261011-0001 现在什么状态」。");
            return decision;
        }

        int total = tutorial + knowledge + data;
        int top = Math.max(tutorial, Math.max(knowledge, data));
        BigDecimal share = BigDecimal.valueOf(top)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        Integer route;
        if (tutorial == top) {
            route = 2;
        } else if (knowledge == top) {
            route = 1;
        } else {
            route = 3;
        }
        decision.setRoute(route);
        decision.setRouteName(routeName(route));
        decision.setConfidence(share);

        double threshold = threshold();
        if (share.doubleValue() < threshold) {
            // 模型已经在上面给了机会，这里不再重复调用：一次请求只花一次路由判定，
            // 否则「模型说不知道怎么分」和「规则说不知道怎么分」要各花一次钱。
            decision.setStatus(Status.CLARIFY);
            decision.setMessage("这个问题的类型不够明确（置信度 "
                    + share.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                    + "% 低于阈值 " + BigDecimal.valueOf(threshold * 100).setScale(0, RoundingMode.HALF_UP)
                    + "%），请选择想问的方向。");
            decision.setOptions(options());
            return decision;
        }
        decision.setStatus(Status.ROUTED);
        return decision;
    }

    /**
     * 模型优先判定三类归属（M7-2-1 的模型侧）。
     *
     * @return null 表示这次不由模型定：未配置 / 开关关闭 / 调用失败 / 自评置信度低于
     *         {@link #MODEL_MIN_CONFIDENCE} / 判定三类都不属于。返回 null 后由规则兜底。
     *
     *         最后一种情况（模判 NONE）必须回退规则复核，不能直接判不支持——
     *         模型说「不知道」不等于规则也答不上，直接据此拒绝会把规则本来答得上的
     *         问题砍掉（实测「台州的供应商」「订单金额大于 999999999999 的有哪些」都被这么砍过）。
     */
    private Decision routeByModel(String text, AiContext context) {
        LlmRouteAdvisor.Decision byModel = routeAdvisor == null ? null : routeAdvisor.decide(text, context);
        if (byModel == null || byModel.confidence() < MODEL_MIN_CONFIDENCE) {
            return null;
        }
        if (byModel.isNone()) {
            return null;
        }
        Decision decision = new Decision();
        decision.setModelAssisted(true);
        decision.setConfidence(BigDecimal.valueOf(byModel.confidence()).setScale(4, RoundingMode.HALF_UP));
        decision.setRoute(byModel.route());
        decision.setRouteName(routeName(byModel.route()));
        decision.setStatus(Status.ROUTED);
        return decision;
    }

    private List<ChatVO.Option> options() {
        List<ChatVO.Option> options = new ArrayList<>();
        options.add(option("查业务数据", "订单状态、库存可用量、成交价、交付进度", 3));
        options.add(option("问制度条款", "审批时限、验收要求、价格偏离标准", 1));
        options.add(option("看操作步骤", "某个单据怎么新增、怎么提交、在哪确认", 2));
        return options;
    }

    private ChatVO.Option option(String label, String detail, Integer route) {
        ChatVO.Option option = new ChatVO.Option();
        option.setLabel(label);
        option.setDetail(detail);
        option.setRoute(route);
        return option;
    }

    public String routeName(Integer route) {
        if (route == null) {
            return "未分类";
        }
        return switch (route) {
            case 1 -> "制度问答";
            case 2 -> "操作教程";
            case 3 -> "数据查询";
            default -> "未分类";
        };
    }

    /**
     * 「怎么样」是疑问语气词，不是操作类信号：去掉后再算教程分，
     * 否则「这家供应商交付怎么样」会被误判成「怎么看操作步骤」。
     */
    private String routeText(String text) {
        return text.replace("怎么样", "").replace("怎么办", "");
    }

    private double threshold() {        SysParam param = sysParamMapper.selectOne(new LambdaQueryWrapper<SysParam>()
                .eq(SysParam::getParamCode, PARAM_THRESHOLD)
                .last("LIMIT 1"));
        if (param == null || param.getParamValue() == null) {
            return 0.60;
        }
        try {
            return new BigDecimal(param.getParamValue()).doubleValue();
        } catch (NumberFormatException e) {
            return 0.60;
        }
    }

    public enum Status {
        /** 分类确定，可继续执行 */
        ROUTED,
        /** 置信度不足，需用户选方向 */
        CLARIFY,
        /** 不属于三类，拒绝并给示例 */
        UNSUPPORTED,
        /** 写操作，按只读边界拒绝 */
        READONLY_REFUSED
    }

    @Data
    public static class Decision {
        private Integer route;
        private String routeName;
        private BigDecimal confidence;
        private Status status;
        private String message;
        private List<ChatVO.Option> options = new ArrayList<>();
        /** 本次分类是否由大模型补判（规则已判定的情况恒为 false） */
        private boolean modelAssisted;
    }
}
