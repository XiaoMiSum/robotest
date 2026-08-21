package io.github.xiaomisum.robotest.framework.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface TestCaseDocumentConvertMapper {

    TestCaseDocumentConvertMapper INSTANCE = Mappers.getMapper(TestCaseDocumentConvertMapper.class);

    TestCaseDocumentRespDTO toRespDTO(TestCaseDocument document);
}