package com.demo.payment.controller;

import com.demo.payment.util.AjaxResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = {"测试"})
@RestController
@RequestMapping("api/test")
public class TestController {

    @ApiOperation("测试")
    @GetMapping("Hello")
    public AjaxResult test(){
        return AjaxResult.success();
    }

}
