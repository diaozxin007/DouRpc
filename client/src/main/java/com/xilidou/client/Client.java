package com.xilidou.client;

import com.xilidou.entity.RpcRequest;
import com.xilidou.entity.RpcResponse;

import java.net.InetSocketAddress;

public interface Client {

    RpcResponse send(RpcRequest request);

    void connect(InetSocketAddress inetSocketAddress);

    InetSocketAddress getInetSocketAddress();

    void close();

}
