package com.talksy.messenger.calls;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallHangupReceiver extends BroadcastReceiver {

    public static final String ACTION_HANGUP = "com.talksy.messenger.CALL_SERVICE_HANGUP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_HANGUP.equals(intent.getAction())) return;
        CallService svc = CallService.current;
        if (svc != null) svc.hangUp();
    }
}
