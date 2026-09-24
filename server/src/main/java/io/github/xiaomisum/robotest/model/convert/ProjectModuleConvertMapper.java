package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProjectModuleConvertMapper {

    @Mapping(target = "type", constant = "directory")
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "count", ignore = true)
    ProjectModuleTreeRespDTO toTreeDTO(ProjectModule module);
}