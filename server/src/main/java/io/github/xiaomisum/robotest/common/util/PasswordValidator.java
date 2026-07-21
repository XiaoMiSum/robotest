package io.github.xiaomisum.robotest.common.util;

import io.github.xiaomisum.robotest.common.ErrorCodeConstants;
import xyz.migoo.framework.common.exception.util.ServiceExceptionUtil;

import java.util.regex.Pattern;

public final class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^a-zA-Z0-9]");

    private PasswordValidator() {
    }

    /**
     * 校验密码强度：>=8位，含大写、小写、数字、特殊字符各至少一个。
     * 不满足时抛出 BusinessException。
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PASSWORD_TOO_WEAK);
        }
        if (!UPPERCASE.matcher(password).find()
                || !LOWERCASE.matcher(password).find()
                || !DIGIT.matcher(password).find()
                || !SPECIAL.matcher(password).find()) {
            throw ServiceExceptionUtil.get(ErrorCodeConstants.PASSWORD_TOO_WEAK);
        }
    }
}
