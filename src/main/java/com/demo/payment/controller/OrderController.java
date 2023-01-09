package com.demo.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.demo.payment.entity.OrderInfo;
import com.demo.payment.enums.OrderStatus;
import com.demo.payment.service.OrderInfoService;
import com.demo.payment.util.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@CrossOrigin
@RestController
@RequestMapping("api/order-info")
@Api(tags = "订单信息")
public class OrderController {

    @Resource
    private OrderInfoService orderInfoService;

    @ApiOperation("订单列表")
    @GetMapping("list")
    public AjaxResult getOrderList(){
        List<OrderInfo> list = orderInfoService.getOrderList();
        Map<String, List<OrderInfo>> result = new HashMap<>();
        result.put("list", list);
        return AjaxResult.success(result);
    }

    @ApiOperation("查询订单状态")
    @GetMapping("query-order-status/{orderNo}")
    public AjaxResult getOrderStatus(@PathVariable String orderNo){
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo info = orderInfoService.getOne(queryWrapper);
        if (info !=null && OrderStatus.SUCCESS.getType().equals(info.getOrderStatus())){
            return AjaxResult.success("支付成功");
        }
        return new AjaxResult(101,"支付中...");
    }

}
