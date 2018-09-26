package com.xilidou;

import com.xilidou.entity.RpcRequest;
import com.xilidou.entity.RpcResponse;
import com.xilidou.netty.NettyClient;

import java.net.InetSocketAddress;

public class Transporters {

    public static RpcResponse send(RpcRequest request){

        NettyClient nettyClient = new NettyClient("127.0.0.1", 8080);

        nettyClient.connect(nettyClient.getInetSocketAddress());

        RpcResponse send = nettyClient.send(request);

        return send;

    }

}
