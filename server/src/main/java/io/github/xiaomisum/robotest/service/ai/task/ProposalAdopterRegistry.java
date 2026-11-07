package io.github.xiaomisum.robotest.service.ai.task;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 提案承接器注册表：type → ProposalAdopter（总册 3.6.5，落库由 3.6.1 承接服务执行）。
 */
@Component
public class ProposalAdopterRegistry {

    private final Map<String, ProposalAdopter> adopters;

    public ProposalAdopterRegistry(List<ProposalAdopter> beans) {
        this.adopters = beans.stream()
                .collect(Collectors.toMap(ProposalAdopter::type, Function.identity(), (a, b) -> a));
    }

    public ProposalAdopter get(String type) {
        return adopters.get(type);
    }
}
