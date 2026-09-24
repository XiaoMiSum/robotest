package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseNodeTreeRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TestCaseNodeConvertMapper {

    @Mapping(target = "children", ignore = true)
    TestCaseNodeTreeRespDTO toTreeDTO(TestCaseNode node);
}
