package com.quant.agent.infrastructure.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// ============================================================================================
// 【Day 8 · 阅读入口】MarketDataCache —— 带 TTL 的行情缓存，JDK 原生实现（不引入 Caffeine/Redis）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 工程护栏的"缓存层"。MarketDataGateway 的第二道关卡。
//   建议阅读时机：读完 RateLimiter 后读它。
//   学完能回答：
//     1. 为什么行情数据要缓存？TTL 设多少合适？
//     2. 这个实现是"主动过期"还是"惰性过期"？
//     3. 为什么缓存 value 用 byte[] 而不是 String？
//
//   💡 为什么行情数据要缓存？TTL 设多少合适？
//     行情 API 调用有成本（限流 + 网络延迟 50~200ms）。
//     同一个 symbol 在短时间内被多次请求（LLM 循环、多个 Node）→ 缓存避免重复调用。
//
//     TTL 选择：
//       - 实时行情：3~5 秒（变化快，太旧没意义）
//       - 基本面：1 小时（变化慢）
//     这里用可配置 TTL，默认 5 秒（行情场景）。
//
//   💡 "主动过期"还是"惰性过期"？
//     两者结合：
//       - 惰性过期：get() 时检查是否过期，过期返回 null（保证读到的都是新鲜的）
//       - 主动清理：后台定时任务移除过期条目（防止内存泄漏）
//     惰性过期保证正确性，主动清理保证内存安全。
//
//   💡 为什么用 byte[] 而不是 String？
//     不是必须，但 byte[] 更通用：
//       - 可以存 JSON 字符串的 UTF-8 字节
//       - 未来如果接二进制协议（如 protobuf）也能直接存
//     这里实际存的是 JSON 的 UTF-8 字节，用 new String(bytes, UTF_8) 还原。
//
//   ⬇ 下一步：看 MarketDataGateway（编排限流+缓存+Adapter）。
// ============================================================================================

/**
 * 带 TTL 的行情缓存（JDK 原生实现）。
 *
 * <p>特性：
 * <ul>
 *   <li>惰性过期 + 主动清理双保险</li>
 *   <li>可配置 TTL（毫秒）</li>
 *   <li>命中率统计（可观测性）</li>
 *   <li>线程安全：ConcurrentHashMap + ScheduledExecutorService</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   MarketDataCache cache = new MarketDataCache(5000);  // 5 秒 TTL
 *   cache.put("price:600519", jsonBytes);
 *   byte[] hit = cache.get("price:600519");  // 命中返回数据；过期返回 null
 * </pre>
 */
public class MarketDataCache {

    private static final Logger log = LoggerFactory.getLogger(MarketDataCache.class);

    // -------------------------------------------------------------------------
    // 缓存条目
    // -------------------------------------------------------------------------

    /**
     * 单个缓存条目。
     *
     * <p>记录存入时间 + TTL，用于惰性过期判断。
     *
     * @param data      缓存数据（JSON UTF-8 字节）
     * @param expireAt  过期时间戳（毫秒）
     */
    private record CacheEntry(byte[] data, long expireAt) {
        /** 是否已过期 */
        boolean isExpired(long now) {
            return now >= expireAt;
        }
    }

    // -------------------------------------------------------------------------
    // 配置
    // -------------------------------------------------------------------------

    /** 默认 TTL（毫秒） */
    private final long defaultTtlMillis;

    /** 缓存最大条目数（防内存无限增长） */
    private final int maxEntries;

    // -------------------------------------------------------------------------
    // 存储
    // -------------------------------------------------------------------------

    /** 底层存储：key → 条目 */
    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    /** 后台清理器：定时移除过期条目 */
    private final ScheduledExecutorService cleaner;

    // -------------------------------------------------------------------------
    // 统计（可观测性）
    // -------------------------------------------------------------------------

    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // 构造器
    // -------------------------------------------------------------------------

    /**
     * 构造器。
     *
     * @param defaultTtlMillis 默认 TTL（毫秒），必须 > 0
     * @param maxEntries        最大条目数，必须 > 0
     * @param cleanupIntervalMillis 后台清理间隔（毫秒）
     */
    public MarketDataCache(long defaultTtlMillis, int maxEntries, long cleanupIntervalMillis) {
        if (defaultTtlMillis <= 0) {
            throw new IllegalArgumentException("defaultTtlMillis 必须 > 0");
        }
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 必须 > 0");
        }
        this.defaultTtlMillis = defaultTtlMillis;
        this.maxEntries = maxEntries;

        // 启动后台清理线程（守护线程，不阻止 JVM 退出）
        this.cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "market-data-cache-cleaner");
            t.setDaemon(true);
            return t;
        });
        this.cleaner.scheduleAtFixedRate(this::evictExpired,
                cleanupIntervalMillis, cleanupIntervalMillis, TimeUnit.MILLISECONDS);

        log.info("MarketDataCache 初始化: ttl={}ms, maxEntries={}, cleanupInterval={}ms",
                defaultTtlMillis, maxEntries, cleanupIntervalMillis);
    }

    /**
     * 便捷构造器：默认 5 秒 TTL、1000 条目、10 秒清理间隔。
     */
    public MarketDataCache() {
        this(5000L, 1000, 10_000L);
    }

    // -------------------------------------------------------------------------
    // 核心操作
    // -------------------------------------------------------------------------

    /**
     * 存入缓存（使用默认 TTL）。
     *
     * @param key   键（如 "price:600519"）
     * @param data  数据（JSON UTF-8 字节）
     */
    public void put(String key, byte[] data) {
        put(key, data, defaultTtlMillis);
    }

    /**
     * 存入缓存（指定 TTL）。
     *
     * <p>如果缓存已满（>= maxEntries），存入失败并打 warn（不抛异常，保证主流程不受影响）。
     *
     * @param key       键
     * @param data      数据
     * @param ttlMillis TTL（毫秒）
     */
    public void put(String key, byte[] data, long ttlMillis) {
        if (key == null || data == null) {
            return;
        }
        // 防内存无限增长：满了就不存（让下次请求走 Adapter）
        if (store.size() >= maxEntries && !store.containsKey(key)) {
            log.warn("缓存已满，跳过写入: key={}, size={}", key, store.size());
            return;
        }
        long expireAt = System.currentTimeMillis() + ttlMillis;
        store.put(key, new CacheEntry(data, expireAt));
    }

    /**
     * 存入字符串（自动转 UTF-8 字节）。
     */
    public void putString(String key, String value) {
        if (value == null) return;
        put(key, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 存入字符串（指定 TTL，自动转 UTF-8 字节）。
     */
    public void putString(String key, String value, long ttlMillis) {
        if (value == null) return;
        put(key, value.getBytes(java.nio.charset.StandardCharsets.UTF_8), ttlMillis);
    }

    /**
     * 读取缓存。
     *
     * <p>惰性过期：get 时检查是否过期，过期返回 null（即使条目还在 map 里）。
     *
     * @param key 键
     * @return 命中且未过期返回数据；未命中或已过期返回 null
     */
    public byte[] get(String key) {
        if (key == null) return null;
        CacheEntry entry = store.get(key);
        if (entry == null) {
            misses.incrementAndGet();
            return null;
        }
        // 惰性过期检查
        if (entry.isExpired(System.currentTimeMillis())) {
            misses.incrementAndGet();
            return null;
        }
        hits.incrementAndGet();
        return entry.data();
    }

    /**
     * 读取缓存字符串（自动从 UTF-8 字节还原）。
     *
     * @param key 键
     * @return 命中返回字符串；未命中返回 null
     */
    public String getString(String key) {
        byte[] data = get(key);
        if (data == null) return null;
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 主动移除指定 key。
     */
    public void invalidate(String key) {
        if (key != null) {
            CacheEntry removed = store.remove(key);
            if (removed != null) evictions.incrementAndGet();
        }
    }

    /**
     * 清空缓存。
     */
    public void clear() {
        long size = store.size();
        store.clear();
        evictions.addAndGet(size);
    }

    // -------------------------------------------------------------------------
    // 内部：后台清理过期条目
    // -------------------------------------------------------------------------

    /**
     * 遍历并移除过期条目（定时任务调用）。
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        AtomicLong removed = new AtomicLong(0);
        store.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(now)) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });
        if (removed.get() > 0) {
            evictions.addAndGet(removed.get());
            log.debug("缓存清理: 移除 {} 个过期条目, 剩余 {}", removed.get(), store.size());
        }
    }

    // -------------------------------------------------------------------------
    // 可观测性：统计快照
    // -------------------------------------------------------------------------

    /** 命中次数 */
    public long getHits() {
        return hits.get();
    }

    /** 未命中次数 */
    public long getMisses() {
        return misses.get();
    }

    /** 驱逐次数（主动清理 + invalidate + clear） */
    public long getEvictions() {
        return evictions.get();
    }

    /** 当前条目数 */
    public int size() {
        return store.size();
    }

    /** 命中率（0.0 ~ 1.0），无请求时返回 0 */
    public double getHitRate() {
        long total = hits.get() + misses.get();
        if (total == 0) return 0.0;
        return (double) hits.get() / total;
    }

    /**
     * 关闭后台清理线程（应用退出时调用，防止线程泄漏）。
     */
    public void shutdown() {
        cleaner.shutdown();
    }

    @Override
    public String toString() {
        return String.format("MarketDataCache{size=%d/%d, hitRate=%.1f%%, hits=%d, misses=%d, evictions=%d}",
                size(), maxEntries, getHitRate() * 100, getHits(), getMisses(), getEvictions());
    }
}
