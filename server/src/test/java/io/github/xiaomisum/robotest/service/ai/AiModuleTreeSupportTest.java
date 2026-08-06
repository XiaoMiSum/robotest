package io.github.xiaomisum.robotest.service.ai;

import io.github.xiaomisum.robotest.framework.common.Constants;
import io.github.xiaomisum.robotest.model.entity.tcase.TestCaseModule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AiModuleTreeSupport 纯函数单测：索引构建、路径构建（乱序输入回溯缓存）、
 * 子树文档收集与模块收集（Regression / MissingPoint / PlanOrder 共用）。
 */
class AiModuleTreeSupportTest {

    private TestCaseModule module(UUID id, String name, String type, UUID parentId) {
        TestCaseModule m = new TestCaseModule();
        m.setId(id);
        m.setName(name);
        m.setType(type);
        m.setParentId(parentId);
        return m;
    }

    @Test
    void indexByParent_buildsBothMaps() {
        UUID root = UUID.randomUUID();
        UUID doc = UUID.randomUUID();
        TestCaseModule r = module(root, "目录A", Constants.ModuleType.DIRECTORY, null);
        TestCaseModule d = module(doc, "文档1", Constants.ModuleType.DOCUMENT, root);

        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(List.of(r, d));

        assertEquals(r, index.moduleById().get(root));
        assertEquals(d, index.moduleById().get(doc));
        assertEquals(List.of(d), index.childrenByParent().get(root));
    }

    @Test
    void buildModulePaths_childBeforeParent_resolvesByBacktrack() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        TestCaseModule mA = module(a, "目录A", Constants.ModuleType.DIRECTORY, null);
        TestCaseModule mB = module(b, "目录B", Constants.ModuleType.DIRECTORY, a);
        TestCaseModule mC = module(c, "文档C", Constants.ModuleType.DOCUMENT, b);

        // 乱序输入（子在前父在后），递归回溯 + 缓存应产出正确路径
        Map<UUID, String> paths = AiModuleTreeSupport.buildModulePaths(List.of(mC, mA, mB));

        assertEquals("目录A", paths.get(a));
        assertEquals("目录A/目录B", paths.get(b));
        assertEquals("目录A/目录B/文档C", paths.get(c));
    }

    @Test
    void buildModulePaths_danglingParent_usesOwnName() {
        UUID doc = UUID.randomUUID();
        TestCaseModule m = module(doc, "孤儿文档", Constants.ModuleType.DOCUMENT, UUID.randomUUID());

        Map<UUID, String> paths = AiModuleTreeSupport.buildModulePaths(List.of(m));

        assertEquals("孤儿文档", paths.get(doc));
    }

    @Test
    void collectDocumentIds_directoryCollectsDescendantDocs() {
        UUID dir = UUID.randomUUID();
        UUID sub = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        TestCaseModule mDir = module(dir, "目录", Constants.ModuleType.DIRECTORY, null);
        TestCaseModule mSub = module(sub, "子目录", Constants.ModuleType.DIRECTORY, dir);
        TestCaseModule mDoc1 = module(doc1, "文档1", Constants.ModuleType.DOCUMENT, dir);
        TestCaseModule mDoc2 = module(doc2, "文档2", Constants.ModuleType.DOCUMENT, sub);

        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(List.of(mDir, mSub, mDoc1, mDoc2));
        Set<UUID> docs = new LinkedHashSet<>();
        AiModuleTreeSupport.collectDocumentIds(dir, index.moduleById(), index.childrenByParent(), docs);

        assertEquals(Set.of(doc1, doc2), docs);
    }

    @Test
    void collectDocumentIds_documentItselfIsLeaf() {
        UUID doc = UUID.randomUUID();
        TestCaseModule m = module(doc, "文档", Constants.ModuleType.DOCUMENT, null);

        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(List.of(m));
        Set<UUID> docs = new LinkedHashSet<>();
        AiModuleTreeSupport.collectDocumentIds(doc, index.moduleById(), index.childrenByParent(), docs);

        assertEquals(Set.of(doc), docs);
    }

    @Test
    void collectDocumentIds_unknownModule_yieldsEmpty() {
        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(List.of());
        Set<UUID> docs = new LinkedHashSet<>();
        AiModuleTreeSupport.collectDocumentIds(UUID.randomUUID(), index.moduleById(), index.childrenByParent(), docs);

        assertTrue(docs.isEmpty());
    }

    @Test
    void collectSubtreeModuleIds_includesSelfAndDescendants() {
        UUID dir = UUID.randomUUID();
        UUID sub = UUID.randomUUID();
        UUID doc = UUID.randomUUID();
        TestCaseModule mDir = module(dir, "目录", Constants.ModuleType.DIRECTORY, null);
        TestCaseModule mSub = module(sub, "子目录", Constants.ModuleType.DIRECTORY, dir);
        TestCaseModule mDoc = module(doc, "文档", Constants.ModuleType.DOCUMENT, sub);

        AiModuleTreeSupport.ModuleIndex index = AiModuleTreeSupport.indexByParent(List.of(mDir, mSub, mDoc));
        Set<UUID> ids = new LinkedHashSet<>();
        AiModuleTreeSupport.collectSubtreeModuleIds(dir, index.moduleById(), index.childrenByParent(), ids);

        assertEquals(Set.of(dir, sub, doc), ids);
    }
}
