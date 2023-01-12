package com.demo.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.RefundInfo;
import com.demo.payment.enums.OrderStatus;
import com.demo.payment.enums.wxpay.WxRefundStatus;
import com.demo.payment.mapper.RefundInfoMapper;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.service.RefundInfoService;
import com.demo.payment.util.OrderNoUtils;
import com.google.gson.Gson;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundInfoServiceImpl extends ServiceImpl<RefundInfoMapper, RefundInfo> implements RefundInfoService {

    @Resource
    private OrderInfoService orderInfoService;

    /**
     * 根据订单号 创建退款单
     * @param orderNo
     * @param reason
     * @return
     */
    @Override
    public RefundInfo createRefundInfo(String orderNo, String reason) {
        OrderInfo orderInfo = orderInfoService.getOrderInfoByOrderNo(orderNo);
        RefundInfo refundInfo = new RefundInfo();
        refundInfo.setOrderNo(orderInfo.getOrderNo());
        refundInfo.setRefundNo(OrderNoUtils.getRefundNo());
        refundInfo.setTotalFee(orderInfo.getTotalFee());
        refundInfo.setRefund(orderInfo.getTotalFee());
        refundInfo.setReason(reason);
        baseMapper.insert(refundInfo);

        return refundInfo;
    }

    /**
     * 修改退款记录 微信
     * @param content
     */
    @Override
    public void updateRefund(String content) {

        //将json字符串转换成Map
        Gson gson = new Gson();
        Map<String, String> resultMap = gson.fromJson(content, HashMap.class);

        //根据退款单编号修改退款单
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", resultMap.get("out_refund_no"));

        //设置要修改的字段
        RefundInfo refundInfo = new RefundInfo();

        refundInfo.setRefundId(resultMap.get("refund_id"));//微信支付退款单号

        //查询退款和申请退款中的返回参数
        if(resultMap.get("status") != null){
            refundInfo.setRefundStatus(resultMap.get("status"));//退款状态
            refundInfo.setContentReturn(content);//将全部响应结果存入数据库的content字段
        }
        //退款回调中的回调参数
        if(resultMap.get("refund_status") != null){
            refundInfo.setRefundStatus(resultMap.get("refund_status"));//退款状态
            refundInfo.setContentNotify(content);//将全部响应结果存入数据库的content字段
        }

        //更新退款单
        baseMapper.update(refundInfo, queryWrapper);
    }

    /**
     * 获取退款单列表
     * @param min
     * @return
     */
    @Override
    public List<RefundInfo> getNoRefundOrderByDuration(int min) {
        //minutes分钟之前的时间
        Instant instant = Instant.now().minus(Duration.ofMinutes(min));
        LambdaQueryWrapper<RefundInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(RefundInfo::getRefundStatus, WxRefundStatus.PROCESSING.getType())
                .le(RefundInfo::getCreateTime, instant);
        return baseMapper.selectList(queryWrapper);
    }

    /**
     * 修改退款记录 支付宝
     * @param refundNo
     * @param reason
     * @param type
     */
    @Override
    public void updateRefundForAliPay(String refundNo, String reason, String type) {
        //根据退款单编号修改退款单
        QueryWrapper<RefundInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("refund_no", refundNo);

        //设置要修改的字段
        RefundInfo refundInfo = new RefundInfo();

        //查询退款和申请退款中的返回参数
        refundInfo.setRefundStatus(type);//退款状态
        refundInfo.setContentReturn(reason);//将全部响应结果存入数据库的content字段

        //更新退款单
        baseMapper.update(refundInfo, queryWrapper);
    }
}
