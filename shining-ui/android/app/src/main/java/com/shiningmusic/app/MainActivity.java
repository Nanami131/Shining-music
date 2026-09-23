package com.shiningmusic.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SongSharePlugin.class);
        registerPlugin(OfflineAudioPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
