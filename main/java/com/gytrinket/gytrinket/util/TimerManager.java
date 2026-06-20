package com.gytrinket.gytrinket.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TimerManager {
    private static final TimerManager INSTANCE = new TimerManager();

    private final Map<UUID, Map<String, Map<String, Long>>> timers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<String, Long>>> totalTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Map<String, Integer>>> finishedTimers = new ConcurrentHashMap<>();

    private TimerManager() {
    }

    public static TimerManager getInstance() {
        return INSTANCE;
    }

    public void createTimer(UUID uuid, String timerType, String timerName, long durationMillis) {
        long endTime = System.nanoTime() + durationMillis * 1_000_000;

        Map<String, Map<String, Integer>> uuidFinishedTimers = finishedTimers.get(uuid);
        if (uuidFinishedTimers != null) {
            Map<String, Integer> typeFinishedTimers = uuidFinishedTimers.get(timerType);
            if (typeFinishedTimers != null) {
                typeFinishedTimers.remove(timerName);
                if (typeFinishedTimers.isEmpty()) {
                    uuidFinishedTimers.remove(timerType);
                    if (uuidFinishedTimers.isEmpty()) {
                        finishedTimers.remove(uuid);
                    }
                }
            }
        }

        timers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
              .computeIfAbsent(timerType, k -> new ConcurrentHashMap<>())
              .put(timerName, endTime);

        totalTimes.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                 .computeIfAbsent(timerType, k -> new ConcurrentHashMap<>())
                 .put(timerName, durationMillis);
    }

    public boolean isTimerFinished(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Integer>> uuidFinishedTimers = finishedTimers.get(uuid);
        if (uuidFinishedTimers != null) {
            Map<String, Integer> typeFinishedTimers = uuidFinishedTimers.get(timerType);
            if (typeFinishedTimers != null && typeFinishedTimers.containsKey(timerName)) {
                return false;
            }
        }

        Map<String, Map<String, Long>> uuidTimers = timers.get(uuid);
        if (uuidTimers == null) {
            return true;
        }

        Map<String, Long> typeTimers = uuidTimers.get(timerType);
        if (typeTimers == null) {
            return true;
        }

        Long endTime = typeTimers.get(timerName);
        if (endTime == null) {
            return true;
        }

        boolean isFinished = System.nanoTime() >= endTime;
        if (isFinished) {
            finishedTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(timerType, k -> new ConcurrentHashMap<>())
                        .put(timerName, 1);

            typeTimers.remove(timerName);
            if (typeTimers.isEmpty()) {
                uuidTimers.remove(timerType);
                if (uuidTimers.isEmpty()) {
                    timers.remove(uuid);
                }
            }

            Map<String, Map<String, Long>> uuidTotalTimes = totalTimes.get(uuid);
            if (uuidTotalTimes != null) {
                Map<String, Long> typeTotalTimes = uuidTotalTimes.get(timerType);
                if (typeTotalTimes != null) {
                    typeTotalTimes.remove(timerName);
                    if (typeTotalTimes.isEmpty()) {
                        uuidTotalTimes.remove(timerType);
                        if (uuidTotalTimes.isEmpty()) {
                            totalTimes.remove(uuid);
                        }
                    }
                }
            }
        }

        return isFinished;
    }

    public int checkTimerStatus(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Integer>> uuidFinishedTimers = finishedTimers.get(uuid);
        if (uuidFinishedTimers != null) {
            Map<String, Integer> typeFinishedTimers = uuidFinishedTimers.get(timerType);
            if (typeFinishedTimers != null && typeFinishedTimers.containsKey(timerName)) {
                return 2;
            }
        }

        Map<String, Map<String, Long>> uuidTimers = timers.get(uuid);
        if (uuidTimers == null) {
            return 2;
        }

        Map<String, Long> typeTimers = uuidTimers.get(timerType);
        if (typeTimers == null) {
            return 2;
        }

        Long endTime = typeTimers.get(timerName);
        if (endTime == null) {
            return 2;
        }

        boolean isFinished = System.nanoTime() >= endTime;
        if (isFinished) {
            finishedTimers.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(timerType, k -> new ConcurrentHashMap<>())
                        .put(timerName, 1);

            typeTimers.remove(timerName);
            if (typeTimers.isEmpty()) {
                uuidTimers.remove(timerType);
                if (uuidTimers.isEmpty()) {
                    timers.remove(uuid);
                }
            }

            Map<String, Map<String, Long>> uuidTotalTimes = totalTimes.get(uuid);
            if (uuidTotalTimes != null) {
                Map<String, Long> typeTotalTimes = uuidTotalTimes.get(timerType);
                if (typeTotalTimes != null) {
                    typeTotalTimes.remove(timerName);
                    if (typeTotalTimes.isEmpty()) {
                        uuidTotalTimes.remove(timerType);
                        if (uuidTotalTimes.isEmpty()) {
                            totalTimes.remove(uuid);
                        }
                    }
                }
            }

            return 1;
        }

        return 0;
    }

    public boolean isTimerActive(UUID uuid, String timerType, String timerName) {
        return !isTimerFinished(uuid, timerType, timerName);
    }

    public long getCurrentTimeMillis() {
        return System.nanoTime() / 1_000_000;
    }

    public long getRemainingTimeMillis(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Long>> uuidTimers = timers.get(uuid);
        if (uuidTimers == null) {
            return 0;
        }

        Map<String, Long> typeTimers = uuidTimers.get(timerType);
        if (typeTimers == null) {
            return 0;
        }

        Long endTime = typeTimers.get(timerName);
        if (endTime == null) {
            return 0;
        }

        long remainingNanos = endTime - System.nanoTime();
        if (remainingNanos <= 0) {
            typeTimers.remove(timerName);
            if (typeTimers.isEmpty()) {
                uuidTimers.remove(timerType);
                if (uuidTimers.isEmpty()) {
                    timers.remove(uuid);
                }
            }

            Map<String, Map<String, Long>> uuidTotalTimes = totalTimes.get(uuid);
            if (uuidTotalTimes != null) {
                Map<String, Long> typeTotalTimes = uuidTotalTimes.get(timerType);
                if (typeTotalTimes != null) {
                    typeTotalTimes.remove(timerName);
                    if (typeTotalTimes.isEmpty()) {
                        uuidTotalTimes.remove(timerType);
                        if (uuidTotalTimes.isEmpty()) {
                            totalTimes.remove(uuid);
                        }
                    }
                }
            }
            return 0;
        }

        return remainingNanos / 1_000_000;
    }

    public long getTotalTimeMillis(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Long>> uuidTotalTimes = totalTimes.get(uuid);
        if (uuidTotalTimes == null) {
            return 0;
        }

        Map<String, Long> typeTotalTimes = uuidTotalTimes.get(timerType);
        if (typeTotalTimes == null) {
            return 0;
        }

        return typeTotalTimes.getOrDefault(timerName, 0L);
    }

    public void cancelTimer(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Long>> uuidTimers = timers.get(uuid);
        if (uuidTimers != null) {
            Map<String, Long> typeTimers = uuidTimers.get(timerType);
            if (typeTimers != null) {
                typeTimers.remove(timerName);
                if (typeTimers.isEmpty()) {
                    uuidTimers.remove(timerType);
                    if (uuidTimers.isEmpty()) {
                        timers.remove(uuid);
                    }
                }
            }
        }

        Map<String, Map<String, Long>> uuidTotalTimes = totalTimes.get(uuid);
        if (uuidTotalTimes != null) {
            Map<String, Long> typeTotalTimes = uuidTotalTimes.get(timerType);
            if (typeTotalTimes != null) {
                typeTotalTimes.remove(timerName);
                if (typeTotalTimes.isEmpty()) {
                    uuidTotalTimes.remove(timerType);
                    if (uuidTotalTimes.isEmpty()) {
                        totalTimes.remove(uuid);
                    }
                }
            }
        }

        Map<String, Map<String, Integer>> uuidFinishedTimers = finishedTimers.get(uuid);
        if (uuidFinishedTimers != null) {
            Map<String, Integer> typeFinishedTimers = uuidFinishedTimers.get(timerType);
            if (typeFinishedTimers != null) {
                typeFinishedTimers.remove(timerName);
                if (typeFinishedTimers.isEmpty()) {
                    uuidFinishedTimers.remove(timerType);
                    if (uuidFinishedTimers.isEmpty()) {
                        finishedTimers.remove(uuid);
                    }
                }
            }
        }
    }

    public void clearAllTimers(UUID uuid) {
        timers.remove(uuid);
        totalTimes.remove(uuid);
        finishedTimers.remove(uuid);
    }

    public void decrementFinishedMarker(UUID uuid, String timerType, String timerName) {
        Map<String, Map<String, Integer>> uuidFinishedTimers = finishedTimers.get(uuid);
        if (uuidFinishedTimers != null) {
            Map<String, Integer> typeFinishedTimers = uuidFinishedTimers.get(timerType);
            if (typeFinishedTimers != null) {
                Integer currentValue = typeFinishedTimers.get(timerName);
                if (currentValue != null) {
                    int newValue = currentValue - 1;
                    if (newValue <= 0) {
                        typeFinishedTimers.remove(timerName);
                        if (typeFinishedTimers.isEmpty()) {
                            uuidFinishedTimers.remove(timerType);
                            if (uuidFinishedTimers.isEmpty()) {
                                finishedTimers.remove(uuid);
                            }
                        }
                    } else {
                        typeFinishedTimers.put(timerName, newValue);
                    }
                }
            }
        }
    }
}
