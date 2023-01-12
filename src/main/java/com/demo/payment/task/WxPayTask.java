package com.demo.payment.task;

import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.RefundInfo;
import com.demo.payment.enums.PayType;
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
public class WxPayTask {

    private final Integer DURATION_MIN = 30;

    @Resource
    private OrderInfoService orderInfoService;

    @Resource
    private RefundInfoService refundInfoService;

    @Resource
    private WxPayService wxPayService;

    // @Scheduled(cron = "*/30 * * * * ?")
    public void orderConfirm(){
        log.info("定时查单任务");
        List<OrderInfo> list = orderInfoService.getNoPayOrderByDuration(DURATION_MIN, PayType.WXPAY.getType());

        for (OrderInfo orderInfo : list) {
            String orderNo = orderInfo.getOrderNo();
            log.warn("超时订单号===>{}", orderNo);
            // 调用微信API 核实订单状态
            wxPayService.checkOrderStatus(orderNo);
        }

    }

    /**
     * 从第0秒开始每隔30秒执行1次，查询创建超过5分钟，并且未成功的退款单
     */
    // @Scheduled(cron = "0/30 * * * * ?")
    public void refundConfirm() throws Exception {
        log.info("refundConfirm 被执行......");

        //找出申请退款超过5分钟并且未成功的退款单
        List<RefundInfo> refundInfoList = refundInfoService.getNoRefundOrderByDuration(1);

        for (RefundInfo refundInfo : refundInfoList) {
            String refundNo = refundInfo.getRefundNo();
            log.warn("超时未退款的退款单号 ===> {}", refundNo);

            //核实订单状态：调用微信支付查询退款接口
            wxPayService.checkRefundStatus(refundNo);
        }
    }

}
