package com.xilidou;

import com.xilidou.api.IHelloService;
import com.xilidou.proxy.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication()
public class ClientApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = SpringApplication.run(ClientApplication.class);
	    IHelloService helloService = context.getBean(IHelloService.class);
        System.out.println(helloService.sayHi("doudou"));
    }

}
