package com.gy_mod.gy_trinket.core.entity.construct;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConstructAttributeTarget {

    public enum EffectType {
        HEALTH,
        DAMAGE,
        ATTACK_SPEED,
        BUILD_SPEED,
        MAX_COUNT
    }

    private final Set<ConstructCategory> categories;
    private final Set<String> tags;
    private final EffectType effectType;

    private ConstructAttributeTarget(Set<ConstructCategory> categories, Set<String> tags, EffectType effectType) {
        this.categories = Collections.unmodifiableSet(new HashSet<>(categories));
        this.tags = Collections.unmodifiableSet(new HashSet<>(tags));
        this.effectType = effectType;
    }

    public Set<ConstructCategory> getCategories() {
        return categories;
    }

    public Set<String> getTags() {
        return tags;
    }

    public EffectType getEffectType() {
        return effectType;
    }

    public boolean matches(ConstructType type, Set<String> instanceTags) {
        if (!categories.isEmpty() && !type.matchesCategories(categories)) {
            return false;
        }
        if (!tags.isEmpty()) {
            Set<String> allTags = new HashSet<>(type.getTags());
            if (instanceTags != null) {
                allTags.addAll(instanceTags);
            }
            if (!allTags.containsAll(tags)) {
                return false;
            }
        }
        return true;
    }

    public boolean matches(ConstructType type) {
        return matches(type, Collections.emptySet());
    }

    public static Builder builder(EffectType effectType) {
        return new Builder(effectType);
    }

    public static class Builder {
        private final Set<ConstructCategory> categories = new HashSet<>();
        private final Set<String> tags = new HashSet<>();
        private final EffectType effectType;

        public Builder(EffectType effectType) {
            this.effectType = effectType;
        }

        public Builder category(ConstructCategory category) {
            this.categories.add(category);
            return this;
        }

        public Builder categories(Set<ConstructCategory> categories) {
            this.categories.addAll(categories);
            return this;
        }

        public Builder tag(String tag) {
            this.tags.add(tag);
            return this;
        }

        public Builder tags(Set<String> tags) {
            this.tags.addAll(tags);
            return this;
        }

        public ConstructAttributeTarget build() {
            return new ConstructAttributeTarget(categories, tags, effectType);
        }
    }
}
