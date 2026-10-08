package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.dto.request.bug.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.service.ai.task.AdoptContext;
import io.github.xiaomisum.robotest.service.ai.task.AdoptOutcome;
import io.github.xiaomisum.robotest.service.ai.task.ArtifactAdopter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asMap;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.asString;
import static io.github.xiaomisum.robotest.service.ai.task.handler.ModelOutputSupport.stringList;

/**
 * 批量分类采纳承接（缺陷分析详设 3.6）：adopted / adopted_edited 按建议字段（或编辑后内容）
 * 调用既有 updateBug 部分更新缺陷（C11，日志与审计由既有服务内建），逐项事务由总册确认入口负责；
 * 驳回不落库。落库资源权限取既有 bug:edit（详设 4.2）。
 */
@Service
public class BugClassifyAdopter implements ArtifactAdopter {

    private static final String PERMISSION_EDIT = "bug:edit";
    private static final int KEYWORD_JOIN_LIMIT = 5;

    @Resource
    private BugService bugService;

    @Override
    public String type() {
        return BugClassifyHandler.TYPE;
    }

    @Override
    public AdoptOutcome adopt(AdoptContext context) {
        if (Constants.AiArtifactAction.REJECTED.equals(context.action())) {
            return null;
        }
        LoginUser loginUser = context.loginUser();
        if (loginUser == null || !loginUser.getPermissions().contains(PERMISSION_EDIT)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        // 调整后采纳（总册 3.6）以编辑内容为准，直接采纳取产物原内容
        Map<String, Object> content = context.content() != null ? context.content()
                : asMap(asMap(context.artifact()).get("content"));
        UUID bugId = parseBugId(content, context);
        Map<String, Object> suggestions = asMap(content.get("suggestions"));

        BugUpdateReqDTO update = new BugUpdateReqDTO();
        update.setBugType(valueOf(suggestions, "bugType"));
        update.setSeverity(valueOf(suggestions, "severity"));
        update.setPriority(valueOf(suggestions, "priority"));
        update.setModuleId(parseModuleId(suggestions));
        String keywords = keywordsOf(suggestions);
        if (keywords != null) {
            update.setKeywords(keywords);
        }
        update.setAssigneeId(parseAssigneeId(suggestions));
        bugService.updateBug(context.projectId(), bugId, context.operatorId(), update);

        Map<String, Object> adoptedRef = new LinkedHashMap<>();
        adoptedRef.put("bugId", bugId.toString());
        adoptedRef.put("bugType", update.getBugType());
        adoptedRef.put("severity", update.getSeverity());
        adoptedRef.put("priority", update.getPriority());
        adoptedRef.put("moduleId", update.getModuleId() == null ? null : update.getModuleId().toString());
        adoptedRef.put("keywords", update.getKeywords());
        adoptedRef.put("assigneeId", update.getAssigneeId() == null ? null : update.getAssigneeId().toString());
        return new AdoptOutcome(bugId, adoptedRef);
    }

    /** 批量产物 bugId 在 content 顶层；草稿模式无确认动作，不会走到这里 */
    private static UUID parseBugId(Map<String, Object> content, AdoptContext context) {
        try {
            return UUID.fromString(String.valueOf(content.get("bugId")).trim());
        } catch (RuntimeException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.BUG_ANALYSIS_NOT_FOUND);
        }
    }

    private static String valueOf(Map<String, Object> suggestions, String field) {
        return asString(asMap(suggestions.get(field)).get("value"));
    }

    private static UUID parseModuleId(Map<String, Object> suggestions) {
        String raw = valueOf(suggestions, "moduleId");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            // 编辑后内容的模块 id 非法 → 按输入非法回执该项
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    private static String keywordsOf(Map<String, Object> suggestions) {
        List<String> keywords = stringList(asMap(suggestions.get("keywords")).get("value"), KEYWORD_JOIN_LIMIT);
        return keywords.isEmpty() ? null : String.join(",", keywords);
    }

    private static UUID parseAssigneeId(Map<String, Object> suggestions) {
        String raw = valueOf(suggestions, "assigneeId");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return UUID.fromString(raw.trim());
    }
}
