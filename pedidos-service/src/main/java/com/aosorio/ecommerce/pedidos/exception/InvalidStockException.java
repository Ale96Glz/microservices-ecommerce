package com.aosorio.ecommerce.pedidos.exception;

public class InvalidStockException extends RuntimeException {

    public InvalidStockException(String message) {
        super(message);
    }
}
