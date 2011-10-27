/*
 * Copyright 2011 Takuo Kitame.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.takuo.android.twicca.plugin.evernote;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class TwiccaPluginSettings extends PreferenceActivity implements OnPreferenceChangeListener {
    /** Called when the activity is first created. */
    private ECacheManager cacheManager;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cacheManager = new ECacheManager(getApplicationContext());
        EditTextPreference editPref;
        MultiAutoCompleteEditTextPreference meditPref;
        NotebookPreference notebookPref;
        String crypted;
        String decrypt = "";
        String summary = "";
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        addPreferencesFromResource(R.xml.settings);
        editPref = (EditTextPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_PASSWORD);
        crypted = prefs.getString(TwiccaEvernoteUploader.PREF_EVERNOTE_CRYPTED, "");
        if (crypted.length() > 0) {
            try {
                decrypt = SimpleCrypt.decrypt(TwiccaEvernoteUploader.SEED, crypted);
            } catch (Exception e){
                // do nothing
            }
        }
        for (int i = 0 ; i < decrypt.length(); i++) {
            summary = summary.concat("*");
        }
        editPref.setSummary(summary);
        editPref.setText(decrypt);
        editPref.setDefaultValue(decrypt);
        editPref.setOnPreferenceChangeListener(this);
        editPref = (EditTextPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_USERNAME);
        editPref.setSummary(editPref.getText());
        editPref.setOnPreferenceChangeListener(this);
        notebookPref = (NotebookPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_NOTEBOOK);
        notebookPref.setSummary(notebookPref.getText());
        notebookPref.setOnPreferenceChangeListener(this);
        notebookPref.setNotebookList(cacheManager.getNotebookNames());
        meditPref = (MultiAutoCompleteEditTextPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_TAGS);
        meditPref.setSummary(meditPref.getText());
        meditPref.setOnPreferenceChangeListener(this);
        meditPref.setStringArray(cacheManager.getTagNames());
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        String key = pref.getKey();
        String value = (String)newValue;
        String summary = value;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor e = prefs.edit();
        if (key.equals(TwiccaEvernoteUploader.PREF_EVERNOTE_USERNAME)) {
            ECacheManager.clear(getCacheDir());
        } else if (key.equals(TwiccaEvernoteUploader.PREF_EVERNOTE_PASSWORD)) {
            String encrypted = "";
            try {
                encrypted = SimpleCrypt.encrypt(TwiccaEvernoteUploader.SEED, value);
            } catch (Exception exp) {
                // do nothing
            }
            e.putString(TwiccaEvernoteUploader.PREF_EVERNOTE_CRYPTED, encrypted);
            e.commit();
            summary = "";
            for (int i = 0 ; i < value.length(); i++) {
                summary = summary.concat("*");
            }
        }
        pref.setSummary(summary);
        return true;
    }
}
