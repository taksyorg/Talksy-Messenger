package com.talksy.messenger;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.talksy.messenger.net.ApiClient;
import com.talksy.messenger.ui.CallActivity;

public abstract class BaseActivity extends AppCompatActivity {

    private final Handler callHandler = new Handler(Looper.getMainLooper());
    private ApiClient callApi;

    private final Runnable callPoll = new Runnable() {
        @Override public void run() {
            if (!CallActivity.isShowing) {
                callApi.getIncomingCall((ok, call, error) -> {
                    if (ok && call != null && !CallActivity.isShowing) {
                        Intent i = new Intent(BaseActivity.this, CallActivity.class);
                        i.putExtra(CallActivity.EXTRA_MODE, CallActivity.MODE_INCOMING);
                        i.putExtra(CallActivity.EXTRA_CALL_ID, call.id);
                        i.putExtra(CallActivity.EXTRA_PEER_ID, call.callerId);
                        i.putExtra(CallActivity.EXTRA_PEER_NAME, call.callerName);
                        i.putExtra(CallActivity.EXTRA_PEER_COLOR, call.callerColor);
                        i.putExtra(CallActivity.EXTRA_VIDEO, "video".equals(call.callType));
                        startActivity(i);
                    }
                });
            }
            callHandler.postDelayed(this, 4000);
        }
    };

    protected boolean listensForCalls() { return false; }

    @Override
    protected void onStart() {
        super.onStart();
        com.talksy.messenger.data.AppLock.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.talksy.messenger.data.AppLock.onActivityStopped();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!(this instanceof com.talksy.messenger.ui.LockActivity)
                && com.talksy.messenger.data.AppLock.shouldLock(this)) {
            startActivity(new Intent(this, com.talksy.messenger.ui.LockActivity.class)
                    .putExtra(com.talksy.messenger.ui.LockActivity.EXTRA_MODE, com.talksy.messenger.ui.LockActivity.MODE_UNLOCK));
            overridePendingTransition(0, 0);
        }
        if (listensForCalls()) {
            if (callApi == null) callApi = new ApiClient(this);
            callHandler.removeCallbacks(callPoll);
            callHandler.postDelayed(callPoll, 1500);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        callHandler.removeCallbacks(callPoll);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(SplashActivity.LocaleHelper.wrap(newBase));
    }
}
