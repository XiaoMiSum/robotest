package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.dto.response.bug.BugDraftSuggestRespDTO;

import java.util.List;
import java.util.UUID;

/**
 * 缺陷相似检索（详设 3.4）：供新建缺陷建议内部复用，不对外暴露路由、不落中间表。
 */
public interface BugAnalysisService {

    /**
     * 检索当前项目内疑似重复缺陷；向量能力未就绪或检索失败时返回空列表，不阻断建议。
     */
    List<BugDraftSuggestRespDTO.DuplicateItem> findDuplicates(String title, String steps,
            UUID projectId, UUID userId);
}
