package br.com.ricarte.assinaflow.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {
    public ForbiddenException(String code, String message) {
        super(code, HttpStatus.FORBIDDEN, message);
    }
}
