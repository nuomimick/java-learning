# ZooKeeper的使用

ZooKeeper的三种部署方式：
1. 单机
2. 伪集群
3. 集群

ZooKeeper命令行简单使用：
1. 创建节点
    ```
    # create [-s] [-e] path data acl
    create -e /zk-book 123
    ```
    -s或-e指定节点特性：顺序还是临时。默认为持久节点。
2. 读取
    ```
    # ls path [watch]
    ls /zk-book 123
    # get path [watch]
    get /zk-book
    ```
3. 更新
    ```
    # set path data [version]
    set /zk-book 456
    ```
4. 删除
    ```
    # delete path [version]
    delete /zk-book
    ```
    
**Java客户端API的使用**
1. 创建会话
```java
public class ZKDemo01 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void process(WatchedEvent watchedEvent) {
        System.out.println("Receive watched event:" + watchedEvent);
        if(Event.KeeperState.SyncConnected == watchedEvent.getState()){
            latch.countDown();
        }
    }

    public static void main(String[] args) throws IOException {
        // 建立会话
        ZooKeeper zooKeeper = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo01());
        System.out.println(zooKeeper.getState());
        try {
            latch.await();
        } catch (InterruptedException e) {}
        System.out.println("ZK session established");
        // 复用会话
        long sessionId = zooKeeper.getSessionId();
        byte[] sessionPasswd = zooKeeper.getSessionPasswd();
        zooKeeper = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo01(), sessionId, sessionPasswd);
    }
}
```
2. 创建节点
```java
/*
同步方式创建
 */
public class ZKDemo02 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
            latch.countDown();
        }
    }
    
    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        ZooKeeper zooKeeper = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo02());
        latch.await();
        String path1 = zooKeeper.create("/zk-demo02-node1", "test-demo02".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        System.out.println("Success create znode:" + path1);
        String path2 = zooKeeper.create("/zk-demo02-node2", "test-demo02".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        System.out.println("Success create znode:" + path2);
    }
}

/*
异步方式创建
*/
public class ZKDemo02 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
            latch.countDown();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        ZooKeeper zooKeeper = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo02());
        latch.await();
        // 最后一个参数用于传递一个对象，可以在回调方法执行的时候使用，通常是放一个上下文信息。
        zooKeeper.create("/zk-demo02-node1-", "test-demo02".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL, new IStringCallback(), "I am ctx...");
        zooKeeper.create("/zk-demo02-node2-", "test-demo02".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL, new IStringCallback(), "I am ctx...");
        Thread.sleep(Integer.MAX_VALUE);
    }
}

class IStringCallback implements AsyncCallback.StringCallback {

    /**
     * @param i  服务器响应码，0：接口调用成功，-4：客户端和服务器端已断开，-110：指定节点已存在，-112：会话已过期
     * @param s  节点路径
     * @param o  调用API时传入的ctx参数
     * @param s1 实际在服务器端创建的节点名
     */
    @Override
    public void processResult(int i, String s, Object o, String s1) {
        System.out.println("Create path result:[" + i + ", " + s + ", " + o + ", real path name:" + s1);
    }
}
```
3. 获取节点
```java
/*
同步方式
*/
public class ZKDemo03 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);
    private static ZooKeeper zk;

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
            // 如果没有节点
            if (watchedEvent.getType() == Event.EventType.None && watchedEvent.getPath() == null) {
                latch.countDown();
            }else if(watchedEvent.getType() == Event.EventType.NodeChildrenChanged){
                // 如果监测到子节点改变，则重新手动获取子节点列表
                try {
                    List<String> children = zk.getChildren(watchedEvent.getPath(), true);
                    System.out.println("ReGetChild:" + children);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        zk = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo03());
        latch.await();
        zk.create("/zk-demo03", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.create("/zk-demo03/c1", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        // watch参数为true表示使用默认的Watcher，即new ZKDemo03()。
        List<String> children = zk.getChildren("/zk-demo03", true);
        System.out.println("GetChild:" + children);
        zk.create("/zk-demo03/c2", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        Thread.sleep(Integer.MAX_VALUE);
    }
}
/*
异步读取
 */
public class ZKDemo03 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);
    private static ZooKeeper zk;

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
            // 如果没有节点
            if (watchedEvent.getType() == Event.EventType.None && watchedEvent.getPath() == null) {
                latch.countDown();
            }else if(watchedEvent.getType() == Event.EventType.NodeChildrenChanged){
                // 如果监测到子节点改变，则重新手动获取子节点列表
                try {
                    List<String> children = zk.getChildren(watchedEvent.getPath(), true);
                    System.out.println("ReGetChild:" + children);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        zk = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo03());
        latch.await();
        zk.create("/zk-demo03", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        zk.create("/zk-demo03/c1", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        // watch参数为true表示使用默认的Watcher，即new ZKDemo03()。
        zk.getChildren("/zk-demo03", true, new IChildren2Callback(), null);
        zk.create("/zk-demo03/c2", "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        Thread.sleep(Integer.MAX_VALUE);
    }
}

class IChildren2Callback implements AsyncCallback.Children2Callback{

    /**
     * @param rc 状态码
     * @param path 节点路径
     * @param ctx 上下文对象，需要从API传入
     * @param children 子节点列表
     * @param stat 一个节点的基本属性信息
     */
    @Override
    public void processResult(int rc, String path, Object ctx, List<String> children, Stat stat) {
        System.out.println("GetChild:" + children);
    }
}
```
4. 获取节点内容
```java
/*
同步获取
 */
public class ZKDemo04 implements Watcher {
    private static CountDownLatch latch = new CountDownLatch(1);
    private static ZooKeeper zk;
    private static Stat stat = new Stat();

    @Override
    public void process(WatchedEvent watchedEvent) {
        if (Event.KeeperState.SyncConnected == watchedEvent.getState()) {
            // 如果没有节点
            if (watchedEvent.getType() == Event.EventType.None && watchedEvent.getPath() == null) {
                latch.countDown();
            } else if (watchedEvent.getType() == Event.EventType.NodeDataChanged) {
                // 如果监测到节点内容改变
                try {
                    System.out.println("ReNodeData:" + new String(zk.getData(watchedEvent.getPath(), true, stat)));
                    System.out.println(stat.getCzxid() + "," + stat.getMzxid() + "," + stat.getVersion());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, KeeperException {
        String path = "/zk-demo04";
        zk = new ZooKeeper("127.0.0.1:2181", 5000, new ZKDemo04());
        latch.await();
        zk.create(path, "123".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        // 传入一个旧的stat对象，对象里的值会被更新为新的
        System.out.println("NodeData:" + new String(zk.getData(path, true, stat)));
        // Czxid节点创建时的事务ID，Mzxid最后一次修改的事务ID
        System.out.println(stat.getCzxid() + "," + stat.getMzxid() + "," + stat.getVersion());
        // -1表示基于数据最新版本进行更新操作
        zk.setData(path, "456".getBytes(), -1);
        Thread.sleep(Integer.MAX_VALUE);
    }
}
/*
异步获取（getData）跟上述其他操作类似，实现的是AsyncCallback.DataCallback接口
异步更新（setData）跟上述其他操作类似，实现的是AsyncCallback.StatCallback接口
 */
```
5. 判断节点是否存在
```java
/*
使用exists方法，有异步方法
 */
```
6. 权限控制  
Zookeeper提供了多种权限控制模式，分别是world、auth、digest、ip和super。
    - world：默认方式，相当于全部都能访问
    - auth：代表已经认证通过的用户(cli中可以通过addauth digest user:pwd 来添加当前上下文中的授权用户)
    - digest：即用户名:密码这种方式认证，这也是业务系统中最常用的。用 username:password 字符串来产生一个MD5串，然后该串被用来作为ACL ID。认证是通过明文发送username:password 来进行的，当用在ACL时，表达式为username:base64 ，base64是password的SHA1摘要的编码。
    - ip：使用客户端的主机IP作为ACL ID 。这个ACL表达式的格式为addr/bits ，此时addr中的有效位与客户端addr中的有效位进行比对。
    - super：跟digest模式一致
```java
public class ZKDemo05 {
    public static final String path = "/zk-auth-test";

    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        ZooKeeper zk1 = new ZooKeeper("127.0.0.1:2181", 5000, null);
        zk1.addAuthInfo("digest", "foo:true".getBytes());
        zk1.create(path, "init".getBytes(), ZooDefs.Ids.CREATOR_ALL_ACL, CreateMode.EPHEMERAL);
        ZooKeeper zk2 = new ZooKeeper("127.0.0.1:2181", 5000, null);
        zk2.addAuthInfo("digest", "foo:true".getBytes());
        System.out.println(new String(zk2.getData(path, false, null)));
        ZooKeeper zk3 = new ZooKeeper("127.0.0.1:2181", 5000, null);
        zk3.addAuthInfo("digest", "foo:false".getBytes());
        System.out.println(new String(zk3.getData(path, false, null)));
    }
}
```
7. 删除节点
```java
/*
使用delete方法，有异步方法 */
```
