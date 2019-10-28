# 使用Redis实现分布式锁

```java
public class RedisDL {
    private JedisPool pool;
    private CountDownLatch notifyLatch = new CountDownLatch(1);
    private ThreadLocal<String> threadLocal = new ThreadLocal<>();

    public RedisDL() {
        pool = new JedisPool(new JedisPoolConfig(), "localhost", 6379, 30000, "password");
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

    public boolean tryLock() {
        boolean result = false;
        try (Jedis jedis = pool.getResource()) {
            String uid = UUID.randomUUID().toString();
            threadLocal.set(uid);
            // 如果不存在就设置值，并设置过期时间
            String rst = jedis.set("lock:mylock", uid, "NX", "PX", 30000);
            if ("OK".equals(rst))
                result = true;
            else if (notifyLatch.getCount() <= 0) {
                notifyLatch = new CountDownLatch(1);
            }
        }
        return result;
    }

    public void unlock() {
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        try (Jedis jedis = pool.getResource()) {
            jedis.eval(script, Arrays.asList("lock:mylock"), Arrays.asList(threadLocal.get()));
        } finally {
            notifyLatch.countDown();
        }
    }
}
```

使用`SET anyLock unique_value NX PX 30000`要注意加锁的业务的执行时间不能太长。
假如有个线程30s都还没有完成业务逻辑的情况下，Key会过期，其他线程有可能会获取到锁。
这样一来的话，第一个线程还没执行完业务逻辑，第二个线程进来了也会出现线程安全问题。自己要解决的话，就得实现定时更新Key的过期时间，
推荐使用Redisson企业级开源包，还提供了对`RedLock`算法的支持。
