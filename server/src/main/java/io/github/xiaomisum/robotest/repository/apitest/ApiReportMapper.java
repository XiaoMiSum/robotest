package io.github.xiaomisum.robotest.repository.apitest;

import io.github.xiaomisum.robotest.model.entity.apitest.ApiReport;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xyz.migoo.framework.common.pojo.PageParam;
import xyz.migoo.framework.common.pojo.PageResult;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface ApiReportMapper extends BaseMapperX<ApiReport> {

    default ApiReport selectByExecutionRecordId(UUID executionRecordId) {
        return selectOne(new LambdaQueryWrapperX<ApiReport>()
                .eq(ApiReport::getExecutionRecordId, executionRecordId));
    }

    /** 报告列表：状态/场景/执行方式/关键字/时间范围叠加筛选，时间倒序（基础设施详细设计 3.4.1 + 测试报告详细设计 3.1） */
    default PageResult<ApiReport> selectPageByProject(UUID projectId, PageParam pageParam, String status,
            UUID sceneId, String executionMode, String keyword, LocalDateTime startDate, LocalDateTime endDate) {
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return selectPage(pageParam, new LambdaQueryWrapperX<ApiReport>()
                .eq(ApiReport::getProjectId, projectId)
                .eqIfPresent(ApiReport::getStatus, status)
                .eqIfPresent(ApiReport::getSceneId, sceneId)
                .eqIfPresent(ApiReport::getExecutionMode, executionMode)
                .likeIfPresent(ApiReport::getSceneName, kw)
                .geIfPresent(ApiReport::getCreatedAt, startDate)
                .leIfPresent(ApiReport::getCreatedAt, endDate)
                .orderByDesc(ApiReport::getCreatedAt));
    }

    /** 分享访问按 token 唯一定位（uk_report_share_token 部分唯一索引支撑） */
    default ApiReport selectByIdAndToken(UUID id, String token) {
        return selectOne(new LambdaQueryWrapperX<ApiReport>()
                .eq(ApiReport::getId, id)
                .eq(ApiReport::getShareToken, token));
    }

    /** 保留期清理：一步物理删除（定时任务详细设计 4.4），绕过逻辑删除 */
    @Delete("DELETE FROM api_report WHERE created_at < #{cutoff}")
    int deletePhysicallyOlderThan(@Param("cutoff") LocalDateTime cutoff);

}
