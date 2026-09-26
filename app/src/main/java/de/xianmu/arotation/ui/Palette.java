package de.xianmu.arotation.ui;

import android.content.Context;
import android.content.res.Configuration;

import de.xianmu.arotation.R;

/** Exact Radix Colors tokens. Attribution and pinned upstream values live in third_party/. */
public final class Palette {
    public final boolean dark;
    public final int background,surface,ink,muted,accent,onAccent,tonal,line,error,button,buttonInk,selectedText;
    public Palette(Context c) {
        dark=(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        background=c.getColor(R.color.radix_slate1);surface=c.getColor(R.color.radix_slate3);
        ink=c.getColor(R.color.radix_slate12);muted=c.getColor(R.color.radix_slate11);
        accent=c.getColor(R.color.radix_blue11);onAccent=c.getColor(R.color.radix_slate1);
        tonal=c.getColor(R.color.radix_blue3);line=c.getColor(R.color.radix_slate6);
        error=c.getColor(R.color.radix_red11);selectedText=c.getColor(dark?R.color.radix_blue11:R.color.radix_blue12);
        button=dark?c.getColor(R.color.radix_slate3):ink;
        buttonInk=dark?ink:background;
    }
}
