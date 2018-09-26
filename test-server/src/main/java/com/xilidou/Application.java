package com.xilidou;


import com.xilidou.netty.TcpService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @author Zhengxin
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) throws InterruptedException {

        ConfigurableApplicationContext context = SpringApplication.run(Application.class);


        TcpService tcpService = context.getBean(TcpService.class);
        tcpService.start();

    }

}
