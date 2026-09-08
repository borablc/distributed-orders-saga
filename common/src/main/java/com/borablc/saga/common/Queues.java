package com.borablc.saga.common;

public final class Queues {
    private Queues(){}

    // Exchanges
    public static final String COMMAND_EXCHANGE = "saga.commands";
    public static final String RETRY_EXCHANGE   = "saga.retry";
    public static final String DLX              = "saga.dlx";

    // Routing keys
    public static final String RK_RESERVE_STOCK  = "inventory.reserve";
    public static final String RK_RELEASE_STOCK  = "inventory.release";
    public static final String RK_CHARGE_PAYMENT = "payment.charge";
    public static final String RK_REFUND_PAYMENT = "payment.refund";

    // Binding patterns (topic exchange)
    public static final String RK_INVENTORY_ALL = "inventory.*";
    public static final String RK_PAYMENT_ALL   = "payment.*";

    // Main Queues
    public static final String INVENTORY_QUEUE = "inventory.commands";
    public static final String PAYMENT_QUEUE   = "payment.commands";

    // Retry queues (Returns to main exchange after TTL expires)
    public static final String INVENTORY_RETRY_QUEUE = "inventory.commands.retry";
    public static final String PAYMENT_RETRY_QUEUE   = "payment.commands.retry";

    // Parking lot (Permanently failed messages, manual handling)
    public static final String INVENTORY_PARKING_LOT = "inventory.commands.parking-lot";
    public static final String PAYMENT_PARKING_LOT   = "payment.commands.parking-lot";
}