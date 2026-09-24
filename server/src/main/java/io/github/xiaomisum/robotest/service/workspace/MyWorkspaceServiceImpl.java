package io.github.xiaomisum.robotest.service.workspace;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.framework.common.ErrorCodeConstants;
import io.github.xiaomisum.robotest.model.dto.request.workspace.MyWorkspaceQueryReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyPageRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.WorkspaceMyScopeCountsDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import io.github.xiaomisum.robotest.model.entity.workspace.Workspace;
import io.github.xiaomisum.robotest.model.entity.workspace.WorkspaceUser;
import io.github.xiaomisum.robotest.repository.admin.SysUserMapper;
import io.github.xiaomisum.robotest.repository.workspace.MyWorkspaceQueryMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceMapper;
import io.github.xiaomisum.robotest.repository.workspace.WorkspaceUserMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import xyz.migoo.framework.common.exception.ServiceExceptionUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MyWorkspaceServiceImpl implements MyWorkspaceService {

    private static final Set<String> VALID_SCOPES = Set.of("all", "managed", "archived");
    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 100;

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private WorkspaceMapper workspaceMapper;
    @Resource
    private WorkspaceUserMapper workspaceUserMapper;
    @Resource
    private MyWorkspaceQueryMapper myWorkspaceQueryMapper;

    @Override
    public WorkspaceMyPageRespDTO getMyWorkspaces(UUID userId, MyWorkspaceQueryReqDTO query) {
        MyWorkspaceQueryReqDTO request = query == null ? new MyWorkspaceQueryReqDTO() : query;
        String keyword = normalizeKeyword(request.getKeyword());
        String scope = normalizeScope(request.getScope());
        int pageNo = normalizePageNo(request.getPageNo());
        int pageSize = normalizePageSize(request.getPageSize());
        long offset = (long) (pageNo - 1) * pageSize;

        List<WorkspaceMyRespDTO> list = myWorkspaceQueryMapper.selectPage(
                userId, keyword, scope, Constants.WorkspaceRole.ADMIN_ID, offset, pageSize);
        long total = myWorkspaceQueryMapper.count(userId, keyword, scope, Constants.WorkspaceRole.ADMIN_ID);
        WorkspaceMyScopeCountsDTO counts = myWorkspaceQueryMapper.countScopes(
                userId, keyword, Constants.WorkspaceRole.ADMIN_ID);

        WorkspaceMyPageRespDTO result = new WorkspaceMyPageRespDTO();
        result.setList(list == null ? List.of() : list);
        result.setTotal(total);
        result.setCounts(normalizeCounts(counts));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setActiveWorkspace(UUID userId, UUID workspaceId) {
        if (userId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }
        if (workspaceId == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.CONTEXT_HEADER_MISSING, "X-Active-Workspace");
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.USER_NOT_FOUND);
        }

        Workspace workspace = workspaceMapper.selectById(workspaceId);
        if (workspace == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_NOT_FOUND);
        }

        WorkspaceUser workspaceUser = workspaceUserMapper.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (workspaceUser == null) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.NO_PERMISSION);
        }
        if (Constants.Status.DISSOLVED.equals(workspace.getStatus())) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.WORKSPACE_DISSOLVED);
        }

        LocalDateTime accessedAt = LocalDateTime.now();
        SysUser userUpdate = new SysUser();
        userUpdate.setId(userId);
        userUpdate.setLastActiveWorkspaceId(workspaceId.toString());
        userMapper.updateById(userUpdate);

        WorkspaceUser workspaceUserUpdate = new WorkspaceUser();
        workspaceUserUpdate.setId(workspaceUser.getId());
        workspaceUserUpdate.setLastAccessedAt(accessedAt);
        workspaceUserMapper.updateById(workspaceUserUpdate);
    }

    private String normalizeKeyword(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return "all";
        }
        String normalized = scope.trim();
        if (!VALID_SCOPES.contains(normalized)) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return normalized;
    }

    private int normalizePageNo(Integer pageNo) {
        if (pageNo == null) {
            return DEFAULT_PAGE_NO;
        }
        if (pageNo < 1) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return pageNo;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.VALIDATION_FAILED);
        }
        return pageSize;
    }

    private WorkspaceMyScopeCountsDTO normalizeCounts(WorkspaceMyScopeCountsDTO counts) {
        if (counts == null) {
            counts = new WorkspaceMyScopeCountsDTO();
        }
        counts.setAll(counts.getAll() == null ? 0L : counts.getAll());
        counts.setManaged(counts.getManaged() == null ? 0L : counts.getManaged());
        counts.setArchived(counts.getArchived() == null ? 0L : counts.getArchived());
        return counts;
    }
}
