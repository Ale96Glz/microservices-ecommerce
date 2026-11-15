package com.aosorio.ecommerce.pagos.exception;

/**
 * El monto enviado no coincide con el total del pedido. La petición está bien
 * formada pero es inaplicable (RFC 9110, 422), a diferencia de los conflictos
 * de estado (409) como "pedido ya pagado".
 */
public class MontoNoCoincideException extends RuntimeException {

    public MontoNoCoincideException(String message) {
        super(message);
    }
}
