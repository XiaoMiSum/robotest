package io.github.xiaomisum.robotest.service.project;

import io.github.xiaomisum.robotest.model.dto.request.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.request.BugUpdateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.BugLogRespDTO;

import xyz.migoo.framework.common.pojo.PageResult;

import java.util.List;

public interface BugService {

    PageResult<BugListRespDTO> getBugPage(String projectId, String status, String severity,
                                     String priority, String assigneeId, String keyword,
                                     Integer pageNo, Integer pageSize);

    String createBug(String projectId, String userId, BugCreateReqDTO reqDTO);

    void updateBug(String bugId, String userId, BugUpdateReqDTO reqDTO);

    List<BugLogRespDTO> getBugLogs(String bugId);
}
