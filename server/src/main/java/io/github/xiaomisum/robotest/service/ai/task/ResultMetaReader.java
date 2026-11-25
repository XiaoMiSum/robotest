package io.github.xiaomisum.robotest.service.ai.task;

import java.util.Map;

/** result_meta 读取口径：导入侧写 documentMeta 嵌套结构，存量任务可能是顶层扁平结构 */
public final class ResultMetaReader {

    private ResultMetaReader() {
    }

    public static Object read(Map<String, Object> resultMeta, String key) {
        if (resultMeta == null) {
            return null;
        }
        Object documentMeta = resultMeta.get("documentMeta");
        if (documentMeta instanceof Map<?, ?> nested) {
            Object value = nested.get(key);
            if (value != null) {
                return value;
            }
        }
        return resultMeta.get(key);
    }
}
