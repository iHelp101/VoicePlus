package com.ihelp101.voiceminus.receivers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.ihelp101.voiceminus.XVoicePlusService;

public class MessageEventReceiver extends XVoicePlusReceiver {

    public static final String INCOMING_VOICE = "com.ihelp101.voiceminus.INCOMING_VOICE";
    public static final String OUTGOING_SMS = "com.ihelp101.voiceminus.OUTGOING_SMS";
    public static final String NEW_OUTGOING_SMS = "android.intent.action.NEW_OUTGOING_SMS";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isEnabled(context)) {
            abortBroadcast();
            setResultCode(Activity.RESULT_CANCELED);

            intent.setClass(context, XVoicePlusService.class);
            startWakefulService(context, intent);
        }
    }
}
