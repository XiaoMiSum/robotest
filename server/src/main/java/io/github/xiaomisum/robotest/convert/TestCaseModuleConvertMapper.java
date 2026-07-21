package io.github.xiaomisum.robotest.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface TestCaseModuleConvertMapper {

    TestCaseModuleConvertMapper INSTANCE = Mappers.getMapper(TestCaseModuleConvertMapper.class);

    @Mapping(target = "parentId", expression = "java(module.getParentId() != null ? java.util.UUID.fromString(module.getParentId()) : null)")
    @Mapping(target = "children", ignore = true)
    TestCaseModuleTreeRespDTO toTreeDTO(TestCaseModule module);

    default UUID toUUID(String value) {
        return value != null ? UUID.fromString(value) : null;
    }
}
