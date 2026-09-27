package com.glint.server.biz.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 通勤破冰话题题库；每次随机抽一条，尽量不与上一条相同。 */
public final class TopicRescuePrompts {

    static final List<String> TEMPLATES = List.of(
            "路过{station}的时候，你会戴耳机还是看看窗外？",
            "{station}这一段，你一般用来干嘛？发呆、补觉，还是回消息？",
            "如果下一站能多停一会儿，你会用来干嘛？",
            "今天这趟车，有没有哪一站让你想提前下车透透气？",
            "通勤最舒服的是早高峰还是晚高峰？你站哪边？",
            "如果只能带一样东西上车，你会带什么？",
            "{station}附近有没有你常买的早餐或咖啡？",
            "这趟车上你更想安静坐着，还是随便聊两句？",
            "如果今天可以改走另一条线，你会换吗？",
            "你平时几点出门最不容易挤？",
            "车上最怕遇到什么：突然刹车、信号消失，还是门要关了还在跑？",
            "{traffic}的时候，你会继续刷手机还是发呆看广告？"
    );

    private TopicRescuePrompts() {
    }

    public static String pick(String stationName, String trafficSummary, String previous) {
        String station = blankTo(stationName, "这一站");
        String traffic = genericTraffic(trafficSummary) ? "车有点慢" : trafficSummary.trim();
        List<String> rendered = new ArrayList<>();
        for (String template : TEMPLATES) {
            rendered.add(template.replace("{station}", station).replace("{traffic}", traffic));
        }
        List<String> pool = rendered.stream().filter(item -> !item.equals(previous)).toList();
        if (pool.isEmpty()) {
            pool = rendered;
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static boolean genericTraffic(String trafficSummary) {
        if (trafficSummary == null || trafficSummary.isBlank()) {
            return true;
        }
        String trimmed = trafficSummary.trim();
        return trimmed.equals("正常运行") || trimmed.equals("通勤路上");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
