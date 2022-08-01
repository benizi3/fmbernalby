package com.virjar.hermes.hermesagent.host.thread;

import android.support.annotation.NonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 不缓存任何任务
 * 设计有问题，加上这个类之后，系统直接死掉了，需要深入一点分析线程池原理
 *
 * @deprecated
 */
public class ZeroCapacityQueue implements BlockingQueue<Runnable> {
    @Override
    public boolean add(Runnable runnable) {
        return false;
    }

    @Override
    public boolean offer(@NonNull Runnable runnable) {
        return false;
    }

    @Override
    public Runnable remove() {
        return null;
    }

    @Override
    public Runnable poll() {
        return null;
    }

    @Override
    public Runnable element() {
        return null;
    }

    @Override
    public Runnable peek() {
        return null;
    }

    @Override
    public void put(Runnable runnable) throws InterruptedException {

    }

    @Override
    public boolean offer(Runnable runnable, long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public Runnable take() throws InterruptedException {
        return null;
    }

    @Override
    public Runnable poll(long timeout, @NonNull TimeUnit unit) throws InterruptedException {
        return null;
    }

    @Override
    public int remainingCapacity() {
        return 0;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends Runnable> c) {
        return false;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean contains(Object o) {
        return false;
    }

    @NonNull
    @Override
    public Iterator<Runnable> iterator() {
        return null;
    }

    @NonNull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @NonNull
    @Override
    public <T> T[] toArray(@NonNull T[] a) {
        return a;
    }

    @Override
    public int drainTo(@NonNull Collection<? super Runnable> c) {
        return 0;
    }

    @Override
    public int drainTo(@NonNull Collection<? super Runnable> c, int maxElements) {
        return 0;
    }
}
