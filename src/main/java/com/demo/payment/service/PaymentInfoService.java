package com.demo.payment.service;

import java.util.Map;

public interface PaymentInfoService {

    void savePaymentInfo(Map<String, Object> resultInfo);

    void savePaymentInfoForAli(Map<String, String> params);
}
