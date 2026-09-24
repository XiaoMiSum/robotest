package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TestCaseDocumentConvertMapper {

    TestCaseDocumentRespDTO toRespDTO(TestCaseDocument document);
}