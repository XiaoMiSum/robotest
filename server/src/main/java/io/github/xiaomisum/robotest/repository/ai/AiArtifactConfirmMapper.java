package io.github.xiaomisum.robotest.repository.ai;

import io.github.xiaomisum.robotest.model.entity.ai.AiArtifactConfirm;
import xyz.migoo.framework.mybatis.core.BaseMapperX;
import xyz.migoo.framework.mybatis.core.LambdaQueryWrapperX;

import java.util.UUID;

public interface AiArtifactConfirmMapper extends BaseMapperX<AiArtifactConfirm> {

    /** 幂等判定（uk_ai_artifact_confirm 兜底前的查询校验，详设 3.6.5） */
    default AiArtifactConfirm selectByTaskAndKey(UUID taskId, String artifactKey) {
        return selectOne(new LambdaQueryWrapperX<AiArtifactConfirm>()
                .eq(AiArtifactConfirm::getTaskId, taskId)
                .eq(AiArtifactConfirm::getArtifactKey, artifactKey)
                .last("LIMIT 1"));
    }

    /** 详情页产物清单的确认状态回填 */
    default java.util.List<AiArtifactConfirm> selectByTask(UUID taskId) {
        return selectList(new LambdaQueryWrapperX<AiArtifactConfirm>()
                .eq(AiArtifactConfirm::getTaskId, taskId));
    }
}
