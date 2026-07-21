package io.github.xiaomisum.robotest.convert;

import io.github.xiaomisum.robotest.model.dto.response.RoleRespDTO;
import io.github.xiaomisum.robotest.model.entity.SysRole;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface RoleConvertMapper {

    RoleConvertMapper INSTANCE = Mappers.getMapper(RoleConvertMapper.class);

    @Mapping(target = "userCount", ignore = true)
    RoleRespDTO toRespDTO(SysRole role);

    default List<String> toPermissions(List<String> permissions) {
        return permissions != null ? permissions : List.of();
    }
}
