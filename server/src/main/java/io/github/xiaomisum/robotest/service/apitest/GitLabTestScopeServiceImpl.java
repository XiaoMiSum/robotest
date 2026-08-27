package io.github.xiaomisum.robotest.service.apitest;

import io.github.xiaomisum.robotest.framework.security.ProjectAccessGuard;
import io.github.xiaomisum.robotest.model.dto.request.apitest.GitLabTestScopeSaveReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.apitest.GitLabTestScopeRespDTO;
import io.github.xiaomisum.robotest.model.entity.apitest.GitLabTestScope;
import io.github.xiaomisum.robotest.repository.apitest.GitLabTestScopeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class GitLabTestScopeServiceImpl implements GitLabTestScopeService {

    @Resource
    private GitLabTestScopeMapper testScopeMapper;

    @Resource
    private ProjectAccessGuard projectAccessGuard;

    @Override
    public List<GitLabTestScopeRespDTO> fetchScopeList(UUID projectId, UUID workspaceId, UUID userId,
                                                         UUID repositoryId) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        List<GitLabTestScope> list = testScopeMapper.selectListByRepositoryId(repositoryId);
        return list.stream().map(this::toRespDTO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveScopeList(UUID projectId, UUID workspaceId, UUID userId,
                                  GitLabTestScopeSaveReqDTO reqDTO) {
        projectAccessGuard.requireProjectMember(projectId, workspaceId, userId);
        UUID repositoryId = reqDTO.getRepositoryId();

        // 全量覆盖：删除旧数据 + 插入新数据
        testScopeMapper.deleteByRepository(repositoryId);
        for (GitLabTestScopeSaveReqDTO.GitLabTestScopeItem item : reqDTO.getItems()) {
            GitLabTestScope entity = new GitLabTestScope();
            entity.setId(UUID.randomUUID());
            entity.setRepositoryId(repositoryId);
            entity.setVariableName(item.getVariableName());
            entity.setScopeType(item.getScopeType());
            entity.setDescription(item.getDescription());
            testScopeMapper.insert(entity);
        }
        return true;
    }

    @Override
    public Map<String, String> buildScopeVariables(UUID repositoryId) {
        List<GitLabTestScope> scopes = testScopeMapper.selectListByRepositoryId(repositoryId);
        Map<String, String> variables = new LinkedHashMap<>();
        for (GitLabTestScope scope : scopes) {
            // 变量名作为 key，值由调用方（前端/调度任务）在触发时填入
            variables.put(scope.getVariableName(), "");
        }
        return variables;
    }

    private GitLabTestScopeRespDTO toRespDTO(GitLabTestScope entity) {
        GitLabTestScopeRespDTO dto = new GitLabTestScopeRespDTO();
        dto.setId(entity.getId());
        dto.setRepositoryId(entity.getRepositoryId());
        dto.setVariableName(entity.getVariableName());
        dto.setScopeType(entity.getScopeType());
        dto.setDescription(entity.getDescription());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
