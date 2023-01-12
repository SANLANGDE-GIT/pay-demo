package com.demo.payment.controller;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayConstants;
import com.alipay.api.internal.util.AlipaySignature;
import com.baomidou.mybatisplus.extension.api.R;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.service.AlipayService;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.util.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
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
                log.error("收单通知，验签失败！");
                return result;
            }
            // 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，
            // 商家需要验证该通知数据中的 out_trade_no 是否为商家系统中创建的订单号。
            OrderInfo  orderInfo = orderInfoService.getOrderInfoByOrderNo(params.get("out_trade_no"));
            if (orderInfo == null) {
                log.error("未找到支付的订单");
                return result;
            }
            // 判断 total_amount 是否确实为该订单的实际金额（即商家订单创建时的金额）。
            String totalAmount = params.get("total_amount");
            Integer totalAmountInt = new BigDecimal(totalAmount).multiply(new BigDecimal("100")).intValue();
            Integer totalFee = orderInfo.getTotalFee();
            if(! totalFee.equals(totalAmountInt)){
                log.error("金额校验失败！");
                return result;
            }
            // 校验通知中的 seller_id（或者 seller_email) 是否为 out_trade_no 这笔单据的对应的操作方（有的时候，一个商家可能有多个 seller_id/seller_email）。
            String sellerId = params.get("seller_id");
            String pid = config.getProperty("alipay.seller-id");
            if(! sellerId.equals(pid)){
                log.error("商户ID校验失败！");
                return result;
            }
            // 验证 app_id 是否为该商家本身。
            String appId = params.get("app_id");
            String aplId = config.getProperty("alipay.app-id");
            if(! appId.equals(aplId)){
                log.error("应用ID校验失败！");
                return result;
            }

            // 上述 1、2、3、4 有任何一个验证不通过，则表明本次通知是异常通知，务必忽略。在上述验证通过后商家必须根据支付宝不同类型的业务通知，
            // 正确的进行不同的业务处理，并且过滤重复的通知结果数据。在支付宝的业务通知中，只有交易通知状态为 TRADE_SUCCESS 或 TRADE_FINISHED 时，
            // 支付宝才会认定为买家付款成功。
            String tradeStatus = params.get("trade_status");
            if ( ! "TRADE_SUCCESS".equals(tradeStatus)){
                log.error("交易失败");
                return result;
            }
            // 校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            // 修改订单状态，记录日志
            alipayService.processOrder(params);
            result = "success";
        } catch (AlipayApiException e) {
            e.printStackTrace();
            return result;
        }
        return result;
    }

    @ApiOperation("交易关闭")
    @PostMapping("trade/close/{orderNo}")
    public AjaxResult cancelOrder(@PathVariable String orderNo){
        log.info("取消订单");
        alipayService.cancelOrder(orderNo);
        return AjaxResult.success("订单取消成功！");
    }

    @ApiOperation("查询订单")
    @PostMapping("trade/query/{orderNo}")
    public AjaxResult queryOrder(@PathVariable String orderNo){
        log.info("查询订单");
        String result = alipayService.queryOrder(orderNo);
        Map<String, String> map = new HashMap<>();
        map.put("result", result);
        return AjaxResult.success(map);
    }

    @ApiOperation("申请退款")
    @PostMapping("/trade/refund/{orderNo}/{reason}")
    public AjaxResult refunds(@PathVariable String orderNo, @PathVariable String reason){
        log.info("申请退款");
        String result = alipayService.refund(orderNo, reason);
        return AjaxResult.success("OK",result);
    }

    @ApiOperation("退款查询")
    @PostMapping("/trade/refund/query/{orderNo}")
    public AjaxResult refundQuery(@PathVariable String orderNo){
        log.info("申请退款");
        String result = alipayService.refundQuery(orderNo);
        return AjaxResult.success("OK",result);
    }

    @ApiOperation("获取账单url")
    @GetMapping("/bill/downloadurl/query/{billDate}/{type}")
    public AjaxResult queryTradeBill(
            @PathVariable String billDate,
            @PathVariable String type)  {
        log.info("获取账单url");
        String downloadUrl = alipayService.queryBill(billDate, type);
        Map<String, String> map = new HashMap<>();
        map.put("downloadUrl", downloadUrl);
        return AjaxResult.success("获取账单url成功",map);
    }

}
