package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.util.List;

@Data
public class PermissionTableRespDTO {

    private String topModule;
    private List<ModuleGroup> modules;

    @Data
    public static class ModuleGroup {
        private String module;
        private List<PermissionItem> permissions;
    }

    @Data
    public static class PermissionItem {
        private String code;
        private String name;
    }
}
