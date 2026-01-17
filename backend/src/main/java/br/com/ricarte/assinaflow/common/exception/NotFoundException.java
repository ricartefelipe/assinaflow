package br.com.ricarte.assinaflow.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}
