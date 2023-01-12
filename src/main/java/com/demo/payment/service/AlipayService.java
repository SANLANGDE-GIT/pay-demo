package com.demo.payment.service;

import java.util.Map;

public interface AlipayService {
    String tradeCreate(Long productId);

    void processOrder(Map<String, String> params);

    void cancelOrder(String orderNo);

    void updateOrderStatusByOrderNo(String orderNo);

    String queryOrder(String orderNo);

    void checkOrderStatus(String orderNo);

    String refund(String orderNo, String reason);

    String refundQuery(String orderNo);

    String queryBill(String billDate, String type);
}
