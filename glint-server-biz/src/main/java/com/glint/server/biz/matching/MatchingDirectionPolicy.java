package com.glint.server.biz.matching;

/**
 * 方向置信策略：当前摘要只有标量置信、没有进出站/上下行轴，因此无法判定「反向」。
 * 双方均达到高置信时视为方向可信，用于同站层加权；冲突判定预留扩展点。
 */
public final class MatchingDirectionPolicy {

    public static final double HIGH_CONFIDENCE = 0.90;
    public static final double WEAK_CONFIDENCE = 0.50;

    private MatchingDirectionPolicy() {
    }

    /** 同站层：若双方都高置信且判定冲突，则不应进入同站层。当前永不判定冲突。 */
    public static boolean blockSameStationDueToOpposite(double mine, double peer) {
        return bothHighConfidence(mine, peer) && isOppositeDirection(mine, peer);
    }

    public static boolean bothHighConfidence(double mine, double peer) {
        return mine >= HIGH_CONFIDENCE && peer >= HIGH_CONFIDENCE;
    }

    /**
     * 预留：未来若摘要增加方向桶（上行/下行），在此比较。
     * 仅有标量置信时 MUST 返回 false，避免误杀同站候选。
     */
    public static boolean isOppositeDirection(double mine, double peer) {
        return false;
    }

    public static boolean hasUsableConfidence(double confidence) {
        return confidence >= WEAK_CONFIDENCE;
    }
}
