package com.ppdv.api.track.service;

import com.ppdv.api.track.dto.TrackClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 车端 YOLO 15 类类别目录（临时常量，P1 用）。
 *
 * <p>TODO: 替换为 Q1 的类别注册表（落地后删除本类）。
 * 基础分为占位初值（H × R），须经业务评审确认后，影子分对比数据才有意义。
 * 6 个物候类不参与评分，基础分为 0。</p>
 */
public final class TrackClassCatalog {

    /** 未知 class 的显式占位类，不映射到任何旧/新类。 */
    public static final String UNKNOWN = "__unknown__";

    /** 旧 5 类中文名一律拒识，不做新旧映射。 */
    private static final Set<String> RETIRED = Set.of("虫害", "病斑", "萎蔫", "黄化", "斑点");

    /** 类别 key → {中文 label, H×R 基础分（占位）}；插入序即展示序，单一清单避免双份维护漂移。 */
    private static final Map<String, TrackClass> CLASSES = new LinkedHashMap<>();

    static {
        register("fruit_immature", "未成熟果实", 0);
        register("fruit_half_ripe", "半成熟果实（转色期）", 0);
        register("fruit_ripe", "成熟果实", 0);
        register("flower_bud", "花蕾期", 0);
        register("flower_anthesis", "开花期", 0);
        register("flower_post_anthesis", "花后期", 0);
        register("leaf_early_blight", "早疫病叶片", 45);
        register("leaf_late_blight", "晚疫病叶片", 55);
        register("leaf_miner", "潜叶虫害叶片", 26);
        register("leaf_mold", "叶霉病叶片", 34);
        register("leaf_mosaic_virus", "花叶病毒病叶片", 55);
        register("leaf_septoria", "斑枯病叶片", 30);
        register("leaf_spider_mites", "红蜘蛛危害叶片", 48);
        register("leaf_yellow_leaf_curl_virus", "黄化曲叶病毒病叶片", 65);
        register("leaf_bacterial_spot", "细菌性斑点病叶片", 47);
    }

    private static void register(String key, String label, int baseScore) {
        CLASSES.put(key, new TrackClass(key, label, baseScore));
    }

    private TrackClassCatalog() {
    }

    public static boolean isRetired(String category) {
        return RETIRED.contains(category);
    }

    public static boolean contains(String category) {
        return CLASSES.containsKey(category);
    }

    /** 参与评分的类别基础分；物候类/未知类返回 0。 */
    public static int baseScore(String category) {
        TrackClass c = CLASSES.get(category);
        return c == null ? 0 : c.baseScore();
    }

    /** 类别目录（前端类别下拉与 label 共用，避免前端复制词表）。 */
    public static List<TrackClass> classes() {
        return List.copyOf(CLASSES.values());
    }
}
