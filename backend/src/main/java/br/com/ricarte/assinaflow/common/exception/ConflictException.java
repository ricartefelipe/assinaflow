package br.com.ricarte.assinaflow.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}
