# kafka命令行使用
1. 开启或关闭kafka服务
```
# 先启动ZooKeeper服务，可直接启动kafka集成的ZooKeeper，&表示后台运行
bin/zookeeper-server-start.sh config/zookeeper.properties & 
bin/kafka-server-start.sh -daemon config/server.properties
# 关闭服务
bin/kafka-server-stop.sh
```
2. 创建topic
```
bin/kafka-topics.sh --create --zookeeper localhost:2181 --topic test --partitions 1 --replication-factor 1
```
3. 启动生产者
```
# 无安全配置
bin/kafka-console-producer.sh --broker-list 10.100.17.79:9092 --topic test
# 有安全配置，producer.properties需要配置
bin/kafka-console-producer.sh --broker-list 10.100.17.79:9092 --topic test --producer.config config/producer.properties
```
4. 启动消费者
```
# 无安全配置
bin/kafka-console-consumer.sh --bootstrap-server 10.100.17.79:9092 --topic test --from-beginning
# 有安全配置，consumer.properties需要配置
bin/kafka-console-consumer.sh --bootstrap-server 10.100.17.79:9092 --topic test --from-beginning --consumer.config config/consumer.properties
```


5. 利用kafka-acls.sh为topic设置ACL 
```
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --add --allow-principal  User:admin  --group test-consumer-group --topic test
```
注意，如果admin要作为消费端连接alice-topic的话，必须对其使用的group（test-consumer-group）也赋权(group 在consumer.properties中有默认配置group.id)

权限操作例子：  
```　　　　　　
# 为用户 alice 在 test（topic）上添加读写的权限
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --add --allow-principal User:alice --operation Read --operation Write --topic test

# 对于 topic 为 test 的消息队列，拒绝来自 ip 为192.168.1.100账户为 zhangsan 进行 read 操作，其他用户都允许
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --add --allow-principal User:* --allow-host * --deny-principal User:zhangsan --deny-host 192.168.1.100 --operation Read --topic test

# 为 zhangsan 和 alice 添加all，以允许来自 ip 为192.168.1.100或者192.168.1.101的读写请求
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --add --allow-principal User:zhangsan --allow-principal User:alice --allow-host 192.168.1.100 --allow-host 192.168.1.101 --operation Read --operation Write --topic test

# 列出 topic 为 test 的所有权限账户
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --list --topic test

# 移除 acl
bin/kafka-acls.sh --authorizer-properties zookeeper.connect=ip:2181 --remove --allow-principal User:zhangsan --allow-principal User:Alice --allow-host 192.168.1.100 --allow-host 192.168.1.101 --operation Read --operation Write --topic test
```
