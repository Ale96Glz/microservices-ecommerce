package com.aosorio.ecommerce.catalogo.exception;

public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
