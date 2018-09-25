package com.xilidou.client;

import com.xilidou.api.IHelloService;
import com.xilidou.proxy.ProxyFactory;

public class test {

    public static void main(String[] args) {

        IHelloService helloService = ProxyFactory.create(IHelloService.class);
        String doudou = helloService.sayHi("doudou");

        System.out.println(doudou);
    }

}
