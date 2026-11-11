package com.cinetv.launcher.ui;

/** Navigation and layout math for circular category shelves and growing search shelves. */
final class ShelfPosition {

    private ShelfPosition() {}

    static int target(int current, int delta, int count, boolean circular) {
        if (count <= 0) return 0;
        int next = current + delta;
        return circular ? next : Math.max(0, Math.min(count - 1, next));
    }

    static int index(int target, int count, boolean circular) {
        if (count <= 0) return 0;
        return circular ? Math.floorMod(target, count) : Math.max(0, Math.min(count - 1, target));
    }

    static float distance(int slot, float focus, int count, boolean circular) {
        float distance = slot - focus;
        if (circular && count > 0) {
            distance -= count * (float) Math.floor((distance + count / 2f) / count);
        }
        return distance;
    }
}
