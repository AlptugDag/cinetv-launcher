package com.cinetv.launcher.service;

import android.graphics.drawable.Drawable;

/** An app on the home dock or grid. The artwork is filled in by the UI layer. */
public class AppTileItem {
    public String packageName;
    public String label;
    public Drawable bannerDrawable;
    public int neonColor;
}
