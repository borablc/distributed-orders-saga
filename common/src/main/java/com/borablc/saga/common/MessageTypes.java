package com.borablc.saga.common;

public final class MessageTypes {

    private MessageTypes(){}

    public static final String ORDER_CREATED             = "order.created.v1";
    public static final String ORDER_CONFIRMED           = "order.confirmed.v1";
    public static final String ORDER_CANCELLED           = "order.cancelled.v1";

    public static final String STOCK_RESERVED            = "stock.reserved.v1";
    public static final String STOCK_RESERVATION_FAILED  = "stock.reservation-failed.v1";
    public static final String STOCK_RELEASED            = "stock.released.v1";

    public static final String PAYMENT_CHARGED           = "payment.charged.v1";
    public static final String PAYMENT_FAILED            = "payment.failed.v1";
    public static final String PAYMENT_REFUNDED          = "payment.refunded.v1";

    public static final String RESERVE_STOCK             = "reserve-stock.v1";
    public static final String RELEASE_STOCK             = "release-stock.v1";
    public static final String CHARGE_PAYMENT            = "charge-payment.v1";
    public static final String REFUND_PAYMENT            = "refund-payment.v1";
}
