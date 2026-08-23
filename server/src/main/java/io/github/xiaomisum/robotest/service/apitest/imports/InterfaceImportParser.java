package io.github.xiaomisum.robotest.service.apitest.imports;

import java.util.List;

/**
 * 导入解析策略（接口管理详细设计 4.1），每种格式一个实现
 */
public interface InterfaceImportParser {

    /** 对应 api_import_mapping.source_type */
    String sourceType();

    /** 是否可处理该格式（按 format 提示或内容嗅探） */
    boolean supports(String formatHint, String content);

    /** 解析为规范化操作列表；无法识别的条目跳过并由调用方记录失败清单 */
    List<ImportedOperation> parse(String content);
}
