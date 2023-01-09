package com.demo.payment.controller;

import com.baomidou.mybatisplus.extension.api.R;
import com.demo.payment.config.WxPayConfig;
import com.demo.payment.enums.wxpay.WxNotifyType;
import com.demo.payment.enums.wxpay.WxRefundStatus;
import com.demo.payment.service.WxPayService;
import com.demo.payment.util.AjaxResult;
import com.demo.payment.util.HttpUtils;
import com.demo.payment.util.WechatPay2ValidatorForRequest;
import com.google.gson.Gson;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.exception.HttpCodeException;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.exception.ParseException;
import com.wechat.pay.contrib.apache.httpclient.exception.ValidationException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

@Slf4j
@CrossOrigin
@RestController
@RequestMapping("api/wx-pay")
@Api(tags = {"微信支付接口"})
public class WxPayController {

    @Resource
    private WxPayService wxPayService;

    @Resource
    private Verifier verifier;

    @Resource
    private WxPayConfig wxPayConfig;

    @ApiOperation("下单")
    @PostMapping("native/{productId}")
    public AjaxResult nativePay(@PathVariable Long productId){
        log.info("发起支付请求！");
        Map<String, Object> map =  wxPayService.getNativePay(productId);
        return AjaxResult.success(map);
    }

    @PostMapping("native/notify")
    @Transactional(timeout = 5,rollbackFor = Exception.class)
    public String nativeNotify(HttpServletRequest request, HttpServletResponse response){

        TreeMap<Object, Object> treeMap = new TreeMap<>();
        Gson gson = new Gson();
        try {
            String body = HttpUtils.readData(request);
            log.info("支付通知 ===>{}", body);
            Map<String, Object> hashMap = gson.fromJson(body, HashMap.class);
            String requestId = (String) hashMap.get("id");
            log.info("支付通知的id ===>{}", requestId);
            // 签名的验证
            /*WechatPay2ValidatorForRequest validatorForRequest = new WechatPay2ValidatorForRequest(verifier, body, requestId);
            if (! validatorForRequest.validate(request)) {
                log.info("支付通知，签名验证失败");
                response.setStatus(HttpStatus.SC_UNAUTHORIZED);
                treeMap.put("code", "FAIL");
                treeMap.put("message", "NOTIFY VALIDATOR SIGNATURE IS FAILED");
            }*/
            // 验证解密报文
            String decryptData = wxPayConfig.getDecryptData(request, body);
            log.info("支付结果===> {}", decryptData);

            // 处理订单
             wxPayService.processOrder(decryptData);

            response.setStatus(200);
            treeMap.put("code", "SUCCESS");
            treeMap.put("message", null);

        } catch (IOException e) {
            e.printStackTrace();
            response.setStatus(500);
            treeMap.put("code", "FAIL");
            treeMap.put("message", "失败");
            return gson.toJson(treeMap);
        } catch (HttpCodeException e) {
            e.printStackTrace();
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
            log.info("支付通知，签名验证失败");
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            treeMap.put("code", "FAIL");
            treeMap.put("message", "PARSE SIGNATURE ERROR");
        } catch (NotFoundException e) {
            e.printStackTrace();
            log.info("支付通知，签名验证失败");
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            treeMap.put("code", "FAIL");
            treeMap.put("message", "CERTIFICATE IS NOT FOUND");
        } catch (ValidationException e) {
            e.printStackTrace();
            log.info("支付通知，签名验证失败");
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            treeMap.put("code", "FAIL");
            treeMap.put("message", "NOTIFY VALIDATOR SIGNATURE IS FAILED");
        }
        return gson.toJson(treeMap);
    }

    @ApiOperation("取消订单")
    @PostMapping("cancel/{orderNo}")
    public AjaxResult cancelOrder(@PathVariable String orderNo){
        log.info("取消订单");
        wxPayService.cancelOrder(orderNo);
        return AjaxResult.success("订单取消成功");
    }

    @ApiOperation("查询订单")
    @GetMapping("select/{orderNo}")
    public AjaxResult getOrder(@PathVariable String orderNo){
        log.info("查询订单");
        String result = wxPayService.selectOrder(orderNo);
        return AjaxResult.success();
    }

    @ApiOperation("申请退款")
    @PostMapping("refunds/{orderNo}/{reason}")
    public AjaxResult refunds(@PathVariable String orderNo,@PathVariable String reason){
        log.info("申请退款");
        wxPayService.refunds(orderNo, reason);
        return AjaxResult.success();
    }
    @PostMapping("refunds/notify")
    @Transactional(timeout = 5,rollbackFor = Exception.class)
    public String refundsNotify(HttpServletRequest request, HttpServletResponse response){
        log.info("退款通知执行");
        Gson gson = new Gson();
        Map<String, String> map = new HashMap<>();//应答对象

        try {
            //处理通知参数
            String body = HttpUtils.readData(request);
            Map<String, Object> bodyMap = gson.fromJson(body, HashMap.class);
            String requestId = (String)bodyMap.get("id");
            log.info("支付通知的id ===> {}", requestId);

            //签名的验证
            /*WechatPay2ValidatorForRequest wechatPay2ValidatorForRequest
                    = new WechatPay2ValidatorForRequest(verifier, requestId, body);
            if(!wechatPay2ValidatorForRequest.validate(request)){

                log.error("通知验签失败");
                //失败应答
                response.setStatus(500);
                map.put("code", "ERROR");
                map.put("message", "通知验签失败");
                return gson.toJson(map);
            }*/
            String decryptData = wxPayConfig.getDecryptData(request, body);
            log.info("通知验签成功");

            //处理退款单
            wxPayService.processRefund(decryptData);

            //成功应答
            response.setStatus(200);
            map.put("code", "SUCCESS");
            map.put("message", "成功");
            return gson.toJson(map);

        } catch (ValidationException ve) {
            log.error("通知验签失败");
            //失败应答
            response.setStatus(HttpStatus.SC_UNAUTHORIZED);
            map.put("code", "ERROR");
            map.put("message", "通知验签失败");
            return gson.toJson(map);
        } catch (Exception e){
            e.printStackTrace();
            //失败应答
            response.setStatus(500);
            map.put("code", "ERROR");
            map.put("message", "失败");
            return gson.toJson(map);
        }
    }

    @ApiOperation("查询退款")
    @GetMapping("/query-refund/{refundNo}")
    public AjaxResult queryRefund(@PathVariable String refundNo) throws Exception {

        log.info("查询退款");
        String result = wxPayService.queryRefund(refundNo);
        Map<String, String> map = new TreeMap<>();
        map.put("result",result);
        return AjaxResult.success("查询成功",map);
    }

    @ApiOperation("获取账单url")
    @GetMapping("/querybill/{billDate}/{type}")
    public AjaxResult queryTradeBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("获取账单url");

        String downloadUrl = wxPayService.queryBill(billDate, type);
        Map<String, String> map = new TreeMap<>();
        map.put("downloadUrl",downloadUrl);
        return AjaxResult.success("获取账单url成功", map);
    }

    @ApiOperation("下载账单")
    @GetMapping("/downloadbill/{billDate}/{type}")
    public AjaxResult downloadBill(
            @PathVariable String billDate,
            @PathVariable String type) throws Exception {

        log.info("下载账单");
        String result = wxPayService.downloadBill(billDate, type);
        Map<String, String> map = new TreeMap<>();
        map.put("result",result);
        return AjaxResult.success(map);
    }
}
