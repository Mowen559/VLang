package com.example.virtual.service;

import org.springframework.stereotype.Service;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 * VZeroCopyService: VLang 零拷贝内存总线
 * 通过堆外内存 (Off-heap) 实现 Java 与 C++/Python/Go 的极速数据共享
 */
@Service
public class VZeroCopyService {

    private final Map<String, ByteBuffer> memoryPool = new ConcurrentHashMap<>();
    private static Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 分配一块跨语言共享内存
     * @param size 字节大小
     * @return 内存块 ID
     */
    public String allocate(int size) {
        String id = "vmem_" + UUID.randomUUID().toString().substring(0, 8);
        ByteBuffer buffer = ByteBuffer.allocateDirect(size);
        memoryPool.put(id, buffer);
        return id;
    }

    /**
     * 获取内存块的物理起始地址
     * 这是传递给 C++ AOT 产物的“硬核”指标
     */
    public long getPhysicalAddress(String id) {
        ByteBuffer buffer = memoryPool.get(id);
        if (buffer == null) return 0L;
        
        // 使用 Unsafe 获取 DirectBuffer 的物理地址
        try {
            Field addressField = java.nio.Buffer.class.getDeclaredField("address");
            addressField.setAccessible(true);
            return (long) addressField.get(buffer);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 写入数据到共享内存
     */
    public void writeData(String id, byte[] data) {
        ByteBuffer buffer = memoryPool.get(id);
        if (buffer != null) {
            buffer.clear();
            buffer.put(data);
        }
    }

    /**
     * 从共享内存读取数据
     */
    public byte[] readData(String id, int length) {
        ByteBuffer buffer = memoryPool.get(id);
        if (buffer == null) return new byte[0];
        
        byte[] data = new byte[Math.min(length, buffer.capacity())];
        buffer.clear();
        buffer.get(data);
        return data;
    }

    public void release(String id) {
        memoryPool.remove(id);
        // 实际上 DirectBuffer 由 GC 回收，
        // 工业级实现通常会手动调用 cleaner 释放，此处简化处理
    }
}
