package com.demo.payment.controller;

import com.demo.payment.entity.Product;
import com.demo.payment.service.ProductService;
import com.demo.payment.util.AjaxResult;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/product")
@Api(tags = {"商品列表"})
@CrossOrigin
public class ProductController {

    @Resource
    private ProductService productService;

    @GetMapping("list")
    public AjaxResult getProductList(){
        Map<String, List<Product>> map = new HashMap<>();
        List<Product> list = productService.list();
        map.put("productList",list);
        return AjaxResult.success(map);
    }

}
