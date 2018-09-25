package com.xilidou.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Zhengxin
 */
@Data
@ConfigurationProperties(prefix = "netty")
public class NettyProperties {

    private int tcpPort = 8080;

    private int bossCount = 4;

    private int workerCount = 16;

    private boolean keepAlive = true;

    private int backlog;

}
