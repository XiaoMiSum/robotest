package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.TestCaseNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TestCaseNodeConvertMapper {

    TestCaseNodeConvertMapper INSTANCE = Mappers.getMapper(TestCaseNodeConvertMapper.class);

    @Mapping(target = "children", ignore = true)
    TestCaseNodeTreeRespDTO toTreeDTO(TestCaseNode node);
}
