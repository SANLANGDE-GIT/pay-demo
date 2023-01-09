package com.demo.payment.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.payment.entity.PaymentInfo;
import com.demo.payment.enums.PayType;
import com.demo.payment.mapper.PaymentInfoMapper;
import com.demo.payment.service.PaymentInfoService;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

    @Override
    public void savePaymentInfo(Map<String, Object> resultInfo) {
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderNo((String) resultInfo.get("out_trade_no"));
        paymentInfo.setTransactionId((String) resultInfo.get("transaction_id"));
        paymentInfo.setPaymentType(PayType.WXPAY.getType());
        paymentInfo.setTradeType((String) resultInfo.get("trade_type"));
        paymentInfo.setTradeState((String) resultInfo.get("trade_state"));
        String amount = (String) resultInfo.get("amount");
        JSONObject jsonObject = JSON.parseObject(amount);
        paymentInfo.setPayerTotal(jsonObject.getInteger("payer_total"));
        paymentInfo.setContent((String) resultInfo.get("amount"));
        baseMapper.insert(paymentInfo);
    }
}
