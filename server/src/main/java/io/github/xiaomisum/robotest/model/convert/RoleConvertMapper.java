package io.github.xiaomisum.robotest.model.convert;

import io.github.xiaomisum.robotest.model.dto.request.admin.RoleCreateReqDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleRespDTO;
import io.github.xiaomisum.robotest.model.dto.response.admin.RoleSimpleRespDTO;
import io.github.xiaomisum.robotest.model.entity.admin.SysRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface RoleConvertMapper {

    @Mapping(target = "userCount", ignore = true)
    RoleRespDTO toRespDTO(SysRole role);

    @Mapping(target = "userCount", ignore = true)
    RoleSimpleRespDTO toSimpleRespDTO(SysRole role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "description", ignore = true)
    @Mapping(target = "isSystem", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    SysRole toEntity(RoleCreateReqDTO dto);

    default List<String> toPermissions(List<String> permissions) {
        return permissions != null ? permissions : List.of();
    }
}
