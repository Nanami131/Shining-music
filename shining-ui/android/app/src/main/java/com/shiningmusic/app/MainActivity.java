package com.shiningmusic.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(SongSharePlugin.class);
        super.onCreate(savedInstanceState);
    }
}
