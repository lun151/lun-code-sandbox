package com.lun.luncodesandbox.unSafe;

/**
 * @Description
 * @Date 2025/10/3
 */
public class SleepError {
    public static void main(String[] args) throws InterruptedException {
        long ONE_HOUR = 60 * 60 * 1000L;
        Thread.sleep(ONE_HOUR);
        System.out.println("sleep end");

    }
}
