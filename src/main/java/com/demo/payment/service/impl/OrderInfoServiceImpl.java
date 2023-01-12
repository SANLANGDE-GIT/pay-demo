package com.demo.payment.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.entity.Product;
import com.demo.payment.enums.OrderStatus;
import com.demo.payment.mapper.OrderInfoMapper;
import com.demo.payment.mapper.ProductMapper;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Resource
    private ProductMapper productMapper;

    /**
     * 根据产品ID生成订单信息
     * @param productId 产品ID
     * @param paymentType
     * @return obj
     */
    @Override
    public OrderInfo getOrderInfoByProductId(Long productId, String paymentType){

        OrderInfo orderInfo = this.getNoPayOrderByProductId(productId, paymentType);
        if (orderInfo != null){
            return orderInfo;
        }
        Product product = productMapper.selectById(productId);
        orderInfo = new OrderInfo();
        orderInfo.setTitle(product.getTitle());
        orderInfo.setOrderNo(OrderNoUtils.getOrderNo());
        orderInfo.setProductId(productId);
        orderInfo.setTotalFee(product.getPrice());
        orderInfo.setOrderStatus(OrderStatus.NOTPAY.getType());
        orderInfo.setPaymentType(paymentType);
        baseMapper.insert(orderInfo);
        return orderInfo;
    }

    /**
     * 保存支付二维码
     * @param id 标识
     * @param codeUrl 连接
     */
    @Override
    public void saveCodeUrlById(String id, String codeUrl) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(id);
        orderInfo.setCodeUrl(codeUrl);
        baseMapper.updateById(orderInfo);
    }

    /**
     * 获取订单列表
     * @return list
     */
    @Override
    public List<OrderInfo> getOrderList() {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        return baseMapper.selectList(queryWrapper.orderByDesc(OrderInfo::getId));
    }

    /**
     * 修改订单状态
     * @param outTradeNo 订单号
     * @param stateDesc 状态
     */
    @Override
    public void updateOrderStatusByOrderNo(String outTradeNo, String stateDesc) {
        LambdaQueryWrapper<OrderInfo> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(OrderInfo::getOrderNo, outTradeNo);
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderStatus(stateDesc);
        this.baseMapper.update(orderInfo,updateWrapper);
    }

    /**
     * 查询订单状态
     * @param outTradeNo 订单号
     * @return string
     */
    @Override
    public String getOrderStatusByOrderNo(String outTradeNo) {
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderInfo::getOrderNo, outTradeNo);
        OrderInfo orderInfo = baseMapper.selectOne(lambdaQueryWrapper);
        if(orderInfo == null){
            return null;
        }
        return orderInfo.getOrderStatus();
    }

    /**
     * 超时未支付订单
     * @param durationMin 超时时间
     * @return list
     */
    @Override
    public List<OrderInfo> getNoPayOrderByDuration(Integer durationMin, String paymentType) {
        Instant instant = Instant.now().minus(Duration.ofMinutes(durationMin));
        LambdaQueryWrapper<OrderInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(OrderInfo::getOrderStatus, OrderStatus.NOTPAY.getType())
                .le(OrderInfo::getCreateTime, instant).eq(OrderInfo::getPaymentType, paymentType);

        return baseMapper.selectList(lambdaQueryWrapper);
    }

    @Override
    public OrderInfo getOrderInfoByOrderNo(String orderNo) {
        return baseMapper.selectOne(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getOrderNo,orderNo));
    }

    /**
     * 获取未支付订单
     * TODO：匹配用户自己订单
     * @param productId
     * @param paymentType
     * @return
     */
    private OrderInfo getNoPayOrderByProductId(Long productId, String paymentType) {
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(productId !=null ,OrderInfo::getProductId, productId)
                .eq(OrderInfo::getOrderStatus, OrderStatus.NOTPAY.getType())
                .eq(OrderInfo::getPaymentType, paymentType);
        return baseMapper.selectOne(queryWrapper);
    }

}
