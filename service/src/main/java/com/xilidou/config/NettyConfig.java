package com.xilidou.config;


import com.xilidou.netty.ChannelRepository;
import com.xilidou.netty.handler.ServerInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Zhengxin
 */
@Configuration
@EnableConfigurationProperties(NettyProperties.class)
public class NettyConfig {

    @Autowired
    private NettyProperties nettyProperties;

    @Autowired
    private ServerInitializer serverInitializer;

    @Bean
    public ServerBootstrap serverBootstrap() throws InterruptedException {

        ServerBootstrap serverBootstrap = new ServerBootstrap();

        serverBootstrap.group(bossGroup(), workerGroup())
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.DEBUG))
                .childHandler(serverInitializer);

        Map<ChannelOption<?>, Object> tcpChannelOptions = tcpChannelOptions();
        Set<ChannelOption<?>> keySet = tcpChannelOptions.keySet();
        for (@SuppressWarnings("rawtypes") ChannelOption option : keySet) {
            serverBootstrap.option(option, tcpChannelOptions.get(option));
        }

        return serverBootstrap;
    }

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup bossGroup(){
        return new NioEventLoopGroup();
    }

    @Bean(destroyMethod = "shutdownGracefully")
    public NioEventLoopGroup workerGroup(){
        return new NioEventLoopGroup();
    }

    @Bean
    public Map<ChannelOption<?>, Object> tcpChannelOptions() {
        Map<ChannelOption<?>, Object> options = new HashMap<>();
        options.put(ChannelOption.SO_BACKLOG, nettyProperties.getBacklog());
        return options;
    }

    @Bean
    public InetSocketAddress tcpSocketAddress() {
        return new InetSocketAddress(nettyProperties.getTcpPort());
    }

    @Bean
    public ChannelRepository channelRepository() {
        return new ChannelRepository();
    }


}
