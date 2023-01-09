package com.demo.payment.config;

import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.Verifier;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.cert.CertificatesManager;
import com.wechat.pay.contrib.apache.httpclient.exception.HttpCodeException;
import com.wechat.pay.contrib.apache.httpclient.exception.NotFoundException;
import com.wechat.pay.contrib.apache.httpclient.exception.ParseException;
import com.wechat.pay.contrib.apache.httpclient.exception.ValidationException;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import sun.security.util.Pem;

import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;

import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.*;
import static com.wechat.pay.contrib.apache.httpclient.constant.WechatPayHttpHeaders.WECHAT_PAY_SIGNATURE;

@Slf4j
@Configuration
@PropertySource("classpath:wxpay.properties") //读取配置文件
@ConfigurationProperties(prefix="wxpay") //读取wxpay节点
@Data //使用set方法将wxpay节点中的值填充到当前类的属性中
public class WxPayConfig {

    // 商户号
    private String mchId;

    // 商户API证书序列号
    private String mchSerialNo;

    // 商户私钥文件
    private String privateKeyPath;

    // APIv3密钥
    private String apiV3Key;

    // APPID
    private String appid;

    // 微信服务器地址
    private String domain;

    // 接收结果通知地址
    private String notifyDomain;

    private PrivateKey getPrivateKey(String fileName){
        try {
            return PemUtil.loadPrivateKey(
                    new FileInputStream(fileName));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("未找到私钥文件",e);
        }
    }

    @Bean
    public Verifier getVerifier() throws HttpCodeException, GeneralSecurityException, IOException, NotFoundException {
        PrivateKey merchantPrivateKey = getPrivateKey(privateKeyPath);

        // 获取证书管理器实例
        CertificatesManager certificatesManager = CertificatesManager.getInstance();
        // 向证书管理器增加需要自动更新平台证书的商户信息
        certificatesManager.putMerchant(mchId, new WechatPay2Credentials(mchId,
                new PrivateKeySigner(mchSerialNo, merchantPrivateKey)), apiV3Key.getBytes(StandardCharsets.UTF_8));
        // ... 若有多个商户号，可继续调用putMerchant添加商户信息

        // 从证书管理器中获取verifier
        return certificatesManager.getVerifier(mchId);
    }

    @Bean("wxPayClient")
    public CloseableHttpClient getWxPayClient() throws GeneralSecurityException, IOException, NotFoundException, HttpCodeException {
        PrivateKey merchantPrivateKey = getPrivateKey(privateKeyPath);

        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, mchSerialNo, merchantPrivateKey)
                .withValidator(new WechatPay2Validator(getVerifier()));
        // ... 接下来，你仍然可以通过builder设置各种参数，来配置你的HttpClient

        // 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签，并进行证书自动更新
        return builder.build();
    }

    /**
     * 获取HttpClient，无需进行应答签名验证，跳过验签的流程
     */
    @Bean(name = "wxPayNoSignClient")
    public CloseableHttpClient getWxPayNoSignClient(){

        //获取商户私钥
        PrivateKey privateKey = getPrivateKey(privateKeyPath);

        //用于构造HttpClient
        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                //设置商户信息
                .withMerchant(mchId, mchSerialNo, privateKey)
                //无需进行签名验证、通过withValidator((response) -> true)实现
                .withValidator((response) -> true);

        // 通过WechatPayHttpClientBuilder构造的HttpClient，会自动的处理签名和验签，并进行证书自动更新
        CloseableHttpClient httpClient = builder.build();

        log.info("== getWxPayNoSignClient END ==");

        return httpClient;
    }


    public String getDecryptData(HttpServletRequest request, String body) throws ValidationException, ParseException, GeneralSecurityException, IOException, NotFoundException, HttpCodeException {
        String wechatPaySerial = request.getHeader(WECHAT_PAY_SERIAL);
        String nonce = request.getHeader(WECHAT_PAY_NONCE);
        String timestamp = request.getHeader(WECHAT_PAY_TIMESTAMP);
        String signature = request.getHeader(WECHAT_PAY_SIGNATURE);
        NotificationRequest notificationRequest = getNotificationRequest(body, wechatPaySerial, nonce, timestamp, signature);
        NotificationHandler handler = new NotificationHandler(getVerifier(), apiV3Key.getBytes(StandardCharsets.UTF_8));
        // 验签和解析请求体
        Notification notification = handler.parse(notificationRequest);
        // 从notification中获取解密报文
        return notification.getDecryptData();
    }

    /**
     * 构建request
     * @param body
     * @param wechatPaySerial
     * @param nonce
     * @param timestamp
     * @param signature
     * @return
     */
    private NotificationRequest getNotificationRequest(String body, String wechatPaySerial, String nonce, String timestamp, String signature) {
        // 构建request，传入必要参数
        return new NotificationRequest.Builder().withSerialNumber(wechatPaySerial)
                .withNonce(nonce)
                .withTimestamp(timestamp)
                .withSignature(signature)
                .withBody(body)
                .build();
    }


}
