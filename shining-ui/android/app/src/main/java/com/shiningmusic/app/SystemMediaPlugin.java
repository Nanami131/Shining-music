package com.shiningmusic.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.PermissionState;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.lang.ref.WeakReference;

/** Bridges the existing web audio player to Android's system media session. */
@CapacitorPlugin(name = "SystemMedia", permissions = {
    @Permission(alias = "notifications", strings = { Manifest.permission.POST_NOTIFICATIONS })
})
public class SystemMediaPlugin extends Plugin {
    private static WeakReference<SystemMediaPlugin> instance = new WeakReference<>(null);
    private boolean notificationPermissionRequested;

    @Override public void load() {
        instance = new WeakReference<>(this);
    }

    @Override protected void handleOnDestroy() {
        if (instance.get() == this) instance.clear();
        super.handleOnDestroy();
    }

    static void sendCommand(String command, long positionMs) {
        SystemMediaPlugin plugin = instance.get();
        if (plugin == null) return;
        plugin.getActivity().runOnUiThread(() -> {
            JSObject event = new JSObject();
            event.put("command", command);
            event.put("positionMs", positionMs);
            plugin.notifyListeners("mediaCommand", event);
        });
    }

    @PluginMethod public void update(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionRequested &&
                getPermissionState("notifications") != PermissionState.GRANTED) {
            notificationPermissionRequested = true;
            requestPermissionForAlias("notifications", call, "onNotificationPermission");
            return;
        }
        updateSession(call);
    }

    @PermissionCallback
    private void onNotificationPermission(PluginCall call) {
        updateSession(call);
    }

    private void updateSession(PluginCall call) {
        boolean playing = Boolean.TRUE.equals(call.getBoolean("playing"));
        if (!playing && !SystemMediaService.isRunning()) {
            call.resolve();
            return;
        }
        Intent intent = new Intent(getContext(), SystemMediaService.class);
        intent.setAction(SystemMediaService.ACTION_UPDATE);
        intent.putExtra("title", call.getString("title", "Shining Music"));
        intent.putExtra("artist", call.getString("artist", ""));
        // JavaScript integers may arrive as Integer rather than Long in the bridge.
        intent.putExtra("durationMs", Math.max(0L, call.getData().optLong("durationMs", 0L)));
        intent.putExtra("positionMs", Math.max(0L, call.getData().optLong("positionMs", 0L)));
        intent.putExtra("playing", playing);
        try {
            if (playing && !SystemMediaService.isRunning() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(intent);
            } else {
                getContext().startService(intent);
            }
            call.resolve();
        } catch (Exception error) {
            call.reject("无法更新系统媒体播放卡片", error);
        }
    }

    @PluginMethod public void stop(PluginCall call) {
        getContext().stopService(new Intent(getContext(), SystemMediaService.class));
        call.resolve();
    }
}
