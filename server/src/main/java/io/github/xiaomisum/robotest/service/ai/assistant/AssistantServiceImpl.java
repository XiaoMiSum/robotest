package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationPageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantConversationUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantEntityRefReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantImageRefReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessagePageReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.ai.AiAssistantMessageSendReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantConversationStatRowDTO;
import io.github.xiaomisum.robotest.model.dto.response.ai.AiAssistantMessageRespDTO;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantConversation;
import io.github.xiaomisum.robotest.model.entity.ai.AiAssistantMessage;
import io.github.xiaomisum.robotest.model.entity.file.FileResource;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantConversationMapper;
import io.github.xiaomisum.robotest.repository.ai.AiAssistantMessageMapper;
import io.github.xiaomisum.robotest.repository.file.FileResourceMapper;
import io.github.xiaomisum.robotest.service.ai.task.AiTaskService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 助手会话实现：归属即权限（详设 3.1），列表按归属空间过滤、创建写入归属空间（详设 3.2/3.3），
 * 更新只携带本次变更字段（C11），删除逻辑删除会话与全部消息（详设 3.4）。
 */
@Service
public class AssistantServiceImpl implements AssistantService {

    /** 详设 3.3：title 缺省「新会话」，首问回填前的占位 */
    private static final String DEFAULT_TITLE = "新会话";

    /** 详设 3.5：正文非空且 ≤4000 字符，首问回填取前 30 字 */
    private static final int MAX_CONTENT_CHARS = 4000;
    private static final int TITLE_BACKFILL_CHARS = 30;

    /** 详设 3.5：附图单条 ≤3 张、单张 ≤10MB，类型限图像白名单（与正文贴图同口径） */
    private static final int MAX_IMAGE_COUNT = 3;
    private static final long MAX_IMAGE_BYTES = 10 * 1024 * 1024L;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 事件流兜底超时（详设 3.5）：到期回收通道并置 interrupted，内容不丢失、可经消息历史补齐 */
    private static final long STREAM_TIMEOUT_MILLIS = 10 * 60 * 1000L;

    private static final String TASK_TYPE_ASSISTANT_PARSE = "assistant_parse";

    @Resource
    private AiAssistantConversationMapper conversationMapper;

    @Resource
    private AiAssistantMessageMapper messageMapper;

    @Resource
    private FileResourceMapper fileResourceMapper;

    @Resource
    private AssistantRefChecker refChecker;

    @Resource
    private AssistantStreamBridge streamBridge;

    @Resource
    private AiTaskService aiTaskService;

    @Override
    public PageResult<AiAssistantConversationRespDTO> pageConversations(AiAssistantConversationPageReqDTO pageReq,
            UUID userId, UUID activeWorkspaceId) {
        // /api/ai 不强制解析上下文头（详设 3.2 列表必带 X-Active-Workspace），服务端自查缺失
        if (activeWorkspaceId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        PageResult<AiAssistantConversation> page = conversationMapper.findPage(pageReq, userId, activeWorkspaceId,
                pageReq.getKeyword());
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
        // 创建必带 X-Active-Workspace（详设 3.3），归属空间创建后不变
        if (activeWorkspaceId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        AiAssistantEntityRefReqDTO context = reqDTO.getContext();
        if (context != null && context.getEntityId() != null) {
            refChecker.requireUserVisible(context.getEntityType(), context.getEntityId(), userId);
        }
        String title = reqDTO.getTitle();
        AiAssistantConversation entity = new AiAssistantConversation();
        entity.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim());
        entity.setUserId(userId);
        entity.setWorkspaceId(activeWorkspaceId);
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
    @Transactional(rollbackFor = Exception.class)
    public SseEmitter send(UUID conversationId, AiAssistantMessageSendReqDTO reqDTO, UUID userId,
            UUID activeWorkspaceId) {
        AiAssistantConversation owned = getOwnedConversation(conversationId, userId);
        if (!Constants.Status.ACTIVE.equals(owned.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CONVERSATION_ARCHIVED);
        }
        String content = reqDTO.getContent() == null ? null : reqDTO.getContent().trim();
        if (content == null || content.isEmpty() || content.length() > MAX_CONTENT_CHARS) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CONTENT_INVALID);
        }
        // /api/ai 不强制解析上下文头（详设 3.1 发送必带 X-Active-Workspace），服务端自查缺失
        if (activeWorkspaceId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING);
        }
        refChecker.requireWorkspaceMember(activeWorkspaceId, userId);
        // 会话绑定归属空间（详设 4.4）：归属不一致按 1000018263 拒绝，防止误解任务落点
        if (!activeWorkspaceId.equals(owned.getWorkspaceId())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_CONVERSATION_WORKSPACE_MISMATCH);
        }
        List<AiAssistantEntityRefReqDTO> attachments = reqDTO.getAttachments();
        if (attachments != null) {
            for (AiAssistantEntityRefReqDTO ref : attachments) {
                if (ref == null || ref.getEntityType() == null || ref.getEntityId() == null) {
                    throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_ATTACHMENT_INVALID);
                }
                refChecker.requireInActiveWorkspace(ref.getEntityType(), ref.getEntityId(), activeWorkspaceId);
            }
        }
        List<Map<String, Object>> images = validateImages(reqDTO.getImages());

        // 首问回填须在落用户消息前判定（落盘后消息数不再为 0）；用户改过标题不覆盖
        boolean firstAsk = DEFAULT_TITLE.equals(owned.getTitle()) && messageCountOf(owned.getId()) == 0;

        AiAssistantMessage userMessage = new AiAssistantMessage();
        userMessage.setConversationId(owned.getId());
        userMessage.setRole(Constants.AiAssistantMessageRole.USER);
        userMessage.setContent(content);
        userMessage.setAttachments(toAttachmentMaps(attachments));
        userMessage.setImages(images);
        userMessage.setStatus(Constants.AiAssistantMessageStatus.DONE);
        messageMapper.insert(userMessage);

        if (firstAsk) {
            AiAssistantConversation rename = new AiAssistantConversation();
            rename.setId(owned.getId());
            rename.setTitle(content.length() > TITLE_BACKFILL_CHARS
                    ? content.substring(0, TITLE_BACKFILL_CHARS) : content);
            conversationMapper.updateById(rename);
        }

        AiAssistantMessage assistantMessage = new AiAssistantMessage();
        assistantMessage.setConversationId(owned.getId());
        assistantMessage.setRole(Constants.AiAssistantMessageRole.ASSISTANT);
        assistantMessage.setStatus(Constants.AiAssistantMessageStatus.STREAMING);
        messageMapper.insert(assistantMessage);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        streamBridge.attach(assistantMessage.getId(), emitter);
        try {
            // projectId 置 null：助手解析为不限项目的个人任务，执行作用域取活跃工作空间（总册 2.6）
            aiTaskService.submitInternal(TASK_TYPE_ASSISTANT_PARSE,
                    Map.of("messageId", assistantMessage.getId().toString()), userId, null, activeWorkspaceId);
        } catch (RuntimeException e) {
            // 提交失败随事务回滚两条消息，通道注册表不残留
            streamBridge.detach(assistantMessage.getId());
            throw e;
        }
        return emitter;
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

    private long messageCountOf(UUID conversationId) {
        AiAssistantConversationStatRowDTO stat = statOf(conversationId);
        return stat == null || stat.getMessageCount() == null ? 0 : stat.getMessageCount();
    }

    /** 附件落盘形态与消息历史回读同构（jsonb 内 entityType / entityId / entityTitle） */
    private static List<Map<String, Object>> toAttachmentMaps(List<AiAssistantEntityRefReqDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (AiAssistantEntityRefReqDTO ref : attachments) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entityType", ref.getEntityType());
            map.put("entityId", ref.getEntityId());
            map.put("entityTitle", ref.getEntityTitle());
            maps.add(map);
        }
        return maps;
    }

    /** 附图校验与落盘形态（详设 3.5）：仅存 {fileId, name} 引用；任一违规按 1000018264 拒绝 */
    private List<Map<String, Object>> validateImages(List<AiAssistantImageRefReqDTO> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID);
        }
        List<Map<String, Object>> maps = new ArrayList<>();
        for (AiAssistantImageRefReqDTO image : images) {
            if (image == null || image.getFileId() == null) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID);
            }
            FileResource file = fileResourceMapper.selectById(image.getFileId());
            if (file == null || !IMAGE_EXTENSIONS.contains(extensionOf(file.getFileName()))
                    || file.getFileSize() == null || file.getFileSize() > MAX_IMAGE_BYTES) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.ASSISTANT_IMAGE_INVALID);
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("fileId", image.getFileId());
            map.put("name", file.getFileName());
            maps.add(map);
        }
        return maps;
    }

    private static String extensionOf(String fileName) {
        String extension = fileName == null ? null : StringUtils.getFilenameExtension(fileName);
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
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
        resp.setWorkspaceId(entity.getWorkspaceId());
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
        resp.setImages(entity.getImages());
        resp.setIntent(entity.getIntent());
        resp.setCitations(entity.getCitations());
        resp.setExecution(entity.getExecution());
        resp.setStatus(entity.getStatus());
        resp.setCreatedAt(entity.getCreatedAt());
        return resp;
    }
}
