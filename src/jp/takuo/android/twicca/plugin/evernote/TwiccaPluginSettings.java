/*
 * Copyright 2012 Takuo Kitame.
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



import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteStore;

import com.evernote.client.conn.ApplicationInfo;
import com.evernote.client.oauth.android.EvernoteSession;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;

public class TwiccaPluginSettings extends PreferenceActivity implements OnPreferenceChangeListener {
    public static final String SHARED_PREF = TwiccaPluginSettings.class.toString();
    /** Called when the activity is first created. */
    private static final String LOG_TAG = TwiccaPluginSettings.class.toString();
    private ECacheManager cacheManager;
    private String mToastMessage;
    private EvernoteSession mSession;
    private Context mContext;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getApplicationContext();
        Intent intent = getIntent();
        boolean reset_auth = intent.getBooleanExtra("reset_auth", false);

        cacheManager = new ECacheManager(mContext);
        Preference pref;
        MultiAutoCompleteEditTextPreference meditPref;
        NotebookPreference notebookPref;
        addPreferencesFromResource(R.xml.settings);

        notebookPref = (NotebookPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_NOTEBOOK);
        notebookPref.setSummary(notebookPref.getText());
        notebookPref.setOnPreferenceChangeListener(this);
        notebookPref.setNotebookList(cacheManager.getNotebookNames());
        meditPref = (MultiAutoCompleteEditTextPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_TAGS);
        meditPref.setSummary(meditPref.getText());
        meditPref.setOnPreferenceChangeListener(this);
        meditPref.setStringArray(cacheManager.getTagNames());

        setupSession(reset_auth);

        pref = findPreference("pref_do_auth");
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
           @Override
           public boolean onPreferenceClick(Preference preference) {
               if (!mSession.isLoggedIn()) {
                   mSession.authenticate(mContext);
               } else {
                   mSession.logOut(getSharedPreferences(SHARED_PREF, MODE_PRIVATE));
               }
               updateui();
               return true;
           }
        });
        Preference preference = findPreference("pref_update_cache");
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new UpdateCacheTask().execute();
                return true;
            }
        });
    }

    private void updateui() {
        Preference pref = findPreference("pref_do_auth");
        if (mSession.isLoggedIn()) {
            pref.setTitle(R.string.pref_auth_authed);
            pref.setSummary(R.string.summary_auth_authed);
            pref = findPreference("pref_update_cache");
            pref.setEnabled(true);
        } else {
            pref.setTitle(R.string.pref_authentication);
            pref.setSummary(R.string.summary_authentication);
            pref = findPreference("pref_update_cache");
            pref.setEnabled(false);
        }
    }

    private void setupSession(boolean reset) {
        ApplicationInfo info =
                new ApplicationInfo(ClippingService.CONSUMER_KEY,
                        ClippingService.CONSUMER_SECRET, ClippingService.EVERNOTE_HOST,
                        ClippingService.APP_NAME, ClippingService.APP_VERSION);
        if (reset) {
            mSession = new EvernoteSession(info,
                    getFilesDir());
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.auth_expired));
            builder.setMessage(getString(R.string.message_auth_expired));
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                   dialog.dismiss();
                }
            });
            builder.create().show();
        } else {
            mSession = new EvernoteSession(info,
                    getSharedPreferences(SHARED_PREF, MODE_PRIVATE),
                    getFilesDir());
        }

        updateui();

        // remove deprecated preferences
        getSharedPreferences(SHARED_PREF, MODE_PRIVATE).edit()
        .remove("pref_evernote_username")
        .remove("pref_evernote_password")
        .remove("pref_evernote_crypted")
        .commit();
    }

    @Override
    public void onResume() {
      super.onResume();
      // Complete the Evernote authentication process if necessary
      if (!mSession.completeAuthentication(getSharedPreferences(SHARED_PREF, MODE_PRIVATE))) {
        // We only want to do this when we're resuming after authentication...
        Toast.makeText(this, "Evernote login failed", Toast.LENGTH_LONG).show();
      }
      updateui();
    }

    @Override
    public boolean onPreferenceChange(Preference pref, Object newValue) {
        String value = (String)newValue;
        String summary = value;
        pref.setSummary(summary);
        return true;
    }

    public class UpdateCacheTask extends AsyncTask<String, String, Boolean> {
        private ProgressDialog mProgressDialog;

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
        protected void onPostExecute (Boolean result) {
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
            if (!result) {
                setupSession(true);
            } else {
                ((NotebookPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_NOTEBOOK)).setNotebookList(cacheManager.getNotebookNames());
                ((MultiAutoCompleteEditTextPreference)findPreference(TwiccaEvernoteUploader.PREF_EVERNOTE_TAGS)).setStringArray(cacheManager.getTagNames());
            }
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            mAuthToken = mSession.getAuthToken();
            return doNoteStore();
        }

        private Boolean doNoteStore() {
            publishProgress(getString(R.string.getting_data));
            for (int i=0; i<5;i++) {
                try {
                    mNoteStore = mSession.createNoteStore();
                    cacheManager.writeNoteCache(mNoteStore.listNotebooks(mAuthToken));
                    cacheManager.writeTagsCache(mNoteStore.listTags(mAuthToken));
                    mToastMessage = null;
                    return true;
                } catch (EDAMUserException eue) {
                    if (eue.isSetErrorCode() && eue.getErrorCode() == EDAMErrorCode.AUTH_EXPIRED) {
                        publishProgress(getString(R.string.auth_expired));
                    }
                    return false;
                } catch (Exception e) {
                    mToastMessage = "Error: " + e.getMessage();
                    Log.e(LOG_TAG, e.getMessage());
                    publishProgress(getString(R.string.getting_data) + "(" +getString(R.string.retry) + ": " + i + ")");
                    return true;
                }
            }
            return true;
        }
    }
}
