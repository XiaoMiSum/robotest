package io.github.xiaomisum.robotest.service.ai.assistant;

import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import io.github.xiaomisum.robotest.repository.workspace.ProjectMapper;
import jakarta.annotation.Resource;
import xyz.migoo.framework.common.util.JsonUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 只读查询工具基类：提供空间内项目清单与结果 JSON 封装。
 *
 * <p>跨项目聚合按「空间成员可见空间内全部项目」规则（ProjectController.getProjects
 * 仅按 workspaceId 过滤），逐项目走既有分页查询后合并；查询参数全部可空。</p>
 */
public abstract class AbstractQueryTool implements AiTool {

    /** 单项目查询条数上限（聚合时逐项目截断，防止 LLM 上下文超预算） */
    protected static final int PER_PROJECT_LIMIT = 20;

    @Resource
    private ProjectMapper projectMapper;

    protected List<Project> listProjects(UUID workspaceId) {
        return projectMapper.listByWorkspaceId(workspaceId);
    }

    protected String toResultJson(Object payload) {
        return JsonUtils.toJsonString(payload);
    }

    protected String str(Map<String, Object> args, String key) {
        return args.get(key) instanceof String s ? s : null;
    }

    protected Boolean bool(Map<String, Object> args, String key) {
        return args.get(key) instanceof Boolean b ? b : null;
    }
}
