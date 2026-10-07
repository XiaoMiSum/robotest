package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantEntityRefReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationStatRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantMessageRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 助手会话实现：归属即权限（详设 3.1），更新只携带本次变更字段（C11），删除逻辑删除会话与全部消息（详设 3.4）。
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    /** 详设 3.3：title 缺省「新会话」，首问回填前的占位 */
    private static final String DEFAULT_TITLE = "新会话";

    @Resource
    private AiAssistantConversationMapper conversationMapper;

    @Resource
    private AiAssistantMessageMapper messageMapper;

    @Resource
    private AssistantRefChecker refChecker;

    @Override
    public PageResult<AiAssistantConversationRespDTO> pageConversations(AiAssistantConversationPageReqDTO pageReq,
            UUID userId) {
        PageResult<AiAssistantConversation> page = conversationMapper.findPage(pageReq, userId, pageReq.getKeyword());
        List<AiAssistantConversation> conversations = page.getList();
        if (conversations == null || conversations.isEmpty()) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<UUID> ids = conversations.stream().map(AiAssistantConversation::getId).toList();
        Map<UUID, AiAssistantConversationStatRowDTO> stats = messageMapper.statByConversationIds(ids).stream()
                .collect(Collectors.toMap(AiAssistantConversationStatRowDTO::getConversationId, Function.identity(),
                        (first, second) -> first));
        List<AiAssistantConversationRespDTO> list = conversations.stream()
                .map(item -> toConversationResp(item, stats.get(item.getId())))
                .toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public AiAssistantConversationRespDTO createConversation(AiAssistantConversationCreateReqDTO reqDTO, UUID userId,
            UUID activeWorkspaceId) {
        AiAssistantEntityRefReqDTO context = reqDTO.getContext();
        if (context != null && context.getEntityId() != null) {
            refChecker.requireUserVisible(context.getEntityType(), context.getEntityId(), userId);
        }
        String title = reqDTO.getTitle();
        AiAssistantConversation entity = new AiAssistantConversation();
        entity.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim());
        entity.setUserId(userId);
        entity.setStatus(Constants.Status.ACTIVE);
        entity.setContextSnapshot(snapshotOf(activeWorkspaceId, context));
        conversationMapper.insert(entity);
        return toConversationResp(entity, null);
    }

    @Override
    public AiAssistantConversationRespDTO updateConversation(UUID conversationId,
            AiAssistantConversationUpdateReqDTO reqDTO, UUID userId) {
        AiAssistantConversation owned = getOwnedConversation(conversationId, userId);
        boolean rename = reqDTO.getTitle() != null;
        boolean changeStatus = reqDTO.getStatus() != null;
        if (!rename && !changeStatus) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (rename && reqDTO.getTitle().isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        if (changeStatus && !Constants.Status.ARCHIVED.equals(reqDTO.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        AiAssistantConversation update = new AiAssistantConversation();
        update.setId(owned.getId());
        if (rename) {
            update.setTitle(reqDTO.getTitle().trim());
        }
        if (changeStatus) {
            update.setStatus(reqDTO.getStatus());
        }
        conversationMapper.updateById(update);
        AiAssistantConversation latest = conversationMapper.selectById(owned.getId());
        return toConversationResp(latest == null ? owned : latest, statOf(owned.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConversation(UUID conversationId, UUID userId) {
        AiAssistantConversation owned = getOwnedConversation(conversationId, userId);
        messageMapper.delete(new LambdaQueryWrapperX<AiAssistantMessage>()
                .eq(AiAssistantMessage::getConversationId, owned.getId()));
        conversationMapper.deleteById(owned.getId());
        return true;
    }

    @Override
    public PageResult<AiAssistantMessageRespDTO> pageMessages(UUID conversationId,
            AiAssistantMessagePageReqDTO pageReq, UUID userId) {
        getOwnedConversation(conversationId, userId);
        PageResult<AiAssistantMessage> page = messageMapper.findPage(pageReq, conversationId);
        List<AiAssistantMessageRespDTO> list = page.getList().stream().map(this::toMessageResp).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public AiAssistantConversation getOwnedConversation(UUID conversationId, UUID userId) {
        AiAssistantConversation conversation = conversationMapper.selectById(conversationId);
        // 归属即权限（详设 3.1）：不存在与非本人同码，不经由错误码泄露会话存在性
        if (conversation == null || !userId.equals(conversation.getUserId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    /** 创建快照：工作空间标识由服务端从请求头写入（C4），快照不参与权限判定 */
    private Map<String, Object> snapshotOf(UUID workspaceId, AiAssistantEntityRefReqDTO context) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("workspaceId", workspaceId);
        snapshot.put("context", context);
        // 与 AiEmbeddingAdminService 同口径：jsonb 内时间以 ISO-8601 字符串存储，规避类型处理器序列化差异
        snapshot.put("capturedAt", LocalDateTime.now().toString());
        return snapshot;
    }

    private AiAssistantConversationStatRowDTO statOf(UUID conversationId) {
        return messageMapper.statByConversationIds(List.of(conversationId)).stream().findFirst().orElse(null);
    }

    private AiAssistantConversationRespDTO toConversationResp(AiAssistantConversation entity,
            AiAssistantConversationStatRowDTO stat) {
        AiAssistantConversationRespDTO resp = new AiAssistantConversationRespDTO();
        resp.setId(entity.getId());
        resp.setTitle(entity.getTitle());
        resp.setStatus(entity.getStatus());
        resp.setContextSnapshot(entity.getContextSnapshot());
        resp.setMessageCount(stat == null || stat.getMessageCount() == null ? 0 : stat.getMessageCount());
        resp.setLastMessageAt(stat == null ? null : stat.getLastMessageAt());
        return resp;
    }

    private AiAssistantMessageRespDTO toMessageResp(AiAssistantMessage entity) {
        AiAssistantMessageRespDTO resp = new AiAssistantMessageRespDTO();
        resp.setId(entity.getId());
        resp.setConversationId(entity.getConversationId());
        resp.setRole(entity.getRole());
        resp.setContent(entity.getContent());
        resp.setAttachments(entity.getAttachments());
        resp.setIntent(entity.getIntent());
        resp.setCitations(entity.getCitations());
        resp.setExecution(entity.getExecution());
        resp.setStatus(entity.getStatus());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
