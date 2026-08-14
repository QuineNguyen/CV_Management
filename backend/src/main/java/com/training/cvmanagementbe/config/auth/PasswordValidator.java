package com.training.cvmanagementbe.config.auth;

import com.training.cvmanagementbe.enums.ErrorCode;
import com.training.cvmanagementbe.enums.PasswordCharset;
import com.training.cvmanagementbe.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Enforce password rule. Shared by change-password and admin reset.
@Component
public class PasswordValidator {

    private final int minLength;

    public PasswordValidator(@Value("${security.password.min-length:8}") int minLength) {
        this.minLength = minLength;
    }

    public void validate(String password) {
        if (!isValid(password)) {
            throw new ApiException.BusinessRuleException(ErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    public boolean isValid(String password) {
        if (password == null || password.length() < minLength) {
            return false;
        }
        for (PasswordCharset charset : PasswordCharset.values()) {
            if (!charset.isPresentIn(password)) {
                return false;
            }
        }
        return true;
    }

    public int minLength() {
        return minLength;
    }
}
