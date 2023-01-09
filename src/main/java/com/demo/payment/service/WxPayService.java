package com.demo.payment.service;

import java.util.Map;

public interface WxPayService {

    Map<String, Object> getNativePay(Long productId);

    void processOrder(String decryptData);

    void cancelOrder(String orderNo);

    String selectOrder(String orderNo);

    void checkOrderStatus(String orderNo);

    void refunds(String orderNo, String reason);

    void processRefund(String decryptData);

    String queryRefund(String refundNo);

    String queryBill(String billDate, String type);

    String downloadBill(String billDate, String type);

    void checkRefundStatus(String refundNo);
}
