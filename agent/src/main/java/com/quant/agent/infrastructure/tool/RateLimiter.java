package com.quant.agent.infrastructure.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// ============================================================================================
// 【Day 8 · 阅读入口】RateLimiter —— 令牌桶限流，JDK 原生实现（不引入 Guava/Resilience4j）。
// --------------------------------------------------------------------------------------------
//   在整条链里的位置：Day 8 工程护栏的"限流门卫"。MarketDataGateway 的第一道关卡。
//   建议阅读时机：Day 8 读完 MarketPrice 后读它。
//   学完能回答：
//     1. 为什么 Agent 系统需要限流？
//     2. 令牌桶和漏桶有什么区别？为什么选令牌桶？
//     3. 这个实现是"阻塞等待"还是"快速拒绝"？为什么？
//
//   💡 为什么 Agent 系统需要限流？
//     外部行情 API（东财/同花顺）有调用频率限制（免费版 ~1s 1 次）。
//     但 Agent 的 LLM 可能在一次工具循环里连续调 N 次 getStockPrice ——
//     如果不加限流 → 瞬间打爆 API → 触发限流/IP 封禁 → 整个 Agent 失明。
//     限流 = 保护外部 API = 保护 Agent 自己。
//
//   💡 令牌桶 vs 漏桶？
//     - 漏桶：固定速率流出（严格匀速）。优点绝对平稳，缺点不能应对突发。
//     - 令牌桶：固定速率产生令牌，来了请求就消耗令牌；桶满时令牌不再累积。
//       优点：允许一定突发（桶里攒的令牌可以一次性用完），更贴合 Agent 场景。
//     这里选令牌桶：Agent 启动时可能需要"突发"拉取多个股票数据，之后平稳。
//
//   💡 这个实现是"阻塞等待"还是"快速拒绝"？
//     快速拒绝（tryAcquire，不阻塞）。
//     原因：Agent 的 Node 执行是同步阻塞的（GraphRunner 是同步 invoke）。
//     如果限流阻塞等待 → Node 卡住 → 整个图卡住 → 用户等不到响应。
//     快速拒绝 → 立刻返回 false → Gateway 走降级路径 → 用户看到"数据降级"而非超时。
//     这是 constitution 里"失败路径必须可观测"的体现。
//
//   ⬇ 下一步：看 MarketDataCache（缓存护栏）。
// ============================================================================================

/**
 * 令牌桶限流器（JDK 原生实现）。
 *
 * <p>设计选择：
 * <ul>
 *   <li>快速拒绝（非阻塞）：超限立刻返回 false，不卡线程</li>
 *   <li>惰性计算令牌：不依赖后台线程，每次 tryAcquire 时按时间差补令牌</li>
 *   <li>线程安全：AtomicLong + ReentrantLock 保证并发正确</li>
 * </ul>
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    // -------------------------------------------------------------------------
    // 配置
    // -------------------------------------------------------------------------

    /** 每秒产生的令牌数 = 允许的调用频率 */
    private final double permitsPerSecond;

    /** 桶容量 = 允许的最大突发量 */
    private final long maxPermits;

    // -------------------------------------------------------------------------
    // 状态
    // -------------------------------------------------------------------------

    /** 当前可用令牌数 */
    private double availablePermits;

    /** 上次补充令牌的时间戳（纳秒） */
    private final AtomicLong lastRefillTimeNanos = new AtomicLong(System.nanoTime());

    /** 保护 availablePermits 的并发写 */
    private final Lock lock = new ReentrantLock();

    // -------------------------------------------------------------------------
    // 统计（可观测性）
    // -------------------------------------------------------------------------

    /** 总请求数 */
    private final AtomicLong totalRequests = new AtomicLong(0);

    /** 总拒绝数 */
    private final AtomicLong totalRejected = new AtomicLong(0);

    /**
     * 构造器。
     *
     * @param permitsPerSecond 每秒允许的调用次数（如 1.0 = 每秒 1 次）
     * @param maxPermits       桶容量（允许的突发量，通常 = permitsPerSecond 的 2~3 倍）
     */
    public RateLimiter(double permitsPerSecond, long maxPermits) {
        if (permitsPerSecond <= 0) {
            throw new IllegalArgumentException("permitsPerSecond 必须 > 0");
        }
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits 必须 > 0");
        }
        this.permitsPerSecond = permitsPerSecond;
        this.maxPermits = maxPermits;
        this.availablePermits = maxPermits;  // 初始满桶（允许启动时突发）
    }

    /**
     * 便捷构造器：桶容量 = 每秒令牌数（不允许突发）。
     */
    public RateLimiter(double permitsPerSecond) {
        this(permitsPerSecond, Math.max(1L, (long) Math.ceil(permitsPerSecond)));
    }

    /**
     * 尝试获取一个令牌（快速拒绝，不阻塞）。
     *
     * <p>流程：
     *   1. 先按时间差补充令牌（惰性计算）
     *   2. 有可用令牌 → 消耗 1 个 → return true
     *   3. 没令牌 → return false（调用方走降级）
     *
     * @return true = 允许通过；false = 限流拒绝
     */
    public boolean tryAcquire() {
        totalRequests.incrementAndGet();
        lock.lock();
        try {
            refill();  // 惰性补令牌

            if (availablePermits >= 1.0) {
                availablePermits -= 1.0;
                return true;
            }

            // 没令牌 → 拒绝
            totalRejected.incrementAndGet();
            log.debug("限流拒绝: available={}/{}, rate={}/s",
                    availablePermits, maxPermits, permitsPerSecond);
            return false;

        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试获取指定数量的令牌。
     *
     * @param permits 需要的令牌数
     * @return true = 允许；false = 拒绝
     */
    public boolean tryAcquire(int permits) {
        if (permits <= 0) return true;
        totalRequests.incrementAndGet();
        lock.lock();
        try {
            refill();
            if (availablePermits >= permits) {
                availablePermits -= permits;
                return true;
            }
            totalRejected.incrementAndGet();
            return false;
        } finally {
            lock.unlock();
        }
    }

    // -------------------------------------------------------------------------
    // 内部：惰性补令牌
    // -------------------------------------------------------------------------

    /**
     * 按时间差补充令牌。
     *
     * <p>公式：新增令牌 = (当前时间 - 上次补充时间) * 每秒令牌数
     * 补充后不超过桶容量 maxPermits。
     */
    private void refill() {
        long now = System.nanoTime();
        long last = lastRefillTimeNanos.get();
        double elapsedSeconds = (now - last) / 1_000_000_000.0;

        if (elapsedSeconds > 0) {
            double newPermits = elapsedSeconds * permitsPerSecond;
            availablePermits = Math.min(maxPermits, availablePermits + newPermits);
            lastRefillTimeNanos.set(now);
        }
    }

    // -------------------------------------------------------------------------
    // 可观测性：统计快照
    // -------------------------------------------------------------------------

    /** 总请求数 */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /** 总拒绝数 */
    public long getTotalRejected() {
        return totalRejected.get();
    }

    /** 当前可用令牌数（近似值，仅用于监控） */
    public double getAvailablePermits() {
        lock.lock();
        try {
            refill();
            return availablePermits;
        } finally {
            lock.unlock();
        }
    }

    /** 每秒允许的调用次数 */
    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }

    @Override
    public String toString() {
        return String.format("RateLimiter{rate=%s/s, available=%s/%s, requests=%s, rejected=%s}",
                permitsPerSecond, String.format("%.1f", getAvailablePermits()),
                maxPermits, getTotalRequests(), getTotalRejected());
    }
}
