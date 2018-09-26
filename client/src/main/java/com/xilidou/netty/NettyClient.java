package com.xilidou.netty;

import com.xilidou.client.Client;
import com.xilidou.coder.RpcDecoder;
import com.xilidou.coder.RpcEncoder;
import com.xilidou.entity.RpcRequest; import com.xilidou.entity.RpcResponse;
import com.xilidou.netty.handler.ClientHandler;
import com.xilidou.serialization.JsonSerialization;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.net.InetSocketAddress;

/**
 * @author Zhengxin
 */
public class NettyClient implements Client {

    private EventLoopGroup eventLoopGroup;
    private Channel channel;
    private ClientHandler clientHandler;

    private String host;
    private int port;

    public NettyClient(String host, int port){
        this.port = port;
        this.host = host;

    }

    @Override
    public RpcResponse send(final RpcRequest request) {
        try {
            channel.writeAndFlush(request).await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return clientHandler.getRpcResponse(request.getRequestId());
    }

    @Override
    public void connect(final InetSocketAddress inetSocketAddress) {

        clientHandler = new ClientHandler();

        eventLoopGroup = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();

        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE,true)
                .option(ChannelOption.TCP_NODELAY,true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(65535,0,4));
                        pipeline.addLast(new RpcEncoder(RpcRequest.class,new JsonSerialization()));
                        pipeline.addLast(new RpcDecoder(RpcResponse.class,new JsonSerialization()));
                        pipeline.addLast(clientHandler);
                    }
                });

        try {
            channel = bootstrap.connect(inetSocketAddress).sync().channel();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InetSocketAddress getInetSocketAddress() {
        return new InetSocketAddress(host,port);
    }

    @Override
    public void close() {
        eventLoopGroup.shutdownGracefully();
        channel.closeFuture().syncUninterruptibly();
    }
}

