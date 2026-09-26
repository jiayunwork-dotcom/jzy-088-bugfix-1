package com.example.bem.validation;

/**
 * 输入非法时抛出，携带稳定的机器码供调用方分支处理。
 */
public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
