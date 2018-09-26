package com.xilidou.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import lombok.Data;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;

/**
 * @author Zhengxin
 */

@Data
@Component
public class TcpService {

    private final ServerBootstrap serverBootstrap;

    private final InetSocketAddress tcpPort;

    public TcpService(ServerBootstrap serverBootstrap,InetSocketAddress inetSocketAddress){
        this.serverBootstrap = serverBootstrap;
        this.tcpPort = inetSocketAddress;
    }

    private Channel serverChannel;

    public void start() throws InterruptedException {

        serverChannel = serverBootstrap.bind(tcpPort).sync().channel().closeFuture().channel();

    }

    @PreDestroy
    public void stop(){
        if(serverChannel != null){
            serverChannel.close();
            serverChannel.parent().close();
        }
    }

}
