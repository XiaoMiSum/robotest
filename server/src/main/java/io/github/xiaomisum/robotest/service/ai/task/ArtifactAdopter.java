package io.github.xiaomisum.robotest.service.ai.task;

/**
 * 产物落库承接器 SPI（详设 3.6.1「落库承接」列）：type → 承接服务，
 * 由各能力域注册；未注册类型的确认逐项回执 1000018114。
 */
public interface ArtifactAdopter {

    /** 对应任务类型（详设 3.6.1 枚举值） */
    String type();

    /**
     * 执行采纳落库（与确认记录同事务，详设 3.6.5）。
     * 失败抛 ServiceException，由调用方回滚该项并继续其余项。
     */
    AdoptOutcome adopt(AdoptContext context);
}
