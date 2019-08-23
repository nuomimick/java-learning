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
