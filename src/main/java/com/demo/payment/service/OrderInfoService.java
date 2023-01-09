package com.demo.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.payment.entity.OrderInfo;

import java.util.List;

public interface OrderInfoService extends IService<OrderInfo> {

    OrderInfo getOrderInfoByProductId(Long productId);

    void saveCodeUrlById(String id, String codeUrl);

    List<OrderInfo> getOrderList();

    void updateOrderStatusByOrderNo(String outTradeNo, String stateDesc);

    String getOrderStatusByOrderNo(String outTradeNo);

    List<OrderInfo> getNoPayOrderByDuration(Integer durationMin);

    OrderInfo getOrderInfoByOrderNo(String orderNo);
}
