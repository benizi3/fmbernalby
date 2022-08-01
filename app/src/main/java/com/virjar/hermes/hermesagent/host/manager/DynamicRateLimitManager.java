package com.virjar.hermes.hermesagent.host.manager;

import android.os.Handler;
import android.os.Looper;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.virjar.hermes.hermesagent.util.libsuperuser.Shell;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 动态限流管理器，基于各种系统表现维度采样，进而动态的给当前系统状态好坏进行判定。
 * 根据当前系统状态动态限制流程，保护自己不被过多的流量打死，避免雪崩问题。
 *
 * @author dengweijia
 * @since 1.13
 */
@Slf4j
public class DynamicRateLimitManager {
    @Getter
    private double limitScore = 1D;
    private Random random = new Random();
    private int[] seedMapping = new int[100];

    private void fillMapping() {
        LinkedList<Integer> linkedList = Lists.newLinkedList();
        for (int i = 0; i < 100; i++) {
            boolean b = random.nextBoolean();
            if (b) {
                linkedList.addFirst(i);
            } else {
                linkedList.addLast(i);
            }
        }
        LinkedList<Integer> linkedListCopy = Lists.newLinkedList();
        for (Integer i : linkedList) {
            boolean b = random.nextBoolean();
            if (b) {
                linkedListCopy.addFirst(i);
            } else {
                linkedListCopy.addLast(i);
            }
        }
        LinkedList<Integer> linkedListCopy2 = Lists.newLinkedList();
        for (Integer i : linkedListCopy) {
            boolean b = random.nextBoolean();
            if (b) {
                linkedListCopy2.addFirst(i);
            } else {
                linkedListCopy2.addLast(i);
            }
        }
        ArrayList<Integer> arrayList = Lists.newArrayList(linkedListCopy2);
        for (int i = 0; i < 100; i++) {
            seedMapping[i] = arrayList.get(i);
        }
    }

    private DynamicRateLimitManager() {
        fillMapping();
    }

    private static DynamicRateLimitManager instance = new DynamicRateLimitManager();

    public static DynamicRateLimitManager getInstance() {
        return instance;
    }

    public void recordParentTreadPoolFull() {
        //不能无限制的降低分数，否则流量过高会直接死掉
        if (limitScore < 0.6) {
            return;
        }
        limitScore = limitScore * 0.95;
    }

    public void recordSubThreadPoolFull() {
        if (limitScore < 0.4) {
            return;
        }
        limitScore = limitScore * 0.9;
        log.warn("sub thread pool full");
    }

    public void recordInvokeSuccess() {
        if (limitScore > 0.85) {
            limitScore = limitScore * 0.9 + 0.1;
        }
    }


    public void recordCPUUsage(double cpuUsage) {
        cpuUsage /= 100;
        log.info("cpu usage:{}", cpuUsage);
        double nowScore;
        if (cpuUsage < 0.1) {
            nowScore = 1.0;
        } else if (cpuUsage < 0.3) {
            nowScore = 0.8;
        } else if (cpuUsage < 0.5) {
            nowScore = 0.3;
        } else {
            nowScore = 0D;
        }
        limitScore = limitScore * 0.9 + nowScore * 0.1;
    }

    public void recordMemoryUsage(double memoryUsage) {
        memoryUsage /= 100;
        log.info("memory usage:{}", memoryUsage);
        double nowScore;
        if (memoryUsage < 0.4) {
            nowScore = 1.0;
        } else if (memoryUsage < 0.5) {
            nowScore = 0.9;
        } else if (memoryUsage < 0.6) {
            nowScore = 0.8;
        } else if (memoryUsage < 0.7) {
            nowScore = 0.6;
        } else if (memoryUsage < 0.75) {
            nowScore = 0.5;
        } else if (memoryUsage < 0.85) {
            nowScore = 0.4;
        } else if (memoryUsage < 0.88) {
            nowScore = 0.2;
        } else if (memoryUsage < 0.9) {
            nowScore = 0.1;
        } else {
            nowScore = 0D;
        }
        limitScore = limitScore * 0.9 + nowScore * 0.1;
    }

    public void recordPingFailed() {
        limitScore = limitScore * 0.9;
    }

    public void recordPingDuration(long duration) {
        log.info("ping duration:{}", duration);
        double nowScore;
        if (duration < 200) {
            nowScore = 1.0;
        } else if (duration < 500) {
            nowScore = 0.9;
        } else if (duration < 1000) {
            nowScore = 0.8;
        } else if (duration < 1500) {
            nowScore = 0.6;
        } else if (duration < 2000) {
            nowScore = 0.5;
        } else if (duration < 3000) {
            nowScore = 0.4;
        } else if (duration < 5000) {
            nowScore = 0.2;
        } else if (duration < 7000) {
            nowScore = 0.1;
        } else {
            nowScore = 0D;
        }
        limitScore = limitScore * 0.9 + nowScore * 0.1;

    }

    private void trimScore() {
        if (limitScore < 0) {
            limitScore = 0;
        } else if (limitScore > 1) {
            limitScore = 1;
        }
    }

    private AtomicLong rateLimitCursor = new AtomicLong(0);
    private RateLimiter rateLimiter = RateLimiter.create(2);

    public boolean limited() {
        trimScore();
        if (limitScore > 0.75) {
            //不限流的状态
            return false;
        }
        if (limitScore > 0.5) {
            //根据流量比例限流，也就是拒绝一定比例的流量
            int cursor = (int) (rateLimitCursor.getAndIncrement() % 100);
            int nowResource = seedMapping[cursor];
            int randomSeed = random.nextInt((int) (limitScore * 100));
            //动态限流
            boolean ret = randomSeed > nowResource;
            if (ret) {
                log.warn("dynamic limit rate");
            }
            return ret;
        }
        if (limitScore > 0.2) {
            //此时，手机状态极差，根据流量比例已经不合适了，这个时候根据QPS来进行流量限制，考虑手机本身接收到的请求来控制
            // 0.5 - 0.2 = 0.3
            // 0.2 - 0.2 = 0
            double rate = limitScore - 0.2;
            if (rate < 0.05) {
                rate = 0.05;
            }
            rateLimiter.setRate(rate);
            return !rateLimiter.tryAcquire();
        }
        return true;
    }

    private void rebootDevice() {
        //先不考虑重启手机了实践手机经常再重启
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Shell.SU.run("reboot");
            }
        });
    }
}
