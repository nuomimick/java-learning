# 常用linux命令记录

**Centos 7.3版本**
1. 更改网络IP
```
cd /etc/sysconfig/network-scripts
vi ifcfg-eth0
# 修改文件中的IPADDR属性
service network restart
```
2. 防火墙放开和关闭端口
```
firewall-cmd --zone=public(作用域) --add-port=80/tcp(端口和访问类型) --permanent(永久生效)
firewall-cmd --zone= public --remove-port=80/tcp --permanent  # 删除
firewall-cmd --reload    # 重新载入，更新防火墙规则
```
3. 添加环境变量
```
vi /etc/profile
# 添加环境变量，例如添加下方Java变量
export JAVA_HOME=/usr/local/java
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar
export JRE_HOME=$JAVA_HOME/jre
```
4. 查看Jps详细信息
```
jps -mlv
```

**Debian版本**
1. 修改IP  
查看物理地址对应的网卡名字
```
cat /etc/udev/rules.d/70-persistent-net.rules
```
修改ip和对应的网卡名字
```
vi /etc/network/interfaces
#########修改如下##########
......
allow-hotplug eth0
iface eth1 inet static
        address 10.99.207.178
......
```
重启网络
```
/etc/init.d/networking restart
```
