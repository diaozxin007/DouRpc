![](https://ws3.sinaimg.cn/large/006tNc79ly1fvngahcinoj31kw0w07wi.jpg)

[原文链接](https://www.xilidou.com/2018/09/26/dourpc-remoting/)

微服务已经是每个互联网开发者必须掌握的一项技术。而 RPC 框架，是构成微服务最重要的组成部分之一。趁最近有时间。又看了看 dubbo 的源码。dubbo 为了做到灵活和解耦，使用了大量的设计模式和 SPI机制，要看懂 dubbo 的代码也不太容易。

按照《徒手撸框架》系列文章的套路，我还是会极简的实现一个 RPC 框架。帮助大家理解 RPC 框架的原理。

广义的来讲一个完整的 RPC 包含了很多组件，包括服务发现，服务治理，远程调用，调用链分析，网关等等。我将会慢慢的实现这些功能，这篇文章主要先讲解的是 RPC 的基石，**远程调用** 的实现。

相信，读完这篇文章你也一定可以自己实现一个可以提供 RPC 调用的框架。

<!--more-->

# 1. RPC 的调用过程

通过下图我们来了解一下 RPC 的调用过程，从宏观上来看看到底一次 RPC 调用经过些什么过程。

当一次调用开始：

![](https://ws3.sinaimg.cn/large/006tNc79ly1fvng3adrhuj30o40im0to.jpg)

1. client 会调用本地动态代理 proxy 
2. 这个代理会将调用通过协议转序列化字节流
3. 通过 netty 网络框架，将字节流发送到服务端
4. 服务端在受到这个字节流后，会根据协议，反序列化为原始的调用，利用反射原理调用服务方提供的方法
5. 如果请求有返回值，又需要把结果根据协议序列化后，再通过 netty 返回给调用方

# 2. 框架概览和技术选型

看一看框架的组件:

![](https://ws4.sinaimg.cn/large/006tNc79ly1fvng3yww6lj30bc0h03zr.jpg)

`clinet`就是调用方。`servive`是服务的提供者。`protocol`包定义了通信协议。`common`包含了通用的一些逻辑组件。

技术选型项目使用 `maven` 作为包管理工具，`json` 作为序列化协议，使用`spring boot`管理对象的生命周期，`netty` 作为 `nio` 的网路组件。所以要阅读这篇文章，你需要对`spring boot`和`netty`有基本的了解。

下面就看看每个组件的具体实现：

# 3. protocol

其实作为 RPC 的协议，只需要考虑一个问题，就是怎么把一次本地方法的调用，变成能够被网络传输的字节流。

我们需要定义方法的调用和返回两个对象实体：

请求：
```java
@Data
public class RpcRequest {
    // 调用编号
    private String requestId;
    // 类名
    private String className;
    // 方法名
    private String methodName;
    // 请求参数的数据类型
    private Class<?>[] parameterTypes;
    // 请求的参数
    private Object[] parameters;
}
```

响应：
```java
@Data
public class RpcResponse {
    // 调用编号
    private String requestId;
    // 抛出的异常
    private Throwable throwable;
    // 返回结果
    private Object result;

}
```

确定了需要序列化的对象实体，就要确定序列化的协议，实现两个方法，序列化和反序列化。

```java
public interface Serialization {
    <T> byte[] serialize(T obj);
    <T> T deSerialize(byte[] data,Class<T> clz);
}
```

可选用的序列化的协议很多，比如：
* jdk 的序列化方法。（不推荐，不利于之后的跨语言调用）
* json 可读性强，但是序列化速度慢，体积大。
* protobuf，kyro，Hessian 等都是优秀的序列化框架，也可按需选择。

为了简单和便于调试，我们就选择 json 作为序列化协议，使用`jackson`作为 json 解析框架。

```java
/**
 * @author Zhengxin
 */
public class JsonSerialization implements Serialization {

    private ObjectMapper objectMapper;

    public JsonSerialization(){
        this.objectMapper = new ObjectMapper();
    }


    @Override
    public <T> byte[] serialize(T obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public <T> T deSerialize(byte[] data, Class<T> clz) {
        try {
            return objectMapper.readValue(data,clz);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
```

因为 netty 支持自定义 coder 。所以只需要实现 `ByteToMessageDecoder` 和 `MessageToByteEncoder` 两个接口。就解决了序列化的问题:

```java
public class RpcDecoder extends ByteToMessageDecoder {

    private Class<?> clz;
    private Serialization serialization;

    public RpcDecoder(Class<?> clz,Serialization serialization){
        this.clz = clz;
        this.serialization = serialization;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if(in.readableBytes() < 4){
            return;
        }

        in.markReaderIndex();
        int dataLength = in.readInt();
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        Object obj = serialization.deSerialize(data, clz);
        out.add(obj);
    }
}
```

```java
public class RpcEncoder extends MessageToByteEncoder {

    private Class<?> clz;
    private Serialization serialization;

    public RpcEncoder(Class<?> clz, Serialization serialization){
        this.clz = clz;
        this.serialization = serialization;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        if(clz != null){
            byte[] bytes = serialization.serialize(msg);
            out.writeInt(bytes.length);
            out.writeBytes(bytes);
        }
    }
}
```

至此，protocol 就实现了，我们就可以把方法的调用和结果的响应转换为一串可以在网络中传输的 byte[] 数组了。

# 4. server

server 是负责处理客户端请求的组件。在互联网高并发的环境下，使用 Nio 非阻塞的方式可以相对轻松的应付高并发的场景。netty 是一个优秀的 Nio 处理框架。Server 就基于 netty 进行开发。关键代码如下：

1. netty 是基于 Reacotr 模型的。所以需要初始化两组线程 boss 和 worker 。boss 负责分发请求，worker 负责执行相应的 handler：

```java
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
```

2. netty 的操作是基于 pipeline 的。所以我们需要把在 protocol 实现的几个 coder 注册到 netty 的 pipeline 中。

```java

        ChannelPipeline pipeline = ch.pipeline();
        // 处理 tcp 请求中粘包的 coder，具体作用可以自行 google
        pipeline.addLast(new LengthFieldBasedFrameDecoder(65535,0,4));

        // protocol 中实现的 序列化和反序列化 coder
        pipeline.addLast(new RpcEncoder(RpcResponse.class,new JsonSerialization()));
        pipeline.addLast(new RpcDecoder(RpcRequest.class,new JsonSerialization()));

        // 具体处理请求的 handler 下文具体解释
        pipeline.addLast(serverHandler);

```

3. 实现具体的 ServerHandler 用于处理真正的调用。

`ServerHandler` 继承 `SimpleChannelInboundHandler<RpcRequest>`。简单来说这个 `InboundHandler` 会在数据被接受时或者对于的 Channel 的状态发生变化的时候被调用。当这个 handler 读取数据的时候方法 `channelRead0()` 会被用，所以我们就重写这个方法就够了。

```java

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest msg) throws Exception {
        RpcResponse rpcResponse = new RpcResponse();
        rpcResponse.setRequestId(msg.getRequestId());
        try{
            // 收到请求后开始处理请求
            Object handler = handler(msg);
            rpcResponse.setResult(handler);
        }catch (Throwable throwable){
            // 如果抛出异常也将异常存入 response 中
            rpcResponse.setThrowable(throwable);
            throwable.printStackTrace();
        }
        // 操作完以后写入 netty 的上下文中。netty 自己处理返回值。
        ctx.writeAndFlush(rpcResponse);
    }

```

handler(msg) 实际上使用的是 cglib 的 Fastclass 实现的，其实根本原理，还是反射。学好 java 中的反射真的可以为所欲为。

```java
    private Object handler(RpcRequest request) throws Throwable {
        Class<?> clz = Class.forName(request.getClassName());
        Object serviceBean = applicationContext.getBean(clz);

        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();

        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        // 根本思路还是获取类名和方法名，利用反射实现调用
        FastClass fastClass = FastClass.create(serviceClass);
        FastMethod fastMethod = fastClass.getMethod(methodName,parameterTypes);

        // 实际调用发生的地方
        return fastMethod.invoke(serviceBean,parameters);
    }
```

总体上来看，server 的实现不是很困难。核心的知识点是 netty 的 channel 的使用和 cglib 的反射机制。

# 5. client 

## future
其实，对于我来说，client 的实现难度，远远大于 server 的实现。netty 是一个异步框架，所有的返回都是基于 Future 和 Callback 的机制。

所以在阅读以下文字前强烈推荐，我之前写的一篇文章 [Future 研究](https://www.xilidou.com/2017/10/24/Futuer%E7%A0%94%E7%A9%B6/)。利用经典的 wite 和 notify 机制，实现异步的获取请求结果。

```java
/**
 * @author zhengxin
 */
public class DefaultFuture {
	private RpcResponse rpcResponse;
	private volatile boolean isSucceed = false;
	private final Object object = new Object();
	public RpcResponse getResponse(int timeout){
		synchronized (object){
			while (!isSucceed){
				try {
                    //wait
					object.wait(timeout);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			return rpcResponse;
		}
	}

	public void setResponse(RpcResponse response){
		if(isSucceed){
			return;
		}
		synchronized (object) {
			this.rpcResponse = response;
			this.isSucceed = true;
            //notiy
			object.notify();
		}
	}
}


```

## 复用资源
为了能够提升 client 的吞吐量，可提供的思路有以下几种：
1. 使用对象池：建立多个 client 以后保存在对象池中。但是代码的复杂度和维护 client 的成本会很高。

2. 尽可能的复用 netty 中的 channel。
之前你可能注意到，为什么要在 RpcRequest 和 RpcResponse 中增加一个 ID。因为 netty 中的 channel 是会被多个线程使用的。当一个结果异步的返回后，你并不知道是哪个线程返回的。这个时候就可以考虑利用一个 Map，建立一个 ID 和 Future 映射。这样请求的线程只要使用对应的 ID 就能获取，相应的返回结果。

```java
/**
 * @author Zhengxin
 */
public class ClientHandler extends ChannelDuplexHandler {
    // 使用 map 维护 id 和 Future 的映射关系，在多线程环境下需要使用线程安全的容器
    private final Map<String, DefaultFuture> futureMap = new ConcurrentHashMap<>();
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if(msg instanceof RpcRequest){
            RpcRequest request = (RpcRequest) msg;
            // 写数据的时候，增加映射
            futureMap.putIfAbsent(request.getRequestId(),new DefaultFuture());
        }
        super.write(ctx, msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(msg instanceof RpcResponse){
            RpcResponse response = (RpcResponse) msg;
            // 获取数据的时候 将结果放入 future 中
            DefaultFuture defaultFuture = futureMap.get(response.getRequestId());
            defaultFuture.setResponse(response);
        }
        super.channelRead(ctx, msg);
    }

    public RpcResponse getRpcResponse(String requestId){
        try {
            // 从 future 中获取真正的结果。
            DefaultFuture defaultFuture = futureMap.get(requestId);
            return defaultFuture.getResponse(10);
        }finally {
            // 完成后从 map 中移除。
            futureMap.remove(requestId);
        }


    }
}
```

这里没有继承 server 中的 `InboundHandler` 而使用了 `ChannelDuplexHandler`。顾名思义就是在写入和读取数据的时候，都会触发相应的方法。写入的时候在 Map 中保存 ID 和 Future。读到数据的时候从 Map 中取出 Future 并将结果放入  Future 中。获取结果的时候需要对应的 ID。

使用 `Transporters` 对请求进行封装。

```java
public class Transporters {
    public static RpcResponse send(RpcRequest request){
        NettyClient nettyClient = new NettyClient("127.0.0.1", 8080);
        nettyClient.connect(nettyClient.getInetSocketAddress());
        RpcResponse send = nettyClient.send(request);
        return send;
    }
}
```

## 动态代理的实现

动态代理技术最广为人知的应用，应该就是 Spring 的 Aop，面向切面的编程实现，动态的在原有方法Before 或者 After 添加代码。而 RPC 框架中动态代理的作用就是彻底替换原有方法，直接调用远程方法。

代理工厂类：
```java
public class ProxyFactory {
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass){
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new RpcInvoker<T>(interfaceClass)
        );
    }
}
```

当 proxyFactory 生成的类被调用的时候，就会执行 RpcInvoker 方法。
```java
public class RpcInvoker<T> implements InvocationHandler {
    private Class<T> clz;
    public RpcInvoker(Class<T> clz){
        this.clz = clz;
    }
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        RpcRequest request = new RpcRequest();

        String requestId = UUID.randomUUID().toString();

        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        Class<?>[] parameterTypes = method.getParameterTypes();

        request.setRequestId(requestId);
        request.setClassName(className);
        request.setMethodName(methodName);
        request.setParameterTypes(parameterTypes);
        request.setParameters(args);

        return Transporters.send(request).getResult();
    }
}
```

看到这个 invoke 方法，主要三个作用，
1. 生成 RequestId。
2. 拼装 RpcRequest。
3. 调用 Transports 发送请求，获取结果。

至此，整个调用链完整了。我们终于完成了一次 RPC 调用。

## 与 Spring 集成

为了使我们的 client 能够易于使用我们需要考虑，定义一个自定义注解 `@RpcInterface` 当我们的项目接入 Spring 以后，Spring 扫描到这个注解之后，自动的通过我们的 ProxyFactory 创建代理对象，并存放在 spring 的 applicationContext 中。这样我们就可以通过 `@Autowired` 注解直接注入使用了。

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RpcInterface {
}
```

```java
@Configuration
@Slf4j
public class RpcConfig implements ApplicationContextAware,InitializingBean {
	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Reflections reflections = new Reflections("com.xilidou");
		DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        // 获取 @RpcInterfac 标注的接口
		Set<Class<?>> typesAnnotatedWith = reflections.getTypesAnnotatedWith(RpcInterface.class);
		for (Class<?> aClass : typesAnnotatedWith) {
            // 创建代理对象，并注册到 spring 上下文。
			beanFactory.registerSingleton(aClass.getSimpleName(),ProxyFactory.create(aClass));
		}
		log.info("afterPropertiesSet is {}",typesAnnotatedWith);
	}
}
```

终于我们最简单的 RPC 框架就开发完了。下面可以测试一下。

# 6. Demo

## api

```java
@RpcInterface
public interface IHelloService {
    String sayHi(String name);
}

```

## server

IHelloSerivce 的实现：

```java
@Service
@Slf4j
public class TestServiceImpl implements IHelloService {

    @Override
    public String sayHi(String name) {
        log.info(name);
        return "Hello " + name;
    }
}
```

启动服务：

```java
@SpringBootApplication
public class Application {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context = SpringApplication.run(Application.class);
        TcpService tcpService = context.getBean(TcpService.class);
        tcpService.start();
    }
}
````


## client

```java
@SpringBootApplication()
public class ClientApplication {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ClientApplication.class);
	    IHelloService helloService = context.getBean(IHelloService.class);
        System.out.println(helloService.sayHi("doudou"));
    }
}
```

运行以后输出的结果：

> Hello doudou

# 总结

终于我们实现了一个最简版的 RPC 远程调用的模块。只是包含最最基础的远程调用功能。

如果你对这个项目感兴趣，欢迎你与我联系，为这个框架贡献代码。

老规矩 Github 地址：[DouPpc](https://github.com/diaozxin007/DouRpc)

徒手撸框架系列文章地址：

[徒手撸框架--实现IoC](https://www.xilidou.com/2018/01/08/spring-ioc/)

[徒手撸框架--实现Aop](https://www.xilidou.com/2018/01/13/spring-aop/)

[徒手撸框架--高并发环境下的请求合并](https://www.xilidou.com/2018/01/22/merge-request/)


欢迎关注我的微信公众号

![二维码](https://ws3.sinaimg.cn/large/006tNc79ly1fo3oqp60v3j307607674r.jpg)