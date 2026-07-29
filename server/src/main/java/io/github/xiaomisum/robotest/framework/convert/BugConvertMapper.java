package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.request.bug.BugCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugAttachmentRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugDetailRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugListRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.bug.BugLogRespDTO;
import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.model.entity.bug.BugLog;
import io.github.xiaomisum.robotest.model.entity.admin.SysUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface BugConvertMapper {

    BugConvertMapper INSTANCE = Mappers.getMapper(BugConvertMapper.class);

    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    BugListRespDTO toListRespDTO(Bug bug);

    @Mapping(target = "moduleName", ignore = true)
    @Mapping(target = "resolvedBy", ignore = true)
    @Mapping(target = "closedBy", ignore = true)
    @Mapping(target = "reporter", ignore = true)
    @Mapping(target = "assignee", ignore = true)
    @Mapping(target = "recentLogs", ignore = true)
    BugDetailRespDTO toDetailRespDTO(Bug bug);

    @Mapping(target = "operatorName", ignore = true)
    BugLogRespDTO toLogRespDTO(BugLog log);

    @Mapping(target = "uploaderName", ignore = true)
    BugAttachmentRespDTO toAttachmentRespDTO(BugAttachment attachment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "confirmed", ignore = true)
    @Mapping(target = "reopenCount", ignore = true)
    @Mapping(target = "reporterId", ignore = true)
    @Mapping(target = "resolution", ignore = true)
    @Mapping(target = "duplicateOfBugId", ignore = true)
    @Mapping(target = "resolvedAt", ignore = true)
    @Mapping(target = "closedBy", ignore = true)
    @Mapping(target = "resolvedBy", ignore = true)
    @Mapping(target = "lastReopenedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Bug toEntity(BugCreateReqDTO dto);

    default BugListRespDTO.UserInfo toUserInfo(SysUser user) {
        if (user == null) {
            return null;
        }
        BugListRespDTO.UserInfo info = new BugListRespDTO.UserInfo();
        info.setId(user.getId());
        info.setName(user.getUsername());
        return info;
    }

    default BugDetailRespDTO.UserInfo toDetailUserInfo(SysUser user) {
        if (user == null) {
            return null;
        }
        BugDetailRespDTO.UserInfo info = new BugDetailRespDTO.UserInfo();
        info.setId(user.getId());
        info.setName(user.getUsername());
        return info;
    }

    default BugDetailRespDTO.UserInfo toDetailUserInfoById(UUID userId, java.util.Map<UUID, SysUser> userMap) {
        if (userId == null || userMap == null) {
            return null;
        }
        SysUser user = userMap.get(userId);
        return toDetailUserInfo(user);
    }
}
