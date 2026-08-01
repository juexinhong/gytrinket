package com.gytrinket.gytrinket.core.entity.construct.wingman.attack_mode;

import com.gytrinket.gytrinket.gytrinket;
import com.gytrinket.gytrinket.core.entity.construct.wingman.WingmanConstructEntity;

public class InterceptorDebug {
    private static final boolean DEBUG = false;

    public static void logAttackStep(WingmanConstructEntity wingman, String step, String msg) {
        if (DEBUG) gytrinket.LOGGER.info("[Interceptor] {} {} | {}", wingman.getId(), step, msg);
    }

    public static void logAttackResult(WingmanConstructEntity wingman, String msg) {
        if (DEBUG) gytrinket.LOGGER.info("[Interceptor] {} result | {}", wingman.getId(), msg);
    }

    public static void logStateChange(WingmanConstructEntity wingman, String msg) {
        if (DEBUG) gytrinket.LOGGER.info("[Interceptor] {} state | {}", wingman.getId(), msg);
    }

    public static void logSlow(WingmanConstructEntity wingman, String key, String msg) {
        if (DEBUG) gytrinket.LOGGER.info("[Interceptor] {} slow | {} - {}", wingman.getId(), key, msg);
    }

    public static void logFast(WingmanConstructEntity wingman, String key, String msg) {
        if (DEBUG) gytrinket.LOGGER.info("[Interceptor] {} fast | {} - {}", wingman.getId(), key, msg);
    }
}
