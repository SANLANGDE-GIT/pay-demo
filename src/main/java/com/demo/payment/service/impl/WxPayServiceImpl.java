package com.demo.payment.service.impl;

import com.demo.payment.config.WxPayConfig;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.RefundInfo;
import com.demo.payment.enums.OrderStatus;
import com.demo.payment.enums.PayType;
import com.demo.payment.enums.wxpay.WxApiType;
import com.demo.payment.enums.wxpay.WxNotifyType;
import com.demo.payment.enums.wxpay.WxRefundStatus;
import com.demo.payment.enums.wxpay.WxTradeState;
import com.demo.payment.mapper.OrderInfoMapper;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.service.PaymentInfoService;
import com.demo.payment.service.RefundInfoService;
import com.demo.payment.service.WxPayService;
import com.demo.payment.util.OrderNoUtils;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;


@Service
@Slf4j
public class WxPayServiceImpl implements WxPayService {

    @Resource
    private WxPayConfig wxPayConfig;

    @Resource
    private CloseableHttpClient wxPayClient;

    @Resource
    private OrderInfoService orderInfoService;
    @Resource
    private PaymentInfoService paymentInfoService;

    @Resource
    private RefundInfoService refundInfoService;

    @Resource
    private CloseableHttpClient wxPayNoSignClient; //无需应答签名

    private final ReentrantLock reentrantLock = new ReentrantLock();

    /**
     * 下单
     * @param productId
     * @return
     */
    @Override
    public Map<String, Object> getNativePay(Long productId) {
        // 1、 生成订单
        log.info("创建订单");
        OrderInfo orderInfo = orderInfoService.getOrderInfoByProductId(productId, PayType.WXPAY.getType());
        String codeUrl = orderInfo.getCodeUrl();
        if(orderInfo != null && StringUtils.isNotBlank(codeUrl)) {
            if (StringUtils.isNotEmpty(codeUrl)) {
                Map<String, Object> result = new HashMap<>();
                orderInfoService.saveCodeUrlById(orderInfo.getId(), codeUrl);
                result.put("codeUrl", codeUrl);
                result.put("orderNo", orderInfo.getOrderNo());
                return result;
            }
        }
        // 2、调用统一下单API
        HttpPost httpPost = new HttpPost("https://api.mch.weixin.qq.com/v3/pay/transactions/native");
        // 请求body参数
        Map paramMap = new HashMap();
        paramMap.put("appid", wxPayConfig.getAppid());
        paramMap.put("mchid", wxPayConfig.getMchId());
        paramMap.put("description", orderInfo.getTitle());
        paramMap.put("out_trade_no", orderInfo.getOrderNo());
        paramMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.NATIVE_NOTIFY.getType()));
        Map amountMap = new HashMap();
        amountMap.put("total", orderInfo.getTotalFee());
        amountMap.put("currency", "CNY");
        paramMap.put("amount", amountMap);

        Gson gson = new Gson();
        String jsonParam = gson.toJson(paramMap);

        StringEntity entity = new StringEntity(jsonParam,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = null;

        try {
            log.info("调用下单API");
            response = wxPayClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            String s = EntityUtils.toString(response.getEntity());
            if (statusCode == 200) { //处理成功
                log.info("success,return body = " + s);
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("success");
            } else {
                log.info("failed,resp code = " + statusCode+ ",return body = " + s);
                throw new IOException("request failed");
            }
            Map hashMap = gson.fromJson(s, HashMap.class);
            Map<String, Object> result = new HashMap<>();
            codeUrl = (String) hashMap.get("code_url");
            // 保存二维码
            orderInfoService.saveCodeUrlById(orderInfo.getId(), codeUrl);
            result.put("codeUrl" , codeUrl);
            result.put("orderNo" , orderInfo.getOrderNo());
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public void processOrder(String decryptData) {
        if(StringUtils.isBlank(decryptData)){
            throw new ParseException("内容为空");
        }
        Gson gson = new Gson();
        Map<String, Object> resultInfo = gson.fromJson(decryptData, HashMap.class);
        String outTradeNo = (String) resultInfo.get("out_trade_no");
        String stateDesc = (String) resultInfo.get("trade_state_desc");
        if(reentrantLock.tryLock()){
            try {
                // 查看订单状态
                String orderStatus = orderInfoService.getOrderStatusByOrderNo(outTradeNo);
                if(! OrderStatus.NOTPAY.equals(orderStatus)){
                    return;
                }
                // 更新订单状态
                orderInfoService.updateOrderStatusByOrderNo(outTradeNo, stateDesc);
                // 记录日志
                paymentInfoService.savePaymentInfo(resultInfo);
            } finally {
                reentrantLock.unlock();
            }
        }
    }

    @Override
    public void cancelOrder(String orderNo) {
        // 调用API取消订单
        this.wxCancelOrder(orderNo);
        // 修改订单状态
        orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.CANCEL.getType());
    }

    @Override
    public String selectOrder(String orderNo) {
        String path = String.format(WxApiType.ORDER_QUERY_BY_NO.getType(),orderNo);
        HttpGet httpGet = new HttpGet(wxPayConfig.getDomain().concat(path).concat("?mchid=" + wxPayConfig.getMchId()));
        httpGet.setHeader("Accept", "application/json");
        CloseableHttpResponse response = null;
        try {
            response = wxPayClient.execute(httpGet);
            String body = EntityUtils.toString(response.getEntity());
            log.info("查询订单信息===> {}", body);
            return body;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void checkOrderStatus(String orderNo) {

        String result = selectOrder(orderNo);
        if(StringUtils.isBlank(result)){
            log.info("未查询到订单信息！");
            return;
        }
        Gson gson = new Gson();
        Map<String, Object> hashMap = gson.fromJson(result, HashMap.class);
        String tradeState = (String) hashMap.get("trade_state");
        if(WxTradeState.SUCCESS.getType().equals(tradeState)){
            log.info("核实订单已支付 ===> {}",orderNo);
            // 更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo,OrderStatus.SUCCESS.getType());
            // 记录支付日志
            paymentInfoService.savePaymentInfo(hashMap);
        }
        if(WxTradeState.NOTPAY.getType().equals(tradeState)){
            cancelOrder(orderNo);
            // 更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.CLOSED.getType());
        }

    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void refunds(String orderNo, String reason) {
        // 1、生成退单记录
        RefundInfo refundInfo = refundInfoService.createRefundInfo(orderNo, reason);
        // 2、调用API发起退款申请
        log.info("调用退款API");

        //调用统一下单API
        String url = wxPayConfig.getDomain().concat(WxApiType.DOMESTIC_REFUNDS.getType());
        HttpPost httpPost = new HttpPost(url);

        // 请求body参数
        Gson gson = new Gson();
        Map paramsMap = new HashMap();
        paramsMap.put("out_trade_no", orderNo);//订单编号
        paramsMap.put("out_refund_no", refundInfo.getRefundNo());//退款单编号
        paramsMap.put("reason",reason);//退款原因
        paramsMap.put("notify_url", wxPayConfig.getNotifyDomain().concat(WxNotifyType.REFUND_NOTIFY.getType()));//退款通知地址

        Map amountMap = new HashMap();
        amountMap.put("refund", refundInfo.getRefund());//退款金额
        amountMap.put("total", refundInfo.getTotalFee());//原订单金额
        amountMap.put("currency", "CNY");//退款币种
        paramsMap.put("amount", amountMap);

        //将参数转换成json字符串
        String jsonParams = gson.toJson(paramsMap);
        log.info("请求参数 ===> {}" + jsonParams);

        StringEntity entity = new StringEntity(jsonParams,"utf-8");
        entity.setContentType("application/json");//设置请求报文格式
        httpPost.setEntity(entity);//将请求报文放入请求对象
        httpPost.setHeader("Accept", "application/json");//设置响应报文格式

        //完成签名并执行请求，并完成验签
        CloseableHttpResponse response = null;
        try {
            response = wxPayClient.execute(httpPost);
            //解析响应结果
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("退款异常, 响应码 = " + statusCode+ ", 退款返回结果 = " + bodyAsString);
            }

            //更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_PROCESSING.getType());

            //更新退款单
            refundInfoService.updateRefund(bodyAsString);
        }catch (Exception e){

        }finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void processRefund(String decryptData) {
        log.info("退款单");

        //解密报文
        // String plainText = decryptFromResource(bodyMap);

        //将明文转换成map
        Gson gson = new Gson();
        HashMap plainTextMap = gson.fromJson(decryptData, HashMap.class);
        if(plainTextMap == null){
            return;
        }
        String orderNo = (String)plainTextMap.get("out_trade_no");

        if(reentrantLock.tryLock()){
            try {

                String orderStatus = orderInfoService.getOrderStatusByOrderNo(orderNo);
                if (!OrderStatus.REFUND_PROCESSING.getType().equals(orderStatus)) {
                    return;
                }

                //更新订单状态
                orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS.getType());

                //更新退款单
                refundInfoService.updateRefund(decryptData);

            } finally {
                //要主动释放锁
                reentrantLock.unlock();
            }
        }
    }

    @Override
    public String queryRefund(String refundNo) {
        log.info("查询退款接口调用 ===> {}", refundNo);

        String url =  String.format(WxApiType.DOMESTIC_REFUNDS_QUERY.getType(), refundNo);
        url = wxPayConfig.getDomain().concat(url);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");

        //完成签名并执行请求
        CloseableHttpResponse response = null;

        try {
            response = wxPayClient.execute(httpGet);
            String bodyAsString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 查询退款返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("查询退款异常, 响应码 = " + statusCode+ ", 查询退款返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public String queryBill(String billDate, String type) {
        log.warn("申请账单接口调用 {}", billDate);

        String url = "";
        if("tradebill".equals(type)){
            url =  WxApiType.TRADE_BILLS.getType();
        }else if("fundflowbill".equals(type)){
            url =  WxApiType.FUND_FLOW_BILLS.getType();
        }else{
            throw new RuntimeException("不支持的账单类型");
        }

        url = wxPayConfig.getDomain().concat(url).concat("?bill_date=").concat(billDate);

        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = null;

        try {
            response = wxPayClient.execute(httpGet);
            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 申请账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("申请账单异常, 响应码 = " + statusCode+ ", 申请账单返回结果 = " + bodyAsString);
            }

            //获取账单下载地址
            Gson gson = new Gson();
            Map<String, String> resultMap = gson.fromJson(bodyAsString, HashMap.class);
            return resultMap.get("download_url");

        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public String downloadBill(String billDate, String type) {
        log.warn("下载账单接口调用 {}, {}", billDate, type);

        //获取账单url地址
        String downloadUrl = this.queryBill(billDate, type);
        if(downloadUrl == null){
            return null;
        }
        //创建远程Get 请求对象
        HttpGet httpGet = new HttpGet(downloadUrl);
        httpGet.addHeader("Accept", "application/json");

        //使用wxPayClient发送请求得到响应
        CloseableHttpResponse response = null;

        try {
            response = wxPayNoSignClient.execute(httpGet);

            String bodyAsString = EntityUtils.toString(response.getEntity());

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                log.info("成功, 下载账单返回结果 = " + bodyAsString);
            } else if (statusCode == 204) {
                log.info("成功");
            } else {
                throw new RuntimeException("下载账单异常, 响应码 = " + statusCode+ ", 下载账单返回结果 = " + bodyAsString);
            }

            return bodyAsString;

        }catch (Exception e){

        }finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void checkRefundStatus(String refundNo) {
        log.warn("根据退款单号核实退款单状态 ===> {}", refundNo);

        //调用查询退款单接口
        String result = this.queryRefund(refundNo);

        //组装json请求体字符串
        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(result, HashMap.class);

        //获取微信支付端退款状态
        String status = resultMap.get("status");

        String orderNo = resultMap.get("out_trade_no");

        if (WxRefundStatus.SUCCESS.getType().equals(status)) {

            log.warn("核实订单已退款成功 ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_SUCCESS.getType());

            //更新退款单
            refundInfoService.updateRefund(result);
        }

        if (WxRefundStatus.ABNORMAL.getType().equals(status)) {

            log.warn("核实订单退款异常  ===> {}", refundNo);

            //如果确认退款成功，则更新订单状态
            orderInfoService.updateOrderStatusByOrderNo(orderNo, OrderStatus.REFUND_ABNORMAL.getType());

            //更新退款单
            refundInfoService.updateRefund(result);
        }
    }

    private void wxCancelOrder(String orderNo) {
        String path = String.format(WxApiType.CLOSE_ORDER_BY_NO.getType(),orderNo);
        HttpPost httpPost = new HttpPost(wxPayConfig.getDomain().concat(path));
        Map<String, Object> hashMap = new HashMap<>();
        hashMap.put("mchid", wxPayConfig.getMchId());
        Gson gson = new Gson();
        String jsonParam = gson.toJson(hashMap);
        StringEntity entity = new StringEntity(jsonParam,"utf-8");
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        CloseableHttpResponse response = null;
        try {
            log.info("调用关单API===> {}", jsonParam);
            response = wxPayClient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) { //处理成功
                log.info("success");
            } else if (statusCode == 204) { //处理成功，无返回Body
                log.info("success");
            } else {
                log.info("failed,resp code = " + statusCode);
                throw new IOException("request failed");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                response.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 解密
     * @param bodMap
     * @return
     */
    private String decryptFromResource(Map<String, Object> bodMap){
        return "";
    }
}
