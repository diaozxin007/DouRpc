package com.xilidou.api;

import com.xilidou.annotation.RpcInterface;

@RpcInterface
public interface IHelloService {

    String sayHi(String name);

}
