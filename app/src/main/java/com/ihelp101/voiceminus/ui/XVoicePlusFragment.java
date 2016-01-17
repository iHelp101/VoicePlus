package com.ihelp101.voiceminus.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.*;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.ihelp101.voiceminus.R;
import com.ihelp101.voiceminus.XVoicePlusService;

public class XVoicePlusFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);
        Activity act = getActivity();
        if (act != null) {
            act.startService(new Intent(getActivity(), XVoicePlusService.class));
        }
    }

    @Override
    public View onCreateView(LayoutInflater li, ViewGroup vg, Bundle b) {
        View result = super.onCreateView(li, vg, b);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        for (int i = 0; i < preferenceScreen.getPreferenceCount(); i++) {
            String key = preferenceScreen.getPreference(i).getKey();
            updateSummary(key);
        }

        return result;
    }

    public void updateSummary(String key) {
        Preference pref = findPreference(key);

        if (pref instanceof ListPreference) {
            ListPreference listPref = (ListPreference) pref;
            listPref.setSummary(listPref.getEntry());
        } else if (pref instanceof EditTextPreference) {
            EditTextPreference textPref = (EditTextPreference) pref;
            textPref.setSummary(textPref.getText());
        }
    }
}
