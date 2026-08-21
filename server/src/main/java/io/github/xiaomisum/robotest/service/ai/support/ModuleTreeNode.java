package io.github.xiaomisum.robotest.service.ai.support;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.tcase.ProjectModule;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseDocument;

import java.util.UUID;

/**
 * 模块树统一节点：ProjectModule（目录）与 TestCaseDocument（文档）的公共投影，
 * 供 AiModuleTreeSupport 等 AI 支撑类统一操作。
 */
public record ModuleTreeNode(UUID id, UUID parentId, String name, String type) {

    public static ModuleTreeNode fromProjectModule(ProjectModule module) {
        return new ModuleTreeNode(module.getId(), module.getParentId(),
                module.getName(), Constants.ModuleType.DIRECTORY);
    }

    public static ModuleTreeNode fromTestCaseDocument(TestCaseDocument doc) {
        return new ModuleTreeNode(doc.getId(), doc.getModuleId(),
                doc.getName(), Constants.ModuleType.DOCUMENT);
    }

    public boolean isDocument() {
        return Constants.ModuleType.DOCUMENT.equals(type);
    }
}
