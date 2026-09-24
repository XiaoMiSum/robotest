package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.response.tcase.ProjectModuleTreeRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.tcase.TestCaseDocumentRespDTO;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MapStructConvertersTest {

    @Test
    void allConvertersAreSpringBeans() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("io.github.xiaomisum.robotest.model.convert");
            context.refresh();

            assertNotNull(context.getBean(BugConvertMapper.class));
            assertNotNull(context.getBean(ProjectConvertMapper.class));
            assertNotNull(context.getBean(ProjectDashboardConvertMapper.class));
            assertNotNull(context.getBean(ProjectModuleConvertMapper.class));
            assertNotNull(context.getBean(RoleConvertMapper.class));
            assertNotNull(context.getBean(TestCaseDocumentConvertMapper.class));
            assertNotNull(context.getBean(TestCaseNodeConvertMapper.class));
            assertNotNull(context.getBean(TestPlanConvertMapper.class));
            assertNotNull(context.getBean(TestReviewConvertMapper.class));
            assertNotNull(context.getBean(UserConvertMapper.class));
            assertNotNull(context.getBean(WorkspaceConvertMapper.class));
            assertNotNull(context.getBean(WorkspaceInvitationConvertMapper.class));
            assertNotNull(context.getBean(WorkspaceMemberConvertMapper.class));
        }
    }

    @Test
    void treeConvertersPreserveEntityFields() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("io.github.xiaomisum.robotest.model.convert");
            context.refresh();

            UUID projectId = UUID.randomUUID();
            ProjectModule module = new ProjectModule();
            module.setId(projectId);
            module.setProjectId(projectId);
            module.setName("目录");
            module.setParentId(null);
            module.setSortOrder(2);
            ProjectModuleTreeRespDTO moduleDto = context.getBean(ProjectModuleConvertMapper.class)
                    .toTreeDTO(module);
            assertEquals(projectId, moduleDto.getId());
            assertEquals("目录", moduleDto.getName());
            assertEquals("directory", moduleDto.getType());

            TestCaseDocument document = new TestCaseDocument();
            document.setId(projectId);
            document.setName("文档");
            document.setSortOrder(3);
            TestCaseDocumentRespDTO documentDto = context.getBean(TestCaseDocumentConvertMapper.class)
                    .toRespDTO(document);
            assertEquals(projectId, documentDto.getId());
            assertEquals("文档", documentDto.getName());
        }
    }
}
