# ZooKeeper

ZooKeeper致力于提供一个高性能、高可用，且具有严格的顺序访问控制能力（主要是写操作的严格顺序性）的分布式协调服务。  

ZooKeeper的四个设计目标：
- 简单的数据模型：通过一个共享的、树型结构的名字空间来进行相互协调。
- 可以构建集群
- 顺序访问
- 高性能

ZooKeeper的基本概念  
1. 集群角色  
ZooKeeper没有沿用传统的主从概念，而是引入了Leader、Follower和Observer三种角色。ZooKeeper集群中的所有机器通过一个Leader选举过程来选定
一台被称为“Leader”的机器，Leader服务器为客户端提供读和写服务。Follower和Observer都能提供读服务，唯一的区别在于Observer不参与Leader的
选举过程，也不参与写操作的“过半写成功”策略。
2. 会话
3. 数据节点（ZNode）  
ZooKeeper中，“节点”分为两类：机器节点和数据节点。数据节点可以分为持久节点和临时节点。临时节点和会话绑定，一旦客户端会话失败，那么这个客户端
创建的所有临时节点都会被移除。
4. 版本  
对应每个ZNode，ZooKeeper会为其维护一个叫作Stat的数据结构，Stat中记录了这个ZNode的三个数据版本，分别是version（当前ZNode的版本），cversion
（当前ZNode的子节点版本），aversion（当前ZNode的ACL版本）
5. Watcher（事件监听器）
6. ACL  
ZooKeeper定义了如下5中权限
    - CREATE：创建子节点的权限
    - READ：获取节点数据和子节点列表的权限
    - WRITE：更新节点数据的权限
    - DELETE：删除子节点的权限
    - ADMIN：设置节点ACL的权限
    
ZAB协议  
ZAB协议是为分布式协调服务ZooKeeper专门设计的一种支持崩溃恢复的原子广播协议。ZAB协议主要包括消息广播和崩溃恢复两个过程，进一步可以细分
为三个阶段，分别是发现、同步和广播。  
- 消息广播  
  ZAB协议的消息广播过程使用的是一个原子广播协议，类似于一个二阶段提交过程。针对客户端的事务请求，Leader服务器会为其生存对应的事务Proposal，
  并将其发送给集群中其他所有机器，然而再分别收集各自的选票，最后进行事务提交。  

  在ZAB协议的二阶段提交过程中，移除了中断逻辑，所有的Follower服务器要么正常反馈Leader提出的事务Proposal，要么就抛出该事务，同时只需要等待
  过半的Follower服务器反馈确认就可以进行事务提交了。整个消息广播协议是基于具有FIFO特性的TCP协议来进行网络通信的，因此很容易保证消息广播过程
  中消息接收与发送的顺序性。
  
  Leader服务器在广播事务之前，首先会为这个事务分配全局单调递增的唯一ID，即事务ID（ZXID）。具体地，Leader服务器会为每一个Follower服务器分配
  一个单独的队列，按照FIFO的策略进行消息发送。
- 崩溃恢复  
  ZAB协议采用崩溃恢复模式处理Leader服务器崩溃退出带来的数据不一致问题。
  
  ZAB协议需要确保那些已经在Leader服务器上提交的事务最终被所有服务器都提交。  
  ZAB协议需要确保丢弃那些只在Leader服务器上被提出的事务。
  
  ZAB协议会从集群中选举出保存的最高编号的事务ID（ZXID最大）的机器作为Leader服务器，这样可以省去Leader服务器检查Proposal的提交和丢弃工作的这一步操作。
  即采用Fast Leader Election算法。
  成为 leader 的条件：
  - 选epoch最大的
  - epoch相等，选 zxid 最大的
  - epoch和zxid都相等，选择server id最大的（就是我们配置zoo.cfg中的myid）  
  
  节点在选举开始都默认投票给自己，当接收其他节点的选票时，会根据上面的条件更改自己的选票并重新发送选票给其他节点，当有一个节点的得票超过半数，
  该节点会设置自己的状态为 leading，其他节点会设置自己的状态为 following。
  
  在ZAB协议的事务编号ZXID设计中，ZXID是一个64位的数字，其中低32位可以看作成一个简单的单调递增计数器了，针对客户端每一个事务请求，Leader
  在产生新的事务Proposal时，都会对该计数器加1，而高32位则代表了Leader周期的epoch编号（可以理解为选举的届期），每当选举产生一个新的Leader，
  就会从这个Leader服务器上取出本地事务日志中最大的编号proposal的ZXID，并从ZXID中解析得到对应epoch编号，然后再对其进行加1，之后就以此编号
  作为新的epoch值，并将地32位置为0开始生成新的ZXID，ZAB协议通过epoch编号来区分Leader变化周期，能够有效的避免了不同的Leader错误的使用了相
  同的ZXID编号提出了不一样的proposal的异常情况。
  
  基于这样的策略，当一个包含了上一个Leader周期中尚未提交过的事务proposal的服务器启动时，当这台机器加入集群中，以Follow角色连接上Leader服
  务器后，Leader服务器会根据自己服务器上最后提交的proposal来和Follow服务器的proposal进行比对，比对的结果肯定是Leader要求Follow进行一个
  回退操作，回退到一个确实已经被集群中过半机器提交的最新proposal。
  
  
  



