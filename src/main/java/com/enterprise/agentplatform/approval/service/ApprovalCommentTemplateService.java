package com.enterprise.agentplatform.approval.service;

import com.enterprise.agentplatform.approval.dto.ApprovalCommentTemplateResponse;
import com.enterprise.agentplatform.common.api.ErrorCode;
import com.enterprise.agentplatform.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ApprovalCommentTemplateService {

    private static final List<ApprovalCommentTemplateResponse> TEMPLATES = List.of(
            new ApprovalCommentTemplateResponse(
                    "APPROVE_EVIDENCE_SUFFICIENT",
                    "approve",
                    "证据充分，批准开单",
                    "已核对知识库引用和工单内容，证据充分，同意创建正式工单。"
            ),
            new ApprovalCommentTemplateResponse(
                    "APPROVE_URGENT_CONTINUITY",
                    "approve",
                    "业务连续性优先",
                    "故障影响业务连续性，先批准开单推进处理，后续补充复盘记录。"
            ),
            new ApprovalCommentTemplateResponse(
                    "REJECT_EVIDENCE_INSUFFICIENT",
                    "reject",
                    "证据不足，退回补充",
                    "当前引用证据不足以支撑高优先级开单，请补充排障依据后重新提交。"
            ),
            new ApprovalCommentTemplateResponse(
                    "REJECT_DUPLICATE_TICKET",
                    "reject",
                    "疑似重复工单",
                    "疑似已有相同问题工单，请先合并到已有工单或说明差异后再提交。"
            )
    );

    public List<ApprovalCommentTemplateResponse> listTemplates() {
        return TEMPLATES;
    }

    public String resolveComment(String action, String templateCode, String comment) {
        String normalizedComment = normalize(comment);
        if (templateCode == null || templateCode.isBlank()) {
            return normalizedComment;
        }
        ApprovalCommentTemplateResponse template = requireTemplate(action, templateCode);
        return normalizedComment.isBlank() ? template.comment() : normalizedComment;
    }

    public String normalizeTemplateCode(String action, String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return null;
        }
        return requireTemplate(action, templateCode).code();
    }

    private ApprovalCommentTemplateResponse requireTemplate(String action, String templateCode) {
        String normalizedAction = action.toLowerCase(Locale.ROOT);
        String normalizedCode = templateCode.strip().toUpperCase(Locale.ROOT);
        return TEMPLATES.stream()
                .filter(template -> template.action().equals(normalizedAction))
                .filter(template -> template.code().equals(normalizedCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "审批备注模板不存在或不适用于当前操作"));
    }

    private String normalize(String comment) {
        return comment == null ? "" : comment.strip();
    }
}
