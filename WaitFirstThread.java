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
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
    /**
     * 第一个版本，读线程会阻塞（串行执行）
     */
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

    /**
     * 第二个版本，用while循环等待，支持读并发
     */
    public void waitTwo() {
        final Status status = new Status();
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger counter = new AtomicInteger(0);
        Runnable r = () -> {
            counter.getAndIncrement();
            if (counter.get() > 1) {
                while (status.getStatus() == 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                System.out.println("直接返回:" + status.getStatus());
            } else {
                status.setStatus(1);
                System.out.println("查询");
            }
        };
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        for (int i = 0; i < 10; i++) {
            executorService.submit(r);
        }
        executorService.shutdown();
    }
}
