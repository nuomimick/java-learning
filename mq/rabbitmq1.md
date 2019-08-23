# rabbitmq学习记录之如何保证消息的可靠性传输（如何防止消息丢失）

总的来说就是保证以下三个方面不出问题
1. 防止生产者丢失了数据
2. 防止rabbitmq丢失了数据
3. 防止消费者丢失了数据

具体来说：
1. 防止生产者丢失了数据  
    - 添加事务支持（同步，不支持）
    ```java
    // 开启事务
    channel.txSelect();
    try{
        channel.basicPublish("", QUEUE_NAME, MessageProperties.MINIMAL_BASIC, message.getBytes("UTF-8"));
        System.out.println("P [x] Sent '" + message + "'");
    }finally {
        channel.txRollback();
        // 重新发送消息
    }
    // 提交事务
    channel.txCommit();
    ```
    - 开启confirm机制（异步，推荐）
    ```java
    // 单条推送
    channel.confirmSelect();
    channel.basicPublish("", QUEUE_NAME, MessageProperties.MINIMAL_BASIC, message.getBytes("UTF-8"));
    if (channel.waitForConfirms()) {
    	System.out.println("消息发送成功" );
    }
    // 批量推送
    channel.confirmSelect();
    for (int i = 0; i < 10; i++) {
    	String message = String.format("时间 => %s", new Date().getTime());
    	channel.basicPublish("", config.QueueName, null, message.getBytes("UTF-8"));
    }
    channel.waitForConfirmsOrDie(); //直到所有信息都发布，只要有一个未确认就会IOException
    System.out.println("全部执行完成");
    // 监听模式（推荐）
    channel.confirmSelect();
    channel.addConfirmListener(new ConfirmListener() {
        @Override
        // 消息确认有可能是批量确认的，是否批量确认在于返回的multiple的参数，此参数为bool值，如果true表示批量执行了deliveryTag这个值以前的所有消息，如果为false的话表示单条确认
        public void handleNack(long deliveryTag, boolean multiple) throws IOException {
            System.out.println("未确认消息，标识：" + deliveryTag);
        }
        @Override
        public void handleAck(long deliveryTag, boolean multiple) throws IOException {
            System.out.println(String.format("已确认消息，标识：%d，多个消息：%b", deliveryTag, multiple));
        }
    });
    for (int i = 0; i < 10; i++) {
        channel.basicPublish("", QUEUE_NAME, MessageProperties.MINIMAL_BASIC, message.getBytes("UTF-8"));
        System.out.println("P [x] Sent '" + message + "'");
    }
    ```

2. 防止rabbitmq丢失了数据（消息持久化）  
    - 队列和交换器的`durable`属性置true
    ```java
    boolean durable = true;
    channel.exchangeDeclare("hello", "direct", durable, false, null);
    channel.queueDeclare(QUEUE_NAME, durable, false, false, null);
    ```
    - 在消息发布前，把它的“投递模式”选项置2
    ```java    
    channel.basicPublish("", QUEUE_NAME, MessageProperties.MINIMAL_PERSISTENT_BASIC, message.getBytes("UTF-8"));
    // MessageProperties封装了BasicProperties的一些常量，可以直接用
    public static final BasicProperties MINIMAL_PERSISTENT_BASIC =
            new BasicProperties(null, null, null, 2,
                                null, null, null, null,
                                null, null, null, null,
                                null, null);
    ```
    持久化可以跟生产者那边的 confirm 机制配合起来，只有消息被持久化到磁盘之后，才会通知生产者 ack
3. 防止消费者丢失了数据
    - 关闭默认的自动确认
    ```java
    boolean autoAck = false;
    channel.basicConsume(QUEUE_NAME, autoAck, consumer);
    ```
    - 自定义确认回复
    ```java
    Consumer consumer = new DefaultConsumer(channel) {
        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String message = new String(body, "UTF-8");
            System.out.println("C [x] Received '" + message + "'");
            channel.basicAck(envelope.getDeliveryTag(), false);
        }
    };
    ```
    > 消费者回馈机制的好处在于如果你的应用程序崩溃了，这样做可以确保消息会被发送给另一个消费者进行处理。另一方面，如果程序存在bug而忘记确认消息的话，rabbitmq将不会给该消费者发送更多消息了。存在可能会导致重复消费者的问题。如果消费者收到一条消息，然后确认之前从rabbitmq断开连接（或者从队列上取消订阅），rabbitmq会认为这条消息没有分发，然后重新分发给下一个订阅的消费者。

