package com.example.bem.web;

import com.example.bem.induction.NonConvergentException;
import com.example.bem.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 统一错误响应：{ code: 机器码, message: 可读说明 }。
 *  - 非法输入      -> 400
 *  - 迭代不收敛    -> 422（请求结构没问题，但气动核算失败）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleValidation(ValidationException ex) {
        String code = ex.code();
        HttpStatus status = "POLAR_NOT_FOUND".equals(code)
                || "STATION_INDEX_OUT_OF_RANGE".equals(code)
                ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(new Dtos.ErrorResponse(code, ex.getMessage()));
    }

    @ExceptionHandler(NonConvergentException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleNonConvergent(NonConvergentException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new Dtos.ErrorResponse("NOT_CONVERGED", ex.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Dtos.ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new Dtos.ErrorResponse("MALFORMED_JSON", "请求体不是合法 JSON：" + ex.getMessage()));
    }
}
