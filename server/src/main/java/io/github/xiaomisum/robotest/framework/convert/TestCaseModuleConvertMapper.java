package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestCaseModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseModule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TestCaseModuleConvertMapper {

    TestCaseModuleConvertMapper INSTANCE = Mappers.getMapper(TestCaseModuleConvertMapper.class);

    @Mapping(target = "children", ignore = true)
    TestCaseModuleTreeRespDTO toTreeDTO(TestCaseModule module);
}
