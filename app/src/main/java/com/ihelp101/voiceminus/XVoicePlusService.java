package com.ihelp101.voiceminus;

import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.ihelp101.voiceminus.gv.GoogleVoiceManager;
import com.ihelp101.voiceminus.gv.GvResponse.Conversation;
import com.ihelp101.voiceminus.gv.GvResponse.Message;
import com.ihelp101.voiceminus.receivers.BootCompletedReceiver;
import com.ihelp101.voiceminus.receivers.MessageEventReceiver;
import com.ihelp101.voiceminus.receivers.UserPollReceiver;

import java.util.*;


public class XVoicePlusService extends IntentService {
    private static final String TAG = XVoicePlusService.class.getName();

    private static final int VOICE_INCOMING_SMS = 10;
    private static final int VOICE_OUTGOING_SMS = 11;

    private static final int PROVIDER_INCOMING_SMS = 1;
    private static final int PROVIDER_OUTGOING_SMS = 2;

    private static final Uri URI_SENT = Uri.parse("content://sms/sent");
    private static final Uri URI_RECEIVED = Uri.parse("content://sms/inbox");

    private GoogleVoiceManager mGVManager = new GoogleVoiceManager(this);

    public XVoicePlusService() {
        this("XVoicePlusService");
    }

    public XVoicePlusService(String name) {
        super(name);
    }

    private SharedPreferences getAppSettings() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }

    private SharedPreferences getRecentMessages() {
        return getSharedPreferences("recent_messages", MODE_PRIVATE);
    }

    private SharedPreferences getSettings() {
        return getSharedPreferences("com.ihelp101.voiceminus_preferences", Context.MODE_WORLD_READABLE);
    }

    // parse out the intent extras from android.intent.action.NEW_OUTGOING_SMS
    // and send it off via google voice
    private void handleOutgoingSms(Intent intent) {
        boolean multipart = intent.getBooleanExtra("multipart", false);
        String destAddr = intent.getStringExtra("destAddr");
        String scAddr = intent.getStringExtra("scAddr");
        ArrayList<String> parts = intent.getStringArrayListExtra("parts");
        ArrayList<PendingIntent> sentIntents = intent.getParcelableArrayListExtra("sentIntents");
        ArrayList<PendingIntent> deliveryIntents = intent.getParcelableArrayListExtra("deliveryIntents");

        onSendMultipartText(destAddr, scAddr, parts, sentIntents, deliveryIntents, multipart);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        startRefresh();

        if (!getSettings().getBoolean("settings_enabled", false)) {
            return;
        }

        if (MessageEventReceiver.OUTGOING_SMS.equals(intent.getAction())) {
            handleOutgoingSms(intent);
            if(getSettings().getBoolean("settings_sync_on_send", false)) {
                startRefresh();
            }
            MessageEventReceiver.completeWakefulIntent(intent);
        } else if (UserPollReceiver.USER_POLL.equals(intent.getAction())) {
            startRefresh();
            UserPollReceiver.completeWakefulIntent(intent);
        } else if (MessageEventReceiver.INCOMING_VOICE.equals(intent.getAction())) {
            if(getSettings().getBoolean("settings_sync_on_receive", false)) {
                startRefresh();
                clearRecent();
            }
            else {
                synthesizeMessage(intent);
            }
            MessageEventReceiver.completeWakefulIntent(intent);
        } else if (MessageEventReceiver.NEW_OUTGOING_SMS.equals(intent.getAction())){
            handleOutgoingSms(intent);
            if(getSettings().getBoolean("settings_sync_on_send", false)) {
                startRefresh();
            }
            MessageEventReceiver.completeWakefulIntent(intent);
        } else if (BootCompletedReceiver.BOOT_COMPLETED.equals(intent.getAction())) {
            if(getSettings().getBoolean("settings_sync_on_boot", false)) {
                startRefresh();
            }
            BootCompletedReceiver.completeWakefulIntent(intent);
        } else if (GoogleVoiceManager.ACCOUNT_CHANGED.equals(intent.getAction())) {
            mGVManager = new GoogleVoiceManager(this);
        }
    }

    // mark all sent intents as failures
    public static void fail(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si != null){
                try {
                    si.send();
                }
                catch (Exception e) {
                }
            }
        }
    }

    // mark all sent intents as successfully sent
    public void success(List<PendingIntent> sentIntents) {
        if (sentIntents == null)
            return;
        for (PendingIntent si: sentIntents) {
            if (si != null) {
                try {
                    si.send(Activity.RESULT_OK);
                }
                catch (Exception e) {
                }
            }
        }
    }

    // mark an outgoing text as recently sent, so if it comes in via
    // round trip, we ignore it.
    private void addRecent(String text) {
            SharedPreferences savedRecent = getRecentMessages();
            Set<String> recentMessage = savedRecent.getStringSet("recent", new HashSet<String>());
            recentMessage.add(text);
            savedRecent.edit().putStringSet("recent", recentMessage).apply();
    }

    private boolean removeRecent(String text) {
        SharedPreferences savedRecent = getRecentMessages();
        Set<String> recentMessage = savedRecent.getStringSet("recent", new HashSet<String>());
        if (recentMessage.remove(text)) {
            savedRecent.edit().putStringSet("recent", recentMessage).apply();
            return true;
        }
        return false;
    }

    private void clearRecent() {
        getRecentMessages().edit().putStringSet("recent", new HashSet<String>()).apply();
    }

    // send an outgoing sms event via google voice
    private void onSendMultipartText(String destAddr, String scAddr, List<String> texts,
            final List<PendingIntent> sentIntents, final List<PendingIntent> deliveryIntents,
            boolean multipart) {
        // combine the multipart text into one string
        StringBuilder textBuilder = new StringBuilder();
        for (String text: texts) {
            textBuilder.append(text);
        }
        String text = textBuilder.toString();

        try {
            // send it off, and note that we recently sent this message
            // for round trip tracking
            mGVManager.sendGvMessage(destAddr, text);
            if(syncEnabled())addRecent(text);
            success(sentIntents);
            return;
        }
        catch (Exception e) {
            Log.d(TAG, "send error", e);
        }

        try {
            // on failure, fetch info and try again
            mGVManager.refreshAuth();
            mGVManager.sendGvMessage(destAddr, text);
            if(syncEnabled())addRecent(text);
            success(sentIntents);
        }
        catch (Exception e) {
            Log.d(TAG, "send failure", e);
            fail(sentIntents);
        }
    }

    boolean messageExists(Message m, Uri uri) {
        Cursor c = getContentResolver().query(uri, null, "date = ? AND body = ?",
                new String[]{String.valueOf(m.date), m.message}, null);
        if (c != null) {
            try {
                return c.moveToFirst();
            } finally {
                c.close();
            }
        }
        return false;
    }

    void insertMessage(Message m) {
        Uri uri;
        int type;
        if (m.type == VOICE_INCOMING_SMS) {
            uri = URI_RECEIVED;
            type = PROVIDER_INCOMING_SMS;
            m.message = messageWithPrefixSuffix(m.message);
        } else if (m.type == VOICE_OUTGOING_SMS) {
            uri = URI_SENT;
            type = PROVIDER_OUTGOING_SMS;
        } else {
            return;
        }

        if (!messageExists(m, uri)) {
            ContentValues values = new ContentValues();
            values.put("address", m.phoneNumber);
            values.put("body", m.message);
            values.put("type", type);
            values.put("date", m.date);
            values.put("date_sent", m.date);
            values.put("read", m.read);
            getContentResolver().insert(uri, values);
        }
    }

    void synthesizeMessage(Message m) {
        if (!messageExists(m, URI_RECEIVED)) {
            try {
                SmsUtils.createFakeSms(this, m.phoneNumber, messageWithPrefixSuffix(m.message), m.date);
            } catch (Exception e) {
                Log.e(TAG, "IOException when creating fake sms, ignoring " +e);
            }
        }
    }

    void synthesizeMessage(Intent intent) {
        if(MessageEventReceiver.INCOMING_VOICE.equals(intent.getAction())) {
            Message message = new Message();
            message.conversationId = intent.getExtras().getString("conversation_id");
            message.id = intent.getExtras().getString("call_id");
            message.type = VOICE_INCOMING_SMS;
            message.message = intent.getExtras().getString("call_content");
            message.phoneNumber = intent.getExtras().getString("sender_address");
            message.date = Long.valueOf(intent.getExtras().getString("call_time"));
            getAppSettings().edit().putLong("timestamp", message.date).apply();
            clearRecent();
            synthesizeMessage(message);
            try {
                mGVManager.markGvMessageRead(message.id, 1);
            } catch (Exception e) {
                Log.w(TAG, "Error marking message as read. ID: " + message.id, e);
            }
        }
        else Log.w(TAG, "Attempt to synthesize message from unknown/invalid intent");
    }

    private String messageWithPrefixSuffix(String message) {
        SharedPreferences settings = getSettings();
        return String.format(Locale.getDefault(), "%s%s%s",
                settings.getString("settings_incoming_prefix", ""),
                message,
                settings.getString("settings_incoming_suffix", ""));
    }
    
    private void markReadIfNeeded(Message message){
        if (message.read == 0){
            Uri uri;
            if (message.type == VOICE_INCOMING_SMS) {
                uri = URI_RECEIVED;
            } else if (message.type == VOICE_OUTGOING_SMS) {
                uri = URI_SENT;
            } else {
                return;
            }

            Cursor c = getContentResolver().query(uri, null, "date = ? AND body = ?",
                    new String[] { String.valueOf(message.date), message.message }, null);
            try {
                if(c != null && c.moveToFirst()){
                    mGVManager.markGvMessageRead(message.id, 1);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error marking message as read. ID: " + message.id, e);
            } finally {
                if (c != null) {
                    c.close();
                }
            }
        }
    }

    private void updateMessages() throws Exception {
        List<Conversation> conversations = mGVManager.retrieveMessages();

        long timestamp = getAppSettings().getLong("timestamp", 0);
        LinkedList<Message> newMessages = new LinkedList<Message>();
        for (Conversation conversation: conversations) {
            for (Message m : conversation.messages) {
                if(m.phoneNumber != null && m.message != null) {
                    markReadIfNeeded(m);
                    if (m.date > timestamp) {
                        newMessages.add(m);
                    }
                }
            }
        }

        // sort by date order so the events get added in the same order
        Collections.sort(newMessages, new Comparator<Message>() {
            @Override
            public int compare(Message lhs, Message rhs) {
                return Long.valueOf(lhs.date).compareTo(rhs.date);
            }
        });

        long max = timestamp;
        for (Message message : newMessages) {
            max = Math.max(max, message.date);

            // on first sync, just populate the mms provider...
            // don't send any broadcasts.
            if (timestamp == 0) {
                insertMessage(message);
                continue;
            }

            // sync up outgoing messages
            if (message.type == VOICE_OUTGOING_SMS) {
                if (!removeRecent(message.message)) {
                    insertMessage(message);
                }
            } else if (message.type == VOICE_INCOMING_SMS) {
                Set<String> recentPushMessages = getRecentMessages().getStringSet("push_messages", new HashSet<String>());
                if (recentPushMessages.remove(message.id)) {
                    // We already synthesized this message
                    getRecentMessages().edit().putStringSet("push_messages", recentPushMessages);
                } else {
                    synthesizeMessage(message);
                }
            }
        }
        getAppSettings().edit().putLong("timestamp", max).apply();
    }

    void startRefresh() {
        try {
            updateMessages();
        } catch (Exception e) {
            Log.e(TAG, "Error refreshing messages", e);
        }
    }

    public boolean syncEnabled() {
        return(getSettings().getBoolean("settings_sync_on_receive", false) | getSettings().getBoolean("settings_sync_on_send", false) | Long.valueOf(getSettings().getString("settings_polling_frequency", "-1")) != -1L);
    }
}
