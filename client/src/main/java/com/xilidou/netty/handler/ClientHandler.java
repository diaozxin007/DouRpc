package com.xilidou.netty.handler;

import com.xilidou.entity.RpcRequest;
import com.xilidou.entity.RpcResponse;
import io.netty.channel.*;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Zhengxin
 */
public class ClientHandler extends ChannelDuplexHandler {

    private final Map<String,RpcResponse> responseMap = new ConcurrentHashMap<>();

    private Object object = new Object();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof RpcResponse){
            RpcResponse response = (RpcResponse) msg;
            responseMap.putIfAbsent(response.getRequestId(),response);
            synchronized (object){
                object.notify();
            }
        }
        super.channelRead(ctx, msg);
    }

    public RpcResponse getRpcResponse(String requestId){

        synchronized (object){
            try {
                object.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return responseMap.get(requestId);
    }
}


