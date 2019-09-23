# Mysql锁和事务

1. Lock和Latch  
latch一般称为闩锁（轻量级锁），因为其要求锁定的时间必须非常短。若持续的时间长，则应用的性能会非常差。在InnoDB存储引擎中，latch又可以
分为mutex（互斥量）和rwlock（读写锁）。其目的是用来保证并发线程操作临界资源的正确性，并且通常没有死锁检测的机制。
  
    lock的对象是事务，用来锁定的是数据库中的对象，如表、页、行。并且一般lock的对象仅在事务commit或rollback后进行释放（不同事务隔离级别
释放的时间可能不同）。此外，lock正如在大多数数据库中一样，是有死锁机制的。

2. 锁的类型  
    - 共享锁（S Lock）：允许事务读取一行数据。
    - 排他硕（X Lock）：允许事务删除或更新一行数据。  
    
    此外，InnoDB存储引擎支持多粒度锁定，这种锁定允许事务在行级别上的锁和表级上的锁同时存在。为了支持在不同粒度进行加锁操作，InnoDB存储引擎
支持一种额外的锁方式，称之为意向锁。意向锁是将锁定的对象分为多个层次，意向锁意味着事务希望在更细粒度上进行加锁。
  
    InnoDB存储引擎支持意向锁设计比较简练，其意向锁即为表级别的锁。设计的目的是为了在一个事务中揭示下一行将被请求的锁请求。其支持两种意向锁：
    意向共享锁（IS Lock）和意向排他锁（IX Lock）。
    
    由于InnoDB存储引擎支持的是行级别的锁，因此意向锁其实不会阻塞除全表扫描以外的任何请求。  
    
    用户可以根据表INNODB_TRX、 INNODB_LOCKS、INNODB_LOCK_WAITS查看事务之间锁的阻塞情况。
    
3. 一致性非锁定读  
一致性的非锁定读是指InnoDB存储引擎通过行多版本控制的方式来读取当前执行时间数据库中的行的数据。如果读取的行正在执行删除或者更新操作，这时读取
操作不会因此去等待行上锁的释放，相反地，InnoD存储引擎会去读取行的一个快照数据。这种通过行多版本技术的并发控制，称之为多版本并发控制（Multi Version Concurrency Controller，MVCC）。  

    在事务隔离级别READ COMMITTED和REPEATABLE READ下，InnoDB存储引擎使用非锁定的一致性读。然而，在READ COMMITTED事务隔离级别下，对于
    快照数据，非一致性读总能读取被锁定行的最新一份快照数据。而在REPEATABLE READ事务隔离级别下，对于快照数据，非一致性读总是读取事务开始时的行
    数据版本。
    
4. 一致性锁定读  
InnoDB存储引擎对于SELECT语句支持两种一致性的锁定读操作，必须要在事务中进行：
    - SELECT...FOR UPDATE;
    - SELECT...LOCK IN SHARE MODE;

5. 行锁的3种算法
    - Record Lock：单个行记录上的锁
    - Gap Lock：间隙锁，锁定一个范围，但不包括记录本身
    - Next-Key Lock：Gap Lock+Record Lock，锁定一个范围，并且锁定记录本身
    
    在Next-Key Lock算法下，InnoDB对于行的查询都是采用这种锁定算法。例如一个索引有10，11，13，20四个值，那么该索引可能被锁的区间为：
    >(-∞,10],(10,11],(11,13],(13,20],(20,+∞)  
    
    然而，当查询的索引含有唯一属性时，InnoDB存储引擎会对Next-Key Lock进行优化，将其降级为Record Lock，即仅锁住索引本身，而不是范围，
    该情况仅在查询的列是唯一索引的情况下。若是辅助索引，则情况会完全不同。下面举例说明。
      
    **辅助索引Next-Key Lock说明**  
    首先根据如下代码创建测试表z：
    ```sql
    CREATE TABLE z (a INT ,b INT ,PRIMARY KEY (a), KEY (b));
    INSERT INTO z SELECT 1,1;
    INSERT INTO z SELECT 3,1;
    INSERT INTO z SELECT 5,3;
    INSERT INTO z SELECT 7,6;
    INSERT INTO z SELECT 10,8;
    ```
    表z的列b是辅助索引，若在会话A中执行下面的SQL语句：
    ```sql
    SELECT * FROM z WHERE b=3 FOR UPDATE 
    ```
    显然，存储引擎会使用Next-Key Lock技术加锁，并且由于两个索引，其需要分别进行锁定。对于聚集索引，其仅对列a等于5的索引加上Record Lock。而
    对于辅助索引，其加上的是Next-Key Lock，锁定的范围是(1,3]，特别特别注意的是，InnoDB还会对辅助索引下一个键值加上Gap Lock，即还有一个辅助
    索引范围为(3,6)的锁。因此，若在新会话B中运行下面的SQL语句，都会被阻塞：
    ```sql
    SELECT  * FROM z WHERE a=5 LOCK IN SHARE MODE;
    INSERT INTO z SELECT 4,2;
    INSERT INTO z SELECT 6,5;
    ```
    Gap Lock的作用是为了阻止多个事务将记录插入到同一范围内，避免幻读（Phantom Problem）问题的产生。  
    
    **注意：**
    ```sql
    INSERT INTO z SELECT 4,1;
    INSERT INTO z SELECT 4,6;
    ```
    以上语句同样会阻塞。**个人理解**是加锁应该是锁住了页中的对应范围的记录。辅助索引在页中先根据b，再是a的值大小进行排序。因此`4,1`和`4,6`都在锁定
    的记录范围内，因此无法插入。
    
6. 事务特性
    - 原子性（atomicity）
    - 一致性（consistency）
    - 隔离性（isolation）
    - 持久性（durability）  
    
    在Mysql命令行的默认设置下，事务都是自动提交的，即执行SQL语句后就会马上执行COMMIT操作。也就是说，所有的SQL语句都是在事务中进行的。  
    
    事务隔离性是由锁实现。原子性、持久性通过数据库的redo log，一致性通过undo log来完成的。  
    
    由于在将日志缓冲写入redo log文件后，InnoDB存储引擎为了确保重做日志写入磁盘，必须进行一次fsync操作。由于fsync的效率取决于磁盘的性能，因此
    **磁盘的性能决定了事务提交的性能，也就是数据库的性能**。
    
    SQL标准定义的四个隔离级别为：
    - READ UNCOMMITTED
    - READ COMMITTED
    - REPEATABLE READ
    - SERIALIZABLE  
    
    InnoDB存储引擎默认支持的REPEATABLE READ，但是与标准SQL不同的是，InnoDB存储引擎在REPEATABLE READ事务隔离级别下，使用Next-Key Lock锁的
    算法，因此避免幻读的产生。这与其他数据库系统是不同的。所以说，InnoDB存储引擎默认支持的REPEATABLE READ能完成保证事务的隔离性要求，即
    达到SQL标准的SERIALIZABLE隔离级别。
    
7. 事务分类
    - 扁平事务
    - 带有保存点的扁平事务
    - 链事务
    - 嵌套事务
    - 分布式事务

    InnoDB存储引擎提供了对XA事务的支持，并通过XA事务来支持分布式事务的实现。在使用分布式事务时，InnoDB存储引擎的事务隔离级别必须设置SERIALIZABLE的。  
    
    XA事务由一个或多个资源管理器、一个事务管理器以及一个应用程序组成。
    - 资源管理器：提供访问事务资源的方法，通常一个数据库就是一个资源管理器。
    - 事务管理器：协调参与全局事务中的各个事务，需要和参与全局事务的所有资源管理器进行通信。
    - 应用程序：定义事务的边界，指定全局事务中的操作。
    
    在Mysql数据库的分布式事务中，资源管理器就是Mysql数据库，事务管理器为连接Mysql服务器的客户端。
    
    分布式事务使用两段式提交的方式。在第一阶段，所有参与全局事务的节点都开始准备（Prepare），告诉事务管理器它们准备好提交了。在第二阶段，事务管理器告诉
    资源管理器执行ROLLBACK还是COMMIT。如果任何一个节点显示不能提交，则所有的节点都被告知需要回滚。  
    
    当前Java的JTA可以很好地支持Mysql的分布式事务，需要使用分布式事务应该认真参考其API。
