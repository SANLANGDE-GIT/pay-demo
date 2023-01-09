package com.demo.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.payment.entity.RefundInfo;

import java.util.List;

public interface RefundInfoService extends IService<RefundInfo> {

    RefundInfo createRefundInfo(String orderNo, String reason);

    void updateRefund(String bodyAsString);

    List<RefundInfo> getNoRefundOrderByDuration(int min);
}
