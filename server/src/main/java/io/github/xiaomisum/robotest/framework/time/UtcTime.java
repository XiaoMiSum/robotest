package io.github.xiaomisum.robotest.framework.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 统一 UTC 时间口径的工具，避免业务代码直接使用系统默认时区的 now()。 */
public final class UtcTime {

    private UtcTime() {
    }

    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static LocalDateTime fromInstant(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 将服务器本地墙钟值转换为 UTC 墙钟值；仅用于响应边界，不能用于入库前转换。 */
    public static LocalDateTime toUtcWallClock(LocalDateTime value) {
        return value == null ? null
                : value.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    public static String toIsoFromSystemLocal(LocalDateTime value) {
        return toIso(toUtcWallClock(value));
    }

    public static String toIso(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }
}
