package com.verve.audiotoggle.killeh;

import android.content.Context;

public class AudioToggle {
    private final Context context;

    public AudioToggle(Context context) {
        this.context = context;
    }

    public Context getContext() {
        return this.context;
    }
    public String setAudioMode(String value) {
        return value;
    }
}
