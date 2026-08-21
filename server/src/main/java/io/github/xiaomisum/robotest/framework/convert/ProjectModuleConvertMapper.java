package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProjectModuleConvertMapper {

    ProjectModuleConvertMapper INSTANCE = Mappers.getMapper(ProjectModuleConvertMapper.class);

    @Mapping(target = "type", constant = "directory")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "count", ignore = true)
    ProjectModuleTreeRespDTO toTreeDTO(ProjectModule module);
}