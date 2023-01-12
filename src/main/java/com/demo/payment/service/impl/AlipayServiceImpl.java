package com.demo.payment.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.demo.payment.config.AlipayClientConfig;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.service.AlipayService;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;

@Slf4j
@Service
public class AlipayServiceImpl implements AlipayService {

    @Resource
    private AlipayClient alipayClient;

    @Resource
    private Environment config;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private ProductService productService;

    @Override
    public String tradeCreate(Long productId) {
        log.info("生成订单");
        OrderInfo orderInfo = orderInfoService.getOrderInfoByProductId(productId);
        log.info("调用AliAPI");

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(config.getProperty("alipay.notify-url"));
        request.setReturnUrl(config.getProperty("alipay.return-url"));
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderInfo.getOrderNo());
        BigDecimal total = new BigDecimal(orderInfo.getTotalFee().toString()).multiply(new BigDecimal("100"));
        bizContent.put("total_amount", total);
        bizContent.put("subject", orderInfo.getTitle());
        bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
        // bizContent.put("time_expire", "2022-08-01 22:00:00");

        //// 商品明细信息，按需传入
        //JSONArray goodsDetail = new JSONArray();
        //JSONObject goods1 = new JSONObject();
        //goods1.put("goods_id", "goodsNo1");
        //goods1.put("goods_name", "子商品1");
        //goods1.put("quantity", 1);
        //goods1.put("price", 0.01);
        //goodsDetail.add(goods1);
        //bizContent.put("goods_detail", goodsDetail);

        //// 扩展信息，按需传入
        //JSONObject extendParams = new JSONObject();
        //extendParams.put("sys_service_provider_id", "2088511833207846");
        //bizContent.put("extend_params", extendParams);

        request.setBizContent(bizContent.toString());
        AlipayTradePagePayResponse response = null;
        try {
            response = alipayClient.pageExecute(request);
            if(response.isSuccess()){
                log.info("调用成功 ===> {}", response.getBody());
                return response.getBody();
            } else {
                log.info("调用失败===> {}", response.getCode() + response.getMsg());
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
            throw new RuntimeException("创建订单失败");
        }
        return null;
    }
}
