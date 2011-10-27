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

import org.apache.thrift.protocol.TBinaryProtocol;

import com.evernote.android.edam.TAndroidHttpClient;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.User;
import com.evernote.edam.userstore.AuthenticationResult;
import com.evernote.edam.userstore.UserStore;

import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class TwiccaPluginSettings extends PreferenceActivity implements OnPreferenceChangeListener {
    /** Called when the activity is first created. */
    private static final String LOG_TAG = TwiccaPluginSettings.class.toString();
    private ECacheManager cacheManager;
    private String mToastMessage;

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
        Preference preference = findPreference("pref_update_cache");
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                String username =
                    prefs.getString(TwiccaEvernoteUploader.PREF_EVERNOTE_USERNAME, "");
                String password =
                    prefs.getString(TwiccaEvernoteUploader.PREF_EVERNOTE_CRYPTED, "");
                if (username.length() == 0 || password.length() == 0) return false;
                try {
                    password = SimpleCrypt.decrypt(TwiccaEvernoteUploader.SEED, password);
                } catch (Exception e) {
                    // FIXME:
                    return false;
                }
                UpdateCacheTask task = new UpdateCacheTask();
                task.execute(username, password);
                return false;
            }
        });
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

    public class UpdateCacheTask extends AsyncTask<String, String, Void> {
        private ProgressDialog mProgressDialog;

        private User mUser;
        private UserStore.Client mUserStore;
        private NoteStore.Client mNoteStore;
        private String mAuthToken;

        public UpdateCacheTask() {
            mProgressDialog = new ProgressDialog(TwiccaPluginSettings.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }

        @Override
        protected void onPreExecute () {
            super.onPreExecute();
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setTitle(R.string.update_cache);
            mProgressDialog.show();
            mToastMessage = null;
        }

        @Override
        protected void onPostExecute (Void result) {
            super.onPostExecute(result);
            try {
                if(mProgressDialog != null &&
                        mProgressDialog.isShowing()) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            } catch (Exception e) {
            }
            if (mToastMessage != null) {
                Toast.makeText(getApplicationContext(), mToastMessage, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        @Override
        protected Void doInBackground(String... params) {
            String username = params[0];
            String password = params[1];
            if (doAuth(username, password)) {
                doNoteStore();
            }
            return null;
        }

        private void doNoteStore() {
            publishProgress(getString(R.string.getting_data));
            for (int i=0; i<5;i++) {
                try {
                    String noteStoreUrl = ClippingService.NOTESTORE_URL_BASE + mUser.getShardId();
                    TAndroidHttpClient noteStoreTrans =
                        new TAndroidHttpClient(noteStoreUrl, ClippingService.USER_AGENT,
                                getFilesDir());
                    TBinaryProtocol noteStoreProt = new TBinaryProtocol(noteStoreTrans);
                    mNoteStore = new NoteStore.Client(noteStoreProt, noteStoreProt);
                    cacheManager.writeNoteCache(mNoteStore.listNotebooks(mAuthToken));
                    cacheManager.writeTagsCache(mNoteStore.listTags(mAuthToken));
                    mToastMessage = null;
                    return;
                } catch (Exception e) {
                    mToastMessage = "Error: " + e.getMessage();
                    Log.e(LOG_TAG, e.getMessage());
                    publishProgress(getString(R.string.getting_data) + "(" +getString(R.string.retry) + ": " + i + ")");
                }
            }
        }

        private boolean doAuth(String username, String password) {
            publishProgress(getString(R.string.authentication));
            for (int i = 0 ; i < 5 ; i++) {
                try {
                    AuthenticationResult authResult;
                    TAndroidHttpClient userStoreTrans =
                        new TAndroidHttpClient(ClippingService.USERSTORE_URL,
                                ClippingService.USER_AGENT, getFilesDir());
                    TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
                    mUserStore = new UserStore.Client(userStoreProt, userStoreProt);

                    boolean versionOk = mUserStore.checkVersion("twiccaEvernotePlugin (EDAM Android)",
                            com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                            com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);
                    if (!versionOk) {
                       mToastMessage = getString(R.string.message_error_version);
                        return false;
                    } // if versionOK

                    try {
                        authResult = mUserStore.authenticate(username, password,
                                ClippingService.CONSUMER_KEY, ClippingService.CONSUMER_SECRET);
                    } catch (EDAMUserException ex) {
                        mToastMessage= getString(R.string.message_error_auth);
                        return false;
                    } // try
                    mUser = authResult.getUser();
                    mAuthToken = authResult.getAuthenticationToken();
                    cacheManager.writeAuthCache(authResult.getAuthenticationToken(), authResult.getExpiration());
                    return true;
                } catch (Exception e) {
                    mToastMessage = "Error: " + e.getMessage();
                    Log.e(LOG_TAG, e.getMessage());
                    publishProgress(getString(R.string.authentication) + "(" +getString(R.string.retry) + ": " + i + ")");
                } // try
            }
            return false;
        }
    }
}
