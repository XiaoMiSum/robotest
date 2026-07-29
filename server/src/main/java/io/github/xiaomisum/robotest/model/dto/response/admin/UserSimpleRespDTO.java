package io.github.xiaomisum.robotest.model.dto.response.admin;

import lombok.Data;

import java.util.UUID;

@Data
public class UserSimpleRespDTO {

    private UUID id;
    private String name;
}
