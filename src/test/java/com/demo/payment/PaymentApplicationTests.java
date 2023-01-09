package com.demo.payment;

import com.demo.payment.config.WxPayConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.security.PrivateKey;

@SpringBootTest
class PaymentApplicationTests {

    @Resource
    WxPayConfig wxPayConfig;

    @Test
    void contextLoads() {
    }

    /*@Test
    void getPrivateKey(){
        String privateKeyPath = wxPayConfig.getPrivateKeyPath();
        PrivateKey privateKey = wxPayConfig.getPrivateKey(privateKeyPath);
        System.out.println(privateKey);
    }*/

}
