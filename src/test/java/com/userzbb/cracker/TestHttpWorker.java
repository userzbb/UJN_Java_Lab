package com.userzbb.cracker;

/**
 * 测试类 - 验证HttpWorker类的功能
 *
 * 实验要求：
 * 1. 定义一个类，包含至少两个成员变量、一个成员方法、一个有参构造方法、一个无参构造方法
 * 2. 使用this关键字、static关键字
 * 3. 在main()方法中创建对象并调用方法
 */
public class TestHttpWorker {

    public static void main(String[] args) {
        System.out.println("=== HttpWorker类功能测试 ===\n");

        // 1. 测试无参构造方法（使用默认值）
        System.out.println("1. 测试无参构造方法（使用默认值）：");
        ProgressTracker tracker1 = new ProgressTracker();
        tracker1.display();
        System.out.println();

        // 2. 测试有参构造方法
        System.out.println("2. 测试有参构造方法：");
        ProgressTracker tracker2 = new ProgressTracker("202331223125");
        tracker2.display();
        System.out.println();

        // 3. 测试this关键字（区分成员变量和参数）
        System.out.println("3. 测试updateIfHigher方法（内部使用this关键字）：");
        System.out.println("   当前高水位标记: " + tracker2.getHighWaterMark());
        boolean updated = tracker2.updateIfHigher("080500");
        System.out.println("   更新080500是否成功: " + updated);
        System.out.println("   更新后高水位标记: " + tracker2.getHighWaterMark());

        // 尝试更新一个更低的值（不会更新）
        updated = tracker2.updateIfHigher("080400");
        System.out.println("   更新080400是否成功: " + updated + " (应该为false，因为080400 < 080500)");
        System.out.println("   高水位标记保持: " + tracker2.getHighWaterMark());
        System.out.println();

        // 4. 测试static关键字
        System.out.println("4. 测试static关键字：");
        System.out.println("   HttpWorker.MAX_RETRIES 是static常量(在HttpWorker类内部定义)");
        System.out.println("   所有HttpWorker实例共享此常量，用于定义最大重试次数");
        System.out.println("   private static final int MAX_RETRIES = 15;");
        System.out.println();

        // 5. 测试成员变量的修改
        System.out.println("5. 测试成员变量的修改：");
        tracker2.incrementAttempts();
        tracker2.incrementAttempts();
        tracker2.incrementAttempts();
        System.out.println("   调用3次incrementAttempts()后:");
        System.out.println("   attemptCount = " + tracker2.getAttemptCount());
        System.out.println();

        // 6. 测试stop方法
        System.out.println("6. 测试running状态：");
        System.out.println("   tracker2.isRunning() = " + tracker2.isRunning());
        tracker2.stop();
        System.out.println("   调用stop()后:");
        System.out.println("   tracker2.isRunning() = " + tracker2.isRunning());
        System.out.println();

        System.out.println("=== 测试完成 ===");
    }
}
