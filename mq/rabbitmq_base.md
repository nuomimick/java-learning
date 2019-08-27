# rabbitmq基础

基本概念：
- 生产者和消费者 
- 队列
- 交换器和绑定
- 消息
- vhost

先熟悉以下函数的参数：
```java
/**
 * 声明一个队列
 * 在RabbitMQ中，队列声明是幂等性的（一个幂等操作的特点是其任意多次执行所产生的影响均与一次执行的影响相同），也就是说，如果不存在，就创建，如果存在，不会对已经存在的队列产生任何影响。
 * 
 * @param 队列名字
 * @param true表示持久化队列 (队列将在服务器重新启动后继续存在)
 * @param true表示独占队列
 * @param true表示自动删除队列 (服务器将在不再使用时删除它)
 * @param 其他构造参数
 */
Queue.DeclareOk queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete,
                             Map<String, Object> arguments) throws IOException;

/**
 * 发布一条消息
 * 
 * @param 交换器
 * @param 路由键
 * @param 消息的其他属性，routing headers etc
 * @param 消息体
 */
void basicPublish(String exchange, String routingKey, BasicProperties props, byte[] body) throws IOException;

/**
 * 声明一个交换器.
 * 
 * @param 交换器名称
 * @param 交换器类型
 * @param 是否持久化交换器 (如果是，则在服务器重启后继续存在)
 * @param true表示自动删除队列 (服务器将在不再使用时删除它)
 * @param 其他构造参数
 */
Exchange.DeclareOk exchangeDeclare(String exchange, String type, boolean durable, boolean autoDelete,
                                       Map<String, Object> arguments) throws IOException;


/**
 * 绑定队列到交换器
 * @param 队列名称
 * @param 交换器名称
 * @param 路由键
 */
Queue.BindOk queueBind(String queue, String exchange, String routingKey) throws IOException;
```
