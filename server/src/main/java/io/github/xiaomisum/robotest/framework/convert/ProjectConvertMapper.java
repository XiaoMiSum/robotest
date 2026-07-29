package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.request.workspace.ProjectCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.workspace.ProjectRespDTO;
import io.github.xiaomisum.robotest.model.entity.workspace.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface ProjectConvertMapper {

    ProjectConvertMapper INSTANCE = Mappers.getMapper(ProjectConvertMapper.class);

    @Mapping(target = "isDefault", expression = "java(project.getId().toString().equals(defaultProjectId))")
    @Mapping(target = "createdBy", ignore = true)
    ProjectRespDTO toRespDTO(Project project, String defaultProjectId);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workspaceId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Project toEntity(ProjectCreateReqDTO dto);

    default ProjectRespDTO.CreatorInfo toCreatorInfo(UUID id, String name) {
        if (id == null || name == null) {
            return null;
        }
        ProjectRespDTO.CreatorInfo info = new ProjectRespDTO.CreatorInfo();
        info.setId(id);
        info.setName(name);
        return info;
    }
}
