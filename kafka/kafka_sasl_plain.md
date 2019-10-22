# kafka开启sasl/plain配置

首先进入kafka的安装目录  
1. 新建config/kafka_server_jaas.conf
```
KafkaServer {
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username="kafkaadmin"
    password="kafkaadminpwd"
    user_kafkaadmin="kafkaadminpwd"
    user_kafkaclient1="kafkaclient1pwd"
    user_kafkaclient2="kafkaclient2pwd";
};
# zookeeper认证使用
Client {
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username="zooclient"
    password="zooclientpwd";
};
```
几点说明：  
1. username/password是给kafka brokers之间作为client初始请求连接访问使用的，会被作为server的broker验证。
2. user_kafkaadmin这个就是1中提到的连接访问请求验证信息，所以这条是必须的。
3. user_kafkaclient1/user_kafkaclient2定义了kafka的client，其值就是密码。
4. Client是把kafka作为client端，访问zookeeper(作为server端)的时候用的。对应的必须在zookeeper里面做相应的配置。


2. 多节点zookeeper下认证  
增加配置文件：`touch kafka_zoo_jaas.conf`  
配置如下：
```
ZKServer{
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username="admin"
    password="admin"
    user_zooclient="zooclientpwd";
};
```
配置应用名称为ZKServer，zookeeper默认使用的JAAS应用名称是Server(或者zookeeper)。
设置的环境变量其实只是传入到JVM的参数。这里设置的环境变量是KAFKA_OPTS。
修改zookeeper的启动脚本zookeeper-server-start.sh如下：
```
export KAFKA_OPTS="-Djava.security.auth.login.config=/kafka安装目录/config/kafka_zoo_jaas.conf  -Dzookeeper.sasl.serverconfig=ZKServer"
```
如果配置使用默认名称，则只需要添加：
```   
export KAFKA_OPTS=" -Djava.security.auth.login.config=/kafka安装目录/config/kafka_zoo_jaas.conf"
```
修改zookeeper.properties配置文件，增加配置：
```   
authProvider.1=org.apache.zookeeper.server.auth.SASLAuthenticationProvider
requireClientAuthScheme=sasl
jaasLoginRenew=3600000
```

3. 新建config/kafka_client_jaas.conf
```
KafkaClient {
    org.apache.kafka.common.security.plain.PlainLoginModule required
    username="kafkaadmin"
    password="kafkaadminpwd";
};
```
4. 配置config/server.properties
```
listeners=SASL_PLAINTEXT://ip:9092
# 使用的认证协议 
security.inter.broker.protocol=SASL_PLAINTEXT
#SASL机制 
sasl.enabled.mechanisms=PLAIN  
sasl.mechanism.inter.broker.protocol=PLAIN   
# 完成身份验证的类 
authorizer.class.name=kafka.security.auth.SimpleAclAuthorizer 
# 如果没有找到ACL（访问控制列表）配置，则允许任何操作。 
#allow.everyone.if.no.acl.found=true
super.users=User:kafkaadmin
```
5. 修改consuer和producer的配置文件consumer.properties和producer.properties，分别增加如下配置：
```
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
```

6. 修改运行脚本  
JAAS文件作为每个broker的jvm参数，在bin/kafka-server-start.sh脚本中增加如下配置（可在最上面）：
```
export KAFKA_OPTS=" -Djava.security.auth.login.config=/kafka安装目录/config/kafka_server_jaas.conf"
```
在kafka-console-consumer.sh和kafka-console-producer.sh中添加：
```
export KAFKA_OPTS="-Djava.security.auth.login.config=/kafka安装目录/config/kafka_client_jaas.conf"
```
7. 启动zookeeper和kafka
```
bin/zookeeper-server-start.sh config/zookeeper.properties & （&代表后台运行）
bin/kafka-server-start.sh config/server.properties &
```
