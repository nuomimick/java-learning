# springboot集成rabbitmq

1. 加入依赖
```java
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```
2. 加入配置
```yaml
spring:
    rabbitmq:
        host: localhost
        port: 5672
        username: guest
        password: guest
```
3. 声明队列
```java
@Configuration
public class RabbitmqConfig {
    // 第一个队列
    @Bean
    public Queue queue1() {
        return new Queue("queue1");
    }
    // 第二个队列
    @Bean
    public Queue queue1() {
        return new Queue("queue1");
    }
}
```
4. 发布消息
```java
@RestController
public class SendController {
    @Autowired
    private AmqpTemplate amqpTemplate;

    @RequestMapping("/send")
    public String send(){
        String content = "Date:" + new Date();
        amqpTemplate.convertAndSend("queue1", content);
        return content;
    }
}
```
5. 消费消息
```java
@Component
@RabbitListener(queues = "queue1")
public class Receiver1 {
    // 第一个消费者
    @RabbitHandler
    public void receiver(String msg) {
        System.out.println("Receiver1:" + msg);
    }
}
@Component
@RabbitListener(queues = "queue2")
public class Receiver2 {
    // 第二个消费者
    @RabbitHandler
    public void receiver(String msg) {
        System.out.println("Receiver2:" + msg);
    }
}
```
5. 使用交换器
```java
@Configuration
public class RabbitmqTopicConfig {
    public static final String message = "topic.message";
    public static final String messages = "topic.messages";

    @Bean
    public Queue queueMessage() {
        return new Queue(RabbitmqTopicConfig.message);
    }

    @Bean
    public Queue queueMessages() {
        return new Queue(RabbitmqTopicConfig.messages);
    }
    // 声明交换器
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange("topic_exchange");
    }
    // 绑定队列到交换器, @Bean默认的id为方法名，如果只有一种类型的Bean，那么会按类型注入
    @Bean
    Binding bindingExchangeMessage(Queue queueMessage, TopicExchange exchange) {
        return BindingBuilder.bind(queueMessage).to(exchange).with("topic.message");
    }
    @Bean
    Binding bindingExchangeMessages(Queue queueMessages, TopicExchange exchange) {
        return BindingBuilder.bind(queueMessages).to(exchange).with("topic.#");
    }
}

// 发布消息，跟上述类似
@RequestMapping("/topicSend1")
public String  topicSend1() {
    String context = "my topic 1";
    System.out.println("发送者说 : " + context);
    this.amqpTemplate.convertAndSend("topic_exchange", "topic.message", context);
    return context;
}
// 消费消息，跟上述类似，不在重复
```
6. Rpc调用
```java
public class RPCServer {
    private static final String RPC_QUEUE_NAME = "rpc_queue";

    private static int fib(int n) {
        if (n == 0) return 0;
        if (n == 1) return 1;
        return fib(n - 1) + fib(n - 2);
    }

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(RPC_QUEUE_NAME, false, false, false, null);
            channel.queuePurge(RPC_QUEUE_NAME);

            channel.basicQos(1);

            System.out.println(" [x] Awaiting RPC requests");

            Object monitor = new Object();
 
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                        .Builder()
                        // 关联编号，在返回队列里如果看到了这个编号，就知道我们的任务处理完成了，如果收到的编号不认识，就可以安全的忽略
                        .correlationId(delivery.getProperties().getCorrelationId())
                        .build();

                String response = "";

                try {
                    String message = new String(delivery.getBody(), "UTF-8");
                    int n = Integer.parseInt(message);

                    System.out.println(" [.] fib(" + message + ")");
                    response += fib(n);
                } catch (RuntimeException e) {
                    System.out.println(" [.] " + e.toString());
                } finally {
                    channel.basicPublish("", delivery.getProperties().getReplyTo(), replyProps, response.getBytes("UTF-8"));
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    // RabbitMq consumer worker thread notifies the RPC server owner thread
                    synchronized (monitor) {
                        monitor.notify();
                    }
                }
            };

            channel.basicConsume(RPC_QUEUE_NAME, false, deliverCallback, (consumerTag -> {
            }));
            // Wait and be prepared to consume the message from RPC client.
            while (true) {
                synchronized (monitor) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}

public class RPCClient implements AutoCloseable {
    private Connection connection;
    private Channel channel;
    private String requestQueueName = "rpc_queue";

    public RPCClient() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");

        connection = factory.newConnection();
        channel = connection.createChannel();
    }

    public static void main(String[] argv) {
        try (RPCClient fibonacciRpc = new RPCClient()) {
            for (int i = 0; i < 100; i++) {
                String i_str = Integer.toString(i);
                System.out.println(" [x] Requesting fib(" + i_str + ")");
                String response = fibonacciRpc.call(i_str);
                System.out.println(" [.] Got '" + response + "'");
            }
        } catch (IOException | TimeoutException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String call(String message) throws IOException, InterruptedException {
        final String corrId = UUID.randomUUID().toString();

        String replyQueueName = channel.queueDeclare().getQueue();
        // 回调队列
        AMQP.BasicProperties props = new AMQP.BasicProperties
                .Builder()
                .correlationId(corrId)
                .replyTo(replyQueueName)
                .build();

        channel.basicPublish("", requestQueueName, props, message.getBytes("UTF-8"));

        final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

        String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                response.offer(new String(delivery.getBody(), "UTF-8"));
            }
        }, consumerTag -> {
        });

        String result = response.take();
        channel.basicCancel(ctag);
        return result;
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
```
