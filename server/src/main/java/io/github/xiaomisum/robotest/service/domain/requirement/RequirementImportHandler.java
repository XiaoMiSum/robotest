package io.github.xiaomisum.robotest.service.domain.requirement;

import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.framework.security.LoginUser;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.config.AiChatMedia;
import io.github.xiaomisum.robotest.service.ai.config.AiChatReply;
import io.github.xiaomisum.robotest.service.ai.task.TaskExecutionContext;
import io.github.xiaomisum.robotest.service.ai.task.TaskHandler;
import io.github.xiaomisum.robotest.service.ai.task.TaskResult;
import io.github.xiaomisum.robotest.service.domain.file.FileContent;
import io.github.xiaomisum.robotest.service.domain.file.FileResourceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import xyz.migoo.framework.common.exception.ServiceException;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 需求导入任务处理器（详设 3.8 / 4.5）：读取来源文件 → 按类型取正文（md 直读 / docx 抽取 /
 * 图片经 media 多模态直传）→ 渲染提示词 → 调模型 → 产物为 documentMeta + requirement_suggestion 列表。
 * 文档级版本识别（detectedVersion + 识别依据引语）随本处理器交付，识别不到为 null（3.8）。
 */
@Component
public class RequirementImportHandler implements TaskHandler {

    private static final String TYPE = "requirement_import";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp", "bmp");
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md");
    /** 与详设 3.8 单文件 20MB 口径对齐：docx 解压后正文字符上限（防解压膨胀） */
    private static final int MAX_TEXT_CHARS = 20 * 1024 * 1024;
    private static final int DETECTED_VERSION_MAX_LENGTH = 50;
    private static final int VERSION_EVIDENCE_MAX_LENGTH = 500;

    private static final String SYSTEM_PROMPT = "你是严谨的需求分析助手，只输出 JSON，不输出解释或代码块标记以外的任何文字。";

    private static final String DEFAULT_PROMPT = """
            将给定需求文档解析为按模块划分的需求建议，并识别被测业务系统版本。
            要求：
            1. 按文档的模块 / 功能边界拆分为若干条建议，每条给出标题、描述（Markdown，含可验证的验收要点）、所属模块与优先级；
            2. 标题互不重复且具体，描述基于原文改写，不得臆造原文没有的业务规则；
            3. 拆分粒度以「单条可独立测试」为准，原文已足够细时可少拆甚至不拆；
            4. 仅当原文明确属于某模块时给出该模块 id，否则 moduleId 为 null；
            5. 优先级只允许 high / medium / low；
            6. documentMeta.detectedVersion 为从文档识别的被测业务系统版本（如 V2.3），versionEvidence 为识别依据的原文引语，
               识别不到时对应值为 null，不得臆造；
            7. 图片附件随消息直接发送（多模态），此时文档内容为空，正文从图片中读取。
            输出结构（JSON 对象，artifacts 数组即产物清单）：
            {"documentMeta":{"detectedVersion":null,"versionEvidence":null},"artifacts":[{"content":{"title":"…","description":"…","moduleId":null,"priority":"medium"}}]}

            文档文件名：{{documentName}}
            文档内容：
            {{documentText}}
            可用模块（id|名称）：
            {{moduleOptions}}
            """;

    @Resource
    private ProjectModuleMapper projectModuleMapper;
    @Resource
    private FileResourceService fileResourceService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String defaultPrompt() {
        return DEFAULT_PROMPT;
    }

    /** 导入入口在需求侧已校验 requirement:create（详设 6.1）；直提任务资源时在此补齐（3.6.2） */
    @Override
    public void checkPermission(LoginUser loginUser) {
        if (loginUser == null || !loginUser.getPermissions().contains("requirement:create")) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_NO_PERMISSION);
        }
    }

    @Override
    public void validateInput(Map<String, Object> input) {
        parseFileId(input);
    }

    @Override
    public TaskResult execute(TaskExecutionContext context) {
        UUID fileId = parseFileId(context.getInput());
        context.report(10, "读取导入文件");
        FileContent file = readFile(fileId);
        String ext = extension(file.fileName());

        String documentText = "";
        AiChatMedia media = null;
        if (IMAGE_EXTENSIONS.contains(ext)) {
            media = new AiChatMedia(imageMimeType(ext), file.content());
        } else if (TEXT_EXTENSIONS.contains(ext)) {
            documentText = new String(file.content(), StandardCharsets.UTF_8);
        } else if ("docx".equals(ext)) {
            documentText = extractDocxText(file.content());
        } else {
            // 直提任务资源绕过 3.8 提交校验的类型，执行期兜底拒绝
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_TYPE_UNSUPPORTED);
        }
        if (media == null && documentText.isBlank()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY);
        }

        context.report(30, "构建提示词");
        List<ProjectModule> modules = projectModuleMapper.listByProjectId(context.getProjectId());
        Map<String, String> variables = new HashMap<>();
        variables.put("documentName", nvl(file.fileName()));
        variables.put("documentText", documentText);
        variables.put("moduleOptions", RequirementSuggestionParser.moduleOptions(modules));
        String userPrompt = context.prompt(DEFAULT_PROMPT, variables);

        context.report(50, "模型解析文档");
        AiChatReply reply = context.chat(SYSTEM_PROMPT, userPrompt, media);

        context.report(85, "解析导入产物");
        Map<String, Object> parsed = RequirementSuggestionParser.parseJsonObject(reply.content());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentMeta", sanitizeDocumentMeta(parsed.get("documentMeta")));
        result.put("artifacts", RequirementSuggestionParser.sanitizeArtifacts(parsed, modules));
        return new TaskResult(result, reply.tokensIn(), reply.tokensOut());
    }

    // ---------- 文件读取与正文抽取 ----------

    private static UUID parseFileId(Map<String, Object> input) {
        Object raw = input == null ? null : input.get("fileId");
        if (raw == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
        try {
            return UUID.fromString(String.valueOf(raw).trim());
        } catch (IllegalArgumentException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.AI_TASK_INPUT_INVALID);
        }
    }

    /** 来源文件已随管理页删除（1000018011）；存储读失败（1000018026）原样上抛走任务失败重试 */
    private FileContent readFile(UUID fileId) {
        try {
            return fileResourceService.readBytes(fileId);
        } catch (ServiceException e) {
            if (e.getCode() != null && e.getCode() == ErrorCodeConstants.FILE_NOT_FOUND.code()) {
                throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_SOURCE_FILE_NOT_FOUND);
            }
            throw e;
        }
    }

    /**
     * docx 正文抽取（JDK ZipInputStream + StAX，零外部依赖）：word/document.xml 按段落收集 w:t 文本，
     * 抽不出正文（非 zip / 无 document.xml / XML 损坏 / 正文空白）按不可解析（1000018008）。
     */
    static String extractDocxText(byte[] bytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return parseDocumentXml(zip);
                }
            }
        } catch (IOException e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY);
        }
        throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY);
    }

    private static String parseDocumentXml(InputStream in) {
        StringBuilder text = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        boolean inText = false;
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            // 防 XXE：禁 DTD 与外部实体（安全规范 6.3，docx 为不可信输入）
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("t".equals(reader.getLocalName())) {
                        inText = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                    paragraph.append(reader.getText());
                    if (text.length() + paragraph.length() > MAX_TEXT_CHARS) {
                        throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_SIZE_EXCEEDED);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("t".equals(reader.getLocalName())) {
                        inText = false;
                    } else if ("p".equals(reader.getLocalName()) && paragraph.length() > 0) {
                        text.append(paragraph).append('\n');
                        paragraph.setLength(0);
                    }
                }
            }
            if (paragraph.length() > 0) {
                text.append(paragraph);
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.REQUIREMENT_IMPORT_EMPTY);
        }
        return text.toString();
    }

    // ---------- 文档级版本识别（3.8 documentMeta） ----------

    /** 识别不到或不可信（超长）一律 null（与采纳侧 system_version 回退同口径），不臆造版本 */
    private static Map<String, Object> sanitizeDocumentMeta(Object raw) {
        String detected = null;
        String evidence = null;
        if (raw instanceof Map<?, ?> meta) {
            String version = RequirementSuggestionParser.asString(meta.get("detectedVersion"));
            if (version != null && !version.isBlank() && version.trim().length() <= DETECTED_VERSION_MAX_LENGTH) {
                detected = version.trim();
            }
            String quote = RequirementSuggestionParser.asString(meta.get("versionEvidence"));
            if (quote != null && !quote.isBlank()) {
                evidence = quote.trim().length() > VERSION_EVIDENCE_MAX_LENGTH
                        ? quote.trim().substring(0, VERSION_EVIDENCE_MAX_LENGTH) : quote.trim();
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("detectedVersion", detected);
        result.put("versionEvidence", evidence);
        return result;
    }

    // ---------- 杂项 ----------

    private static String extension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static String imageMimeType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg";
        };
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
