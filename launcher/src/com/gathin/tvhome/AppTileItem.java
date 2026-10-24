package com.gathin.tvhome;

import android.graphics.drawable.Drawable;

/**
 * Data model representing an application tile on the CineTV Launcher dock or grid.
 */
public class AppTileItem {
    public String packageName;
    public String label;
    public Drawable bannerDrawable;
    public int neonColor;

    public AppTileItem() {}

    public AppTileItem(String label, String packageName, Drawable bannerDrawable, int neonColor) {
        this.label = label;
        this.packageName = packageName;
        this.bannerDrawable = bannerDrawable;
        this.neonColor = neonColor;
    }
}
