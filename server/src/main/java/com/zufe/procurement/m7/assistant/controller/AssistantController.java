package com.zufe.procurement.m7.assistant.controller;

import com.zufe.procurement.common.Result;
import com.zufe.procurement.common.ResultCode;
import com.zufe.procurement.m7.assistant.dto.ChatRequest;
import com.zufe.procurement.m7.assistant.service.AiChatService;
import com.zufe.procurement.m7.assistant.service.AiProfileService;
import com.zufe.procurement.m7.assistant.service.AiSessionService;
import com.zufe.procurement.m7.assistant.vo.ChatMessageVO;
import com.zufe.procurement.m7.assistant.vo.ChatVO;
import com.zufe.procurement.m7.assistant.vo.ProfileVO;
import com.zufe.procurement.m7.assistant.vo.SessionVO;
import com.zufe.procurement.security.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 智能助手统一入口（M7-1、M7-2、M7-3）。
 *
 * 全部接口只读：chat 只调用查询服务，feedback 只写消息记录，不触碰任何业务表。
 * 结果码放在外层 code 上，payload 照常返回，前端据 code 决定提示样式、
 * 据 payload 渲染澄清选项与引用，不必依赖错误文案做分支。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AiChatService chatService;
    private final AiProfileService profileService;
    private final AiSessionService sessionService;

    public AssistantController(AiChatService chatService, AiProfileService profileService,
                               AiSessionService sessionService) {
        this.chatService = chatService;
        this.profileService = profileService;
        this.sessionService = sessionService;
    }

    /** M7-1-1 提问（含 M7-3-2 参数修正重跑） */
    @PostMapping("/chat")
    @RequirePermission("m7:chat:query")
    public Result<ChatVO> chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        AiChatService.ChatOutcome outcome = chatService.chat(request, httpRequest.getRemoteAddr());
        return new Result<>(outcome.getCode(), messageOf(outcome.getCode()), outcome.getVo());
    }

    /** M7-1-4 入口提示：本角色能问什么、数据范围到哪 */
    @GetMapping("/profile")
    @RequirePermission("m7:chat:query")
    public Result<ProfileVO> profile() {
        return Result.ok(profileService.profile());
    }

    /** M7-1-2 对话历史 */
    @GetMapping("/sessions")
    @RequirePermission("m7:session:list")
    public Result<List<SessionVO>> sessions() {
        return Result.ok(sessionService.listSessions(com.zufe.procurement.auth.AuthContext
                .getOrThrow().getUserId()));
    }

    /** M7-1-2 单个会话的消息列表 */
    @GetMapping("/sessions/{id}/messages")
    @RequirePermission("m7:session:list")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return Result.ok(sessionService.messages(id,
                com.zufe.procurement.auth.AuthContext.getOrThrow().getUserId()));
    }

    /**
     * M7-3-3 解析错误反馈：记录为测试集候选。
     * 不新增表，复用消息表存一条反馈记录（role=1、intent_code=parse_feedback），
     * 保持 H 域 6 张表的结构不变。
     */
    @PostMapping("/feedback")
    @RequirePermission("m7:chat:query")
    public Result<Void> feedback(@RequestBody Map<String, Object> body) {
        Object reason = body.get("reason");
        String content = "解析错误反馈：" + (reason == null ? "未填写原因" : reason);
        sessionService.appendFeedback(currentSessionId(body), content, body);
        return Result.ok();
    }

    private Long currentSessionId(Map<String, Object> body) {
        Object value = body.get("sessionId");
        if (value == null) {
            return 0L;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private String messageOf(String code) {
        for (ResultCode value : ResultCode.values()) {
            if (value.getCode().equals(code)) {
                return value.getMessage();
            }
        }
        return "处理完成";
    }
}
