package br.com.ricarte.assinaflow.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String code, String message) {
        super(code, HttpStatus.UNAUTHORIZED, message);
    }
}
