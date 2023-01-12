package com.demo.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.RefundInfo;
import com.demo.payment.enums.AliPayTradeState;
import com.demo.payment.enums.OrderStatus;
import com.demo.payment.enums.PayType;
import com.demo.payment.enums.wxpay.WxTradeState;
import com.demo.payment.service.*;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

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
    private RefundInfoService refundInfoService;

    @Resource
    private PaymentInfoService paymentInfoService;

    private final ReentrantLock reentrantLock = new ReentrantLock();

    @Override
    public String tradeCreate(Long productId) {
        log.info("生成订单");
        OrderInfo orderInfo = orderInfoService.getOrderInfoByProductId(productId, PayType.ALIPAY.getType());
        log.info("调用AliAPI");

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(config.getProperty("alipay.notify-url"));
        request.setReturnUrl(config.getProperty("alipay.return-url"));
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderInfo.getOrderNo());
        Double total = new BigDecimal(orderInfo.getTotalFee().toString()).divide(new BigDecimal("100")).doubleValue();
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processOrder(Map<String, String> params) {
        log.info("处理订单！");

        String outTradeNo = params.get("out_trade_no");

        if(reentrantLock.tryLock()) {
            try {
                // 查看订单状态
                String status = orderInfoService.getOrderStatusByOrderNo(outTradeNo);
                if (!OrderStatus.NOTPAY.getType().equals(status)) {
                    return;
                }

                orderInfoService.updateOrderStatusByOrderNo(outTradeNo, OrderStatus.SUCCESS.getType());
                // 记录订单日志
                paymentInfoService.savePaymentInfoForAli(params);
            }finally {
                reentrantLock.unlock();
            }
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {

        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("trade_no", orderNo);
        request.setBizContent(bizContent.toString());
        AlipayTradeCloseResponse response = null;
        try {
            response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功!");
            } else {
                log.info(response.getSubMsg());
            }
            this.updateOrderStatusByOrderNo(orderNo);
        } catch (Exception e) {
            e.printStackTrace();
            log.info("调用失败");
            throw new RuntimeException("订单取消接口调用失败！");
        }
    }

    @Override
    public void updateOrderStatusByOrderNo(String orderNo) {
        orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.CANCEL.getType());
    }

    @Override
    public String queryOrder(String orderNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", orderNo);
        //bizContent.put("trade_no", "2014112611001004680073956707");
        request.setBizContent(bizContent.toString());
        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功");
                return response.getBody();
            } else {
                log.warn(response.getSubMsg());
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("调用失败");
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkOrderStatus(String orderNo) {
        log.info("查询核实订单状态===>{}",orderNo);
        String result = this.queryOrder(orderNo);
        if(StringUtils.isBlank(result)){
            log.info("订单未创建！");
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.CLOSED.getType());
            return;
        }
        JSONObject jsonObject = JSON.parseObject(result);
        JSONObject alipayTradeQueryResponse = jsonObject.getJSONObject("alipay_trade_query_response");
        String tradeStatus = alipayTradeQueryResponse.getString("trade_status");
        if(AliPayTradeState.SUCCESS.getType().equals(tradeStatus)){
            log.info("核实订单已支付 ===> {}",orderNo);
            // 更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo,OrderStatus.SUCCESS.getType());
            // 记录支付日志
             paymentInfoService.savePaymentInfoForAli(jsonObject.getObject("alipay_trade_query_response", Map.class));
        }
        if(AliPayTradeState.NOTPAY.getType().equals(tradeStatus)){
            cancelOrder(orderNo);
            // 更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.CLOSED.getType());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refund(String orderNo, String reason) {


        log.info("调用Ali退款API");

        OrderInfo orderInfo = orderInfoService.getOrderInfoByOrderNo(orderNo);
        if(orderInfo ==null){
            log.warn("未查询到订单");
            return "未查询到订单";
        }
        RefundInfo refundInfo = refundInfoService.createRefundInfo(orderNo, reason);

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("trade_no", orderNo);
        Integer totalFee = orderInfo.getTotalFee();
        double doubleValue = new BigDecimal(String.valueOf(totalFee)).divide(new BigDecimal("100")).doubleValue();
        bizContent.put("refund_amount", doubleValue);
        bizContent.put("out_request_no", reason);

        //// 返回参数选项，按需传入
        //JSONArray queryOptions = new JSONArray();
        //queryOptions.add("refund_detail_item_list");
        //bizContent.put("query_options", queryOptions);

        request.setBizContent(bizContent.toString());
        try {
            AlipayTradeRefundResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功");
                orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS.getType());
                refundInfoService.updateRefundForAliPay(refundInfo.getRefundNo(),
                        response.getBody(),
                        OrderStatus.REFUND_SUCCESS.getType());
            } else {
                log.info("调用失败");
                orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL.getType());
                refundInfoService.updateRefundForAliPay(refundInfo.getRefundNo(),
                        response.getBody(),
                        OrderStatus.REFUND_ABNORMAL.getType());
            }
            return response.getSubMsg();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("申请退款失败");
        }
    }

    @Override
    public String refundQuery(String orderNo) {
        log.info("查询退款接口调用 ===> {}", orderNo);

        AlipayTradeFastpayRefundQueryRequest request = new AlipayTradeFastpayRefundQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("trade_no", orderNo);
        bizContent.put("out_request_no", orderNo);

        //// 返回参数选项，按需传入
        //JSONArray queryOptions = new JSONArray();
        //queryOptions.add("refund_detail_item_list");
        //bizContent.put("query_options", queryOptions);

        request.setBizContent(bizContent.toString());
        try {
            AlipayTradeFastpayRefundQueryResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功");
                return response.getBody();
            } else {
                log.warn("调用失败");
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("退款查询接口调用失败");
        }
    }

    @Override
    public String queryBill(String billDate, String type) {
        AlipayDataDataserviceBillDownloadurlQueryRequest request = new AlipayDataDataserviceBillDownloadurlQueryRequest();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("bill_type",type);
        jsonObject.put("bill_date",billDate);
        // jsonObject.put("smid",); //二级商户smid，这个参数只在bill_type是trade_zft_merchant时才能使用
        request.setBizContent(jsonObject.toJSONString());
        try {
            AlipayDataDataserviceBillDownloadurlQueryResponse response = alipayClient.execute(request);
            if(response.isSuccess()){
                log.info("调用成功");
                String body = response.getBody();
                JSONObject jsonBody = JSON.parseObject(body);
                JSONObject downloadUrlQueryResponse = jsonBody.getJSONObject("alipay_data_dataservice_bill_downloadurl_query_response");
                return downloadUrlQueryResponse.getString("bill_download_url");
            } else {
                log.warn("调用失败");
                return null;
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return null;
    }


}
