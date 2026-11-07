package io.github.xiaomisum.robotest.service.ai.task;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 产物承接器注册表：type → ArtifactAdopter（详设 3.6.5，落库由 3.6.1 承接服务执行）。
 */
@Component
public class ArtifactAdopterRegistry {

    private final Map<String, ArtifactAdopter> adopters;

    public ArtifactAdopterRegistry(List<ArtifactAdopter> beans) {
        this.adopters = beans.stream()
                .collect(Collectors.toMap(ArtifactAdopter::type, Function.identity(), (a, b) -> a));
    }

    public ArtifactAdopter get(String type) {
        return adopters.get(type);
    }
}
