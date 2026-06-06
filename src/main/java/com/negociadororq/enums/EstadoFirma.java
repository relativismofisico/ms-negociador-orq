package com.negociadororq.enums;

public enum EstadoFirma {
    SIN_CESION, //Estado cuando no se ha realizado el endoso de la factura operaction factoring
    CESION, //Estado cuando se ha realizado el endoso de la factura en operacion factoring
    SIN_DESCUENTO, //Estado cuando no se ha realizado el descuento de la factura operacion confirming
    DESCUENTO, //Estado cuando se ha realizado el descuento de la factura en operaciones de comfirming
    EN_APROBACION, //Estado cuando se realiza el endoso o el descuento pero falta al menos un actor en aprobar
    RECHAZADA, //Estado cuando se realiza el endoso o el descuento pero al menos un actor en rechazo el endoso
    EN_REVISION,  //Estado cuando se realiza el endoso o el descuento pero ningun actor ha aceptado el endoso o el descuento
    APROBACION  //Estado cuando se realiza el endoso o el descuento y los actores han aceptado el endoso o el descuento
}
