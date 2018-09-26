package com.xilidou.coder.handler;

<<<<<<< HEAD:client/src/main/java/com/xilidou/coder/handler/ClientHandler.java
=======
import com.xilidou.DefaultFuture;
import com.xilidou.entity.RpcRequest;
>>>>>>> 0b54287355c29b96cd1f3fcbd49726c972b74f37:client/src/main/java/com/xilidou/netty/handler/ClientHandler.java
import com.xilidou.entity.RpcResponse;
import io.netty.channel.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Zhengxin
 */
public class ClientHandler extends ChannelDuplexHandler {

    private final Map<String, DefaultFuture> futureMap = new ConcurrentHashMap<>();


    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        if(msg instanceof RpcRequest){
            RpcRequest request = (RpcRequest) msg;
            futureMap.putIfAbsent(request.getRequestId(),new DefaultFuture());
        }

        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof RpcResponse){
            RpcResponse response = (RpcResponse) msg;
            DefaultFuture defaultFuture = futureMap.get(response.getRequestId());
            defaultFuture.setResponse(response);
        }
        super.channelRead(ctx, msg);
    }

    public RpcResponse getRpcResponse(String requestId){

        try {
            DefaultFuture defaultFuture = futureMap.get(requestId);
            return defaultFuture.getResponse(10);
        }finally {
            futureMap.remove(requestId);
        }


    }
}


