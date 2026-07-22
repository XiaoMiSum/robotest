package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.ProjectRespDTO;
import io.github.xiaomisum.robotest.model.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface ProjectConvertMapper {

    ProjectConvertMapper INSTANCE = Mappers.getMapper(ProjectConvertMapper.class);

    @Mapping(target = "isDefault", expression = "java(project.getId().toString().equals(defaultProjectId))")
    @Mapping(target = "createdBy", ignore = true)
    ProjectRespDTO toRespDTO(Project project, String defaultProjectId);

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
