# rabbitmq 交换器

RabbitMQ中消息传递模型的核心思想是：生产者不直接发送消息到队列。实际的运行环境中，生产者是不知道消息会发送到那个队列上，她只会将消息发送到一个交换器，交换器也像一个生产线，她一边接收生产者发来的消息，另外一边则根据交换规则，将消息放到队列中。

交换器的规则有：
- direct（直连）
- topic（主题）
- headers（标题）
- fanout（分发）：广播所有的消息

交换器列表   
通过`rabbitmqctl list_exchanges`指令列出服务器上所有可用的交换器。
>amq.*开头的交换器都是RabbitMQ默认创建的。  
>""空字符串表示默认的匿名交换器，匿名交换器的规则：发送到routingKey名称对应的队列。

绑定列表  
如果要查看绑定列表，可以执行`rabbitmqctl list_bindings`命令

声明一个交换器
```java
channel.exchangeDeclare("hello", "direct", true, false, null);
```

临时队列  
如果要在生产者和消费者之间创建一个新的队列，又不想使用原来的队列，临时队列就是为这个场景而生的：
1. 首先，每当我们连接到RabbitMQ，我们需要一个新的空队列，我们可以用一个随机名称来创建，或者说让服务器选择一个随机队列名称给我们。
2. 一旦我们断开消费者，队列应该立即被删除。  

在Java客户端，提供queuedeclare()为我们创建一个非持久化、独立、自动删除的队列名称。
```java
String queue = channel.queueDeclare().getQueue();
```

**交换器代码实例**  
1. fauout（分发）
```java
public class Producer1 {
    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        // fanout会把消息广播到所有队列，如果交换器是分发fanout类型，就会忽略路由关键字routingkey的作用。
        channel.exchangeDeclare("fanout_logs", "fanout");
        // 当前没有还没有队列被绑定到交换器，消息将被丢弃，因为没有消费者监听，这条消息将被丢弃。
        for (int i = 0; i < 5; i++) {
            String message = "Hello World! " + i;
            channel.basicPublish("mq1_exchange1", "", null, message.getBytes());
            System.out.println(" [x] Sent '" + message + "'");
        }
        channel.close();
        connection.close();
    }
}

public class Consumer1 {
    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        // 如果交换器是分发fanout类型，就会忽略路由关键字routingkey的作用。
        channel.exchangeDeclare("fanout_logs", "fanout");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, "fanout_logs", "");
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(" [x] Received '" + message + "'");
            }
        };
        channel.basicConsume(queueName, true, consumer);
    }
}
```
2. direct（直连）
```java
public class Producer1 {
    private static final String[] routingKeys = new String[]{"info" ,"warning", "error"};

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare("direct_logs", "direct");
        // 分发消息
        for (String routingKey: routingKeys) {
            String message = "Hello World! " + routingKey;
            channel.basicPublish("direct_logs", routingKey, null, message.getBytes());
            System.out.println(" [x] Sent '" + message + "'");
        }
        channel.close();
        connection.close();
    }
}

public class Consumer2 {
    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare("direct_logs", "direct");
        String queueName = channel.queueDeclare().getQueue();
        // 只需要关注其中两种级别，允许多个队列以相同的路由关键字绑定到同一个交换器中
        channel.queueBind(queueName, "direct_logs", "info");
        channel.queueBind(queueName, "direct_logs", "warning");

        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println(" [x] Received '" + message + "'");
            }
        };
        channel.basicConsume(queueName, true, consumer);
    }
}
```
3. topic  

该交互器的匹配符
- *（星号）表示一个单词
- \#（井号）表示零个或者多个单词
>如果消费者端的路由关键字只使用`#`来匹配消息，在匹配`topic`模式下，它会变成一个分发`fanout`模式，接收所有消息。
>如果消费者端的路由关键字中没有`#`或者`*`，它就变成直连`direct`模式来工作。
```java
channel.exchangeDeclare("topic_logs", "topic");
String queueName = channel.queueDeclare().getQueue();
channel.queueBind(queueName, "topic_logs", "*.info.*");
```
4. 远程调用
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

            channel.basicConsume(RPC_QUEUE_NAME, false, deliverCallback, (consumerTag -> { }));
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

public class RPCClient implements AutoCloseable{
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
