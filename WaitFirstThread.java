/**
 * 模拟Redis查询操作
 */
class Status {
    private volatile int status;

    /**
     * 模拟Redis查询操作
     *
     * @return 查询结果
     */
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        /**
         * 模拟Redis查询并设置key操作，可能比较耗时，所有加了sleep
         */
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.status = status;
    }
}

public class WaitFirstThread {
    public void waitOne() {
        final Status status = new Status();
        ReentrantLock lock = new ReentrantLock();
        Runnable r = () -> {
            if (status.getStatus() == 0) {
                lock.lock();
                try {
                    if (status.getStatus() == 0) {
                        System.out.println("查询");
                        status.setStatus(1);
                    } else {
                        System.out.println("直接返回1:" + status.getStatus());
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("直接返回2:" + status.getStatus());
            }
        };
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 10; i++) {
            executorService.submit(r);
        }
        executorService.shutdown();
    }

    public void waitTwo() {
        final Status status = new Status();
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        AtomicInteger counter = new AtomicInteger(0);
        Runnable r = () -> {
            counter.getAndIncrement();
            lock.lock();
            try {
                if (counter.get() > 1) {
                    if (status.getStatus() == 0) {
                        condition.await();
                    }
                    System.out.println("直接返回:" + status.getStatus());
                } else {
                    status.setStatus(1);
                    System.out.println("查询");
                    condition.signal();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }

        };
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 10; i++) {
            executorService.submit(r);
        }
        executorService.shutdown();
    }
}
