package io.github.xiaomisum.robotest.service.domain.bug;

import io.github.xiaomisum.robotest.model.entity.bug.Bug;
import io.github.xiaomisum.robotest.model.entity.bug.BugAttachment;
import io.github.xiaomisum.robotest.repository.bug.BugAttachmentMapper;
import io.github.xiaomisum.robotest.repository.bug.BugMapper;
import io.github.xiaomisum.robotest.repository.tcase.ProjectModuleMapper;
import io.github.xiaomisum.robotest.service.ai.task.type.AgentExecutionPreparation;
import io.github.xiaomisum.robotest.service.ai.task.type.TaskTypeAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BugClassifyTypeAdapterTest {

    @Test
    void prepare_limitsMediaAndParserCoversTrustedBugSet() {
        BugMapper bugMapper = mock(BugMapper.class);
        BugAttachmentMapper attachmentMapper = mock(BugAttachmentMapper.class);
        BugAttachmentService attachmentService = mock(BugAttachmentService.class);
        ProjectModuleMapper moduleMapper = mock(ProjectModuleMapper.class);
        BugClassifyTypeAdapter support = new BugClassifyTypeAdapter();
        ReflectionTestUtils.setField(support, "bugMapper", bugMapper);
        ReflectionTestUtils.setField(support, "bugAttachmentMapper", attachmentMapper);
        ReflectionTestUtils.setField(support, "bugAttachmentService", attachmentService);
        ReflectionTestUtils.setField(support, "projectModuleMapper", moduleMapper);
        UUID projectId = UUID.randomUUID();
        List<Bug> bugs = new ArrayList<>();
        List<BugAttachment> attachments = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Bug bug = new Bug();
            bug.setId(UUID.randomUUID());
            bug.setProjectId(projectId);
            bug.setTitle("bug-" + i);
            bugs.add(bug);
            for (int j = 0; j < 3; j++) {
                BugAttachment attachment = new BugAttachment();
                attachment.setBugId(bug.getId());
                attachment.setFileName(i + "-" + j + ".png");
                attachments.add(attachment);
            }
        }
        when(bugMapper.listByIds(any())).thenReturn(bugs);
        when(attachmentMapper.listByBugIds(any())).thenReturn(attachments);
        when(attachmentService.readBytes(any())).thenReturn(new byte[]{1});
        when(moduleMapper.listByProjectId(projectId)).thenReturn(List.of());
        Map<String, Object> input = Map.of("bugIds", bugs.stream().map(bug -> bug.getId().toString()).toList());

        AgentExecutionPreparation prepared = support.prepare(new TaskTypeAdapter.PreparationContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), projectId, input, null));

        assertEquals(8, prepared.media().size());
        assertEquals(5, prepared.proposalParser().apply("{\"items\":[]}").size());
    }
}
