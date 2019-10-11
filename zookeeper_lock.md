# Zookeeper 分布式锁
1. 排他锁  
第一版
```java
/**
* 不同的客户端尝试创建一个相同名字的节点，如果这个节点不存在则创建，
* 并说明该客户端获取到了锁，其他客户端对该节点添加Watch监听节点变化。
* 释放锁就是删除节点。
*/
public class DLock {
    private final String exclusivePath = "/exclusive_lock";
    private final String exclusiveNodePath = "/exclusive_lock/lock";
    private final String sharedPath = "/shared_lock";
    private ZooKeeper zk;
    private CountDownLatch connectLatch = new CountDownLatch(1);
    private CountDownLatch notifyLatch = new CountDownLatch(1);
    private IWatcher watcher = new IWatcher();

    public DLock() {
        try {
            zk = new ZooKeeper("127.0.0.1:2181", 5000, watcher);
            connectLatch.await();
            zk.create(exclusivePath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            zk.create(sharedPath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        while (!tryLock()) {
            System.out.println("未获得锁，继续等待...");
            try {
                notifyLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("已获得锁!!!");
    }

    private boolean tryLock() {
        try {
            zk.create(exclusiveNodePath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
            return true;
        } catch (KeeperException ke) {
            if (ke.code() == KeeperException.Code.NODEEXISTS) {
                try {
                    zk.exists(exclusiveNodePath, true);
                    if (notifyLatch.getCount() <= 0) {
                        notifyLatch = new CountDownLatch(1);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void unlock() {
        try {
            zk.delete(exclusiveNodePath, -1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    class IWatcher implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            if (event.getState() == Event.KeeperState.SyncConnected) {
                connectLatch.countDown();
                if (event.getType() == Event.EventType.NodeDeleted) {
                    notifyLatch.countDown();
                }
            }
        }
    }
}
```
第二版（也可以作分布式队列）
```java
/**
* 利用临时顺序节点，如果客户端新建的节点是子节点列表里最小的节点，则说明获取到锁，
* 否则就对新建节点的前一个节点添加监听
*/
public class DDLock {
    private final String sharedPath = "/shared_lock";
    private ZooKeeper zk;
    private CountDownLatch connectLatch = new CountDownLatch(1);
    private CountDownLatch notifyLatch = new CountDownLatch(1);
    private DDLock.IWatcher watcher = new DDLock.IWatcher();
    private String hostName;
    private ThreadLocal<String> lockNodeName = new ThreadLocal<>();

    public DDLock() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            //获取本机计算机名称
            hostName = address.getHostName();
            zk = new ZooKeeper("127.0.0.1:2181", 5000, watcher);
            connectLatch.await();
            zk.create(sharedPath, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void lock() {
        while (!tryLock()) {
            System.out.println("未获得锁，继续等待...");
            try {
                notifyLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println("已获得锁!!!");
    }

    private boolean tryLock() {
        String path = sharedPath + "/" + hostName + "-W-";
        String lockedName = "";
        if (lockNodeName.get() == null) {
            try {
                lockedName = zk.create(path, "".getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
                lockNodeName.set(lockedName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            lockedName = lockNodeName.get();
        }
        try {
            List<String> children = zk.getChildren(sharedPath, true);
            int lockNum = Integer.valueOf(lockedName.substring(lockedName.lastIndexOf("-") + 1));
            Map<Integer, String> collect = children.stream().collect(Collectors.toMap(vo -> Integer.valueOf(vo.substring(vo.lastIndexOf("-") + 1)), vo -> vo));
            int[] sorted = children.stream().map(name -> Integer.valueOf(name.substring(name.lastIndexOf("-") + 1))).mapToInt(v -> v).sorted().toArray();
            // 如果节点序号为当前序号最小的节点，则表示获取锁成功
            int lockIdx = Arrays.binarySearch(sorted, lockNum);
            if (sorted[0] == lockNum) {
                return true;
            } else {
                zk.exists(sharedPath + "/" + collect.get(sorted[lockIdx - 1]), true);
                if (notifyLatch.getCount() <= 0) {
                    notifyLatch = new CountDownLatch(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public void unlock() {
        try {
            zk.delete(lockNodeName.get(), -1);
            lockNodeName.remove();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class IWatcher implements Watcher {

        @Override
        public void process(WatchedEvent event) {
            if (event.getState() == Event.KeeperState.SyncConnected) {
                connectLatch.countDown();
                if (event.getType() == Event.EventType.NodeDeleted) {
                    notifyLatch.countDown();
                }
            }
        }
    }
}
```
2. 共享锁  
改进后的分布式锁实现（避免了羊群效应）
- 客户端调用create()方法创建一个类似于“/shared_lock/[Hostname]-请求类型-序号”的临时顺序节点。
- 客户端调用getChildren()接口来获取所有已经创建的子节点列表，注意，这里不注册任何Watcher。
- 如果无法获取共享锁，那么就调用exist()来对比自己小的那个节点注册Watcher。注意，这里“比自己小的节点”只是一个笼统的说法，具体对于读请求和写请求不一样。  
  读请求：向比自己序号小的最有一个写请求节点注册Watcher监听。
  写请求：向比自己序号小的最后一个节点注册Watcher监听。
- 等待Watcher通知，继续进行步骤2。
