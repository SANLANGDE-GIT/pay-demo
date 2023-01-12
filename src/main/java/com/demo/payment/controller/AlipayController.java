package com.demo.payment.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.service.AlipayService;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.util.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Api(tags = {"支付宝接口"})
@CrossOrigin
@RestController
@RequestMapping("api/ali-pay/")
public class AlipayController {

    @Resource
    private AlipayService alipayService;
    @Resource
    private Environment config;

    @Resource
    private OrderInfoService orderInfoService;


    @PostMapping("trade/page/pay/{productId}")
    public AjaxResult tradePagePay(@PathVariable Long productId){
        log.info("统一收单下单支付页面接口调用");
        String formStr = alipayService.tradeCreate(productId);
        Map<String, String> map = new HashMap<>();
        map.put("formStr", formStr);
        return AjaxResult.success(map);
    }

    @ApiOperation("收单通知")
    @PostMapping("trade/notify")
    public String tradeNotify(@RequestParam Map<String, String> params){
        log.info("收单通知信息===>{}", JSON.toJSON(params));

        String result = "failure";

        try {
            boolean signVerified = AlipaySignature.rsaCheckV1(
                    params,
                    config.getProperty("alipay.alipay-public-key"),
                    AlipayConstants.CHARSET_UTF8,
                    AlipayConstants.SIGN_TYPE_RSA2); //调用SDK验证签名
            if(! signVerified){
                // 验签失败则记录异常日志，并在response中返回failure.
                log.info("收单通知，验签失败！");
                return result;
            }
            // 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，
            // 商家需要验证该通知数据中的 out_trade_no 是否为商家系统中创建的订单号。
            OrderInfo  orderInfo = orderInfoService.getOrderInfoByOrderNo(params.get("out_trade_no"));
            if (orderInfo == null) {
                log.info("未找到支付的订单");
                return result;
            }
            // 判断 total_amount 是否确实为该订单的实际金额（即商家订单创建时的金额）。
            String totalAmount = params.get("total_amount");
            // 校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方（有的时候，一个商家可能有多个 seller_id/seller_email）。
            // 验证 app_id 是否为该商家本身。

            // 校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            result = "success";
        } catch (AlipayApiException e) {
            e.printStackTrace();
            return result;
        }
        return result;
    }

}
