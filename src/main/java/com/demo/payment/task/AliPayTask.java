package com.demo.payment.task;

import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.RefundInfo;
import com.demo.payment.enums.PayType;
import com.demo.payment.service.AlipayService;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.service.RefundInfoService;
import com.demo.payment.service.WxPayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class AliPayTask {

    private final Integer DURATION_MIN = 1;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private RefundInfoService refundInfoService;

    @Resource
    private AlipayService alipayService;

//     @Scheduled(cron = "*/30 * * * * ?")
    public void orderConfirm(){
        log.info("定时查单任务");
        List<OrderInfo> list = orderInfoService.getNoPayOrderByDuration(DURATION_MIN, PayType.ALIPAY.getType());

        for (OrderInfo orderInfo : list) {
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单号===>{}", orderNo);
            // 调用AliAPI 核实订单状态
            alipayService.checkOrderStatus(orderNo);
        }

    }

}
