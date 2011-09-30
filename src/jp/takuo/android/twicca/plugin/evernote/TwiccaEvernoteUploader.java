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

/* from EDAM sample */
import java.util.List;
import java.lang.System;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransportException;

import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.User;
import com.evernote.edam.userstore.AuthenticationResult;
import com.evernote.edam.userstore.Constants;
import com.evernote.edam.userstore.UserStore;

import com.evernote.android.edam.TAndroidHttpClient;

/* Android */
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.Toast;

public class TwiccaEvernoteUploader extends Activity {
    public static final String SEED = "encrypt";
    private static final String LOG_TAG = "TwiccaEvernote";
    private static final int REQUEST_CODE = 210;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+", Pattern.CASE_INSENSITIVE);

    // Preferences keys
    public static final String PREF_EVERNOTE_USERNAME = "pref_evernote_username";
    public static final String PREF_EVERNOTE_PASSWORD = "pref_evernote_password";
    public static final String PREF_EVERNOTE_NOTEBOOK = "pref_evernote_notebook";
    public static final String PREF_EVERNOTE_TAGS     = "pref_evernote_tags";
    public static final String PREF_EVERNOTE_CRYPTED  = "pref_evernote_crypted";
    public static final String PREF_CONFIRM_DIALOG    = "pref_confirm_dialog";
    public static final String PREF_EVERNOTE_EXPIRE_AUTH = "pref_evernote_expire_auth";
    public static final String PREF_EVERNOTE_SHARD_ID = "pref_evernote_shard_id";
    public static final String PREF_EVERNOTE_AUTH_TOKEN = "pref_evernote_auth_token";

    private Context mContext;

    // data from Twicca
    private String mScreenName;
    private String mUsername;
    private String mBodyText;
    private String mProfileImageUrl;
    private String mTweetId;
    private String mCreatedAt;
    private String mSource;
    
    // Evernote settings
    private SharedPreferences mPrefs;
    private String mEvernoteUsername;
    private String mEvernotePassword;
    private String mEvernoteNotebook;
    private String mEvernoteTags;

    // UI
    private ProgressDialog mProgressDialog;
    private EditText mEditNotebook;
    private EditText mEditTags;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) run();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        mContext = getApplicationContext();
        Intent intent = getIntent();
        mBodyText = intent.getStringExtra(Intent.EXTRA_TEXT);
        mTweetId = intent.getStringExtra("id");
        mScreenName = intent.getStringExtra("user_screen_name");
        mUsername = intent.getStringExtra("user_name");
        mProfileImageUrl = intent.getStringExtra("user_profile_image_url_normal");
        mCreatedAt = intent.getStringExtra("created_at");
        mSource = intent.getStringExtra("source");
        run();
    }

    private void run () {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);
        mEvernoteUsername = mPrefs.getString(PREF_EVERNOTE_USERNAME, "");
        mEvernotePassword = mPrefs.getString(PREF_EVERNOTE_PASSWORD, "");
        mEvernoteNotebook = mPrefs.getString(PREF_EVERNOTE_NOTEBOOK, "");
        mEvernoteTags = mPrefs.getString(PREF_EVERNOTE_TAGS, "");

        String crypt = mPrefs.getString(PREF_EVERNOTE_CRYPTED, "");
        if (mEvernotePassword.length() > 0) {
            try {
            crypt = SimpleCrypt.encrypt(SEED, mEvernotePassword);
            mPrefs.edit().putString(PREF_EVERNOTE_CRYPTED, crypt).commit();
            mPrefs.edit().remove(PREF_EVERNOTE_PASSWORD).commit();
            Log.d(LOG_TAG, "plain text password has been migrate to crypted");
            } catch (Exception e) {
                Log.d(LOG_TAG, "Failed to encrypt plain password: " + mEvernotePassword);
            }
        }
        if (crypt.length() > 0) {
            try {
                mEvernotePassword = SimpleCrypt.decrypt(SEED, crypt);
            } catch (Exception e) {
                Log.d(LOG_TAG, "Failed to decrypt password: " + crypt);
            }
        }
        if (mEvernoteUsername.length() == 0 || mEvernotePassword.length() == 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(TwiccaEvernoteUploader.this);
            builder.setTitle(R.string.settings_name);
            builder.setMessage(getString(R.string.account_warning));
            builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent settings = new Intent(TwiccaEvernoteUploader.this, TwiccaPluginSettings.class);
                    TwiccaEvernoteUploader.this.startActivityForResult(settings, REQUEST_CODE);
                }
            }
           );

            builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    TwiccaEvernoteUploader.this.finish();
                }
            }
            );
            builder.create().show();
            return;
        }

        if (mPrefs.getBoolean(PREF_CONFIRM_DIALOG, true)) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(LAYOUT_INFLATER_SERVICE);
            View layout = inflater.inflate(R.layout.alert_dialog,
                                           (ViewGroup) findViewById(R.id.layout_root));
            AlertDialog.Builder builder = new AlertDialog.Builder(TwiccaEvernoteUploader.this);
            builder.setTitle(R.string.confirm_clip);
            mEditNotebook = (EditText)layout.findViewById(R.id.edit_notebook);
            mEditTags = (EditText)layout.
            findViewById(R.id.edit_tags);
            mEditNotebook.setText(mEvernoteNotebook);
            mEditTags.setText(mEvernoteTags);
            builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mEvernoteNotebook = mEditNotebook.getText().toString();
                    mEvernoteTags = mEditTags.getText().toString();
                    AsyncRequest req = new AsyncRequest();
                    req.execute();
                    dialog.dismiss();
                }
            }
            );
            builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    TwiccaEvernoteUploader.this.finish();
                }
            }
            );
            builder.setView(layout);
            builder.create().show();
        } else {
            AsyncRequest req = new AsyncRequest();
            req.execute();
        }
    }

    public class AsyncRequest extends AsyncTask<Void, String, Void> {
        private static final String CONSUMER_KEY = "sample";
        private static final String CONSUMER_SECRET = "abcdef0123456789";

        private static final String USER_AGENT = "twiccaEvernotePlugin (Android EDAM)/" +
        Constants.EDAM_VERSION_MAJOR + "." + 
        Constants.EDAM_VERSION_MINOR;

        private String mToastMessage = null;

        private static final String NOTE_PREFIX = 
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
          "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" +
          "<en-note>";
        private static final String NOTE_SUFFIX = "</en-note>";
        // Change this value to "www.evernote.com" to use the Evernote production
        // server instead of the sandbox server.
        private static final String EVERNOTE_HOST = "sandbox.evernote.com";
        private static final String USERSTORE_URL = "https://" + EVERNOTE_HOST + "/edam/user";
        private static final String NOTESTORE_URL_BASE = "https://" + EVERNOTE_HOST + "/edam/note/";

        private String mAuthToken;
        // Client classes used to interact with the Evernote web service
        private UserStore.Client mUserStore;
        private NoteStore.Client mNoteStore;

        public AsyncRequest() {
            mProgressDialog = new ProgressDialog(TwiccaEvernoteUploader.this);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }

        protected void onProgressUpdate(String... progress) {
            mProgressDialog.setMessage(progress[0]);
        }

        protected void onPreExecute() {
            super.onPreExecute();
            mToastMessage = null;
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setTitle(R.string.dialog_title);
            mProgressDialog.setMessage(getString(R.string.dialog_message));
            mProgressDialog.show();
        }

        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            if(mProgressDialog != null &&
                mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
            if (mToastMessage != null) {
                Toast.makeText(mContext, mToastMessage, Toast.LENGTH_LONG).show();
            }
            finish();
        }

        /**
         * Set up communications with the Evernote web service API, including
         * authenticating the user.
         */
        private void setupApi() {
            long nowTime = System.currentTimeMillis();
            long expire = mPrefs.getLong(PREF_EVERNOTE_EXPIRE_AUTH, 0);
            // reuse authentication token and shardId.
            String shardid = mPrefs.getString(PREF_EVERNOTE_SHARD_ID, "");
            setAuthToken(mPrefs.getString(PREF_EVERNOTE_AUTH_TOKEN, ""));

            try {
                AuthenticationResult authResult;
                TAndroidHttpClient userStoreTrans =
                    new TAndroidHttpClient(USERSTORE_URL, USER_AGENT, getFilesDir());
                TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
                setUserStore(new UserStore.Client(userStoreProt, userStoreProt));

                Log.d(LOG_TAG, "Check Authentication expire... (< 1800.000sec): " + (expire - nowTime) / 1000);
                if (expire - nowTime < 30 * 60 * 1000) { // 30mins before to expire, should re-auth, (msec)
                    Log.d(LOG_TAG, "ReAuthentication is required");
                    Log.d(LOG_TAG, "Check protocol version...");

                    boolean versionOk = mUserStore.checkVersion("twiccaEvernotePlugin (EDAM Android)",
                            com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                            com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);
                    if (!versionOk) {
                        mToastMessage = getString(R.string.message_error_version);
                        Log.e(LOG_TAG, mToastMessage);
                        return;
                    } // if versionOK

                    Log.d(LOG_TAG, "Authenticate user...");
                    try {
                        authResult = getUserStore().authenticate(mEvernoteUsername, mEvernotePassword, CONSUMER_KEY, CONSUMER_SECRET);
                    } catch (EDAMUserException ex) {
                        mToastMessage = getString(R.string.message_error_auth);
                        Log.e(LOG_TAG, mToastMessage, ex);
                        return;
                    } // try
                    User user = authResult.getUser();
                    setAuthToken(authResult.getAuthenticationToken());
                    shardid = user.getShardId();
                } else {
                    // less than 23 hours, refreshAuth
                    Log.d(LOG_TAG, "Refreshing authentication...");
                    authResult = getUserStore().refreshAuthentication(getAuthToken());
                    Log.d(LOG_TAG, "Auth expired at" + authResult.getExpiration());
                    setAuthToken(authResult.getAuthenticationToken());
                }
                SharedPreferences.Editor editor = mPrefs.edit();
                editor.putLong(PREF_EVERNOTE_EXPIRE_AUTH, authResult.getExpiration());
                editor.putString(PREF_EVERNOTE_AUTH_TOKEN, getAuthToken());
                editor.putString(PREF_EVERNOTE_SHARD_ID, shardid);
                editor.commit();
                // After successful authentication, configure a connection to the NoteStore
                Log.d(LOG_TAG, "Getting the NoteStore...");
                String noteStoreUrl = NOTESTORE_URL_BASE + shardid; // user.getShardId();
                TAndroidHttpClient noteStoreTrans =
                    new TAndroidHttpClient(noteStoreUrl, USER_AGENT, getFilesDir());
                TBinaryProtocol noteStoreProt = new TBinaryProtocol(noteStoreTrans);
                setNoteStore(new NoteStore.Client(noteStoreProt, noteStoreProt));
            } catch (TTransportException e) {
                mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                Log.e(LOG_TAG, mToastMessage, e);
            } catch (Throwable t) {
                mToastMessage = getString(R.string.message_error_api);
                Log.e(LOG_TAG, mToastMessage, t);
            } // try
        } // method

        private UserStore.Client getUserStore() {
            return this.mUserStore;
        }

        private void setUserStore(UserStore.Client userStore) {
            this.mUserStore = userStore;
        }

        private NoteStore.Client getNoteStore() {
            return this.mNoteStore;
        }

        private void setNoteStore(NoteStore.Client noteStore) {
            this.mNoteStore = noteStore;
        }

        private String getAuthToken() {
            return this.mAuthToken;
        }

        private void setAuthToken(String authToken) {
            this.mAuthToken = authToken;
        }

        @Override
        protected Void doInBackground(Void... params) {
            publishProgress(getString(R.string.dialog_message) + "\n" +
                    getString(R.string.message_setup_api));
            setupApi();
            try {
                if (getNoteStore() != null) {
                    Notebook notebook = null;
                    Note note = new Note();
                    Time time = new Time();
                    if (mEvernoteNotebook.length() > 0) {
                        Log.d(LOG_TAG, "Search notebook: '" + mEvernoteNotebook + "'");
                        List<Notebook> notebooks = getNoteStore().listNotebooks(getAuthToken());
                        for(Notebook n : notebooks) {
                              if (mEvernoteNotebook.equalsIgnoreCase(n.getName())) {
                                  notebook = n;
                                  break;
                              } // if
                        } // for
                        if (notebook == null) {
                            Log.d(LOG_TAG, "Create new notebook: '" + mEvernoteNotebook + "'");
                            notebook = new Notebook();
                            notebook.setName(mEvernoteNotebook);
                            getNoteStore().createNotebook(getAuthToken(), notebook);
                            notebook = getNoteStore().getNotebook(getAuthToken(), notebook.getName());
                        } // notebook == null
                    } // mEvernoteNotebook != ""

                    Log.d(LOG_TAG, "Clipping the note...");
                    publishProgress(getString(R.string.dialog_message)+ "\n" +
                            getString(R.string.notebook)+ " " + (notebook != null ? notebook.getName() : "(default)") + "\n" +
                            getString(R.string.tags) + " " + mEvernoteTags);

                    note.setTitle("Tweet by " + mUsername +" (@" + mScreenName + ")");
                    if (mEvernoteTags.length() > 0) {
                        note.setTagNames(java.util.Arrays.asList(mEvernoteTags.split(",")));
                    } // if
                    if (notebook != null) {
                        note.setNotebookGuid(notebook.getGuid());
                    } // if
                    time.set(Long.parseLong(mCreatedAt));
                    Matcher matcher = URL_PATTERN.matcher(mBodyText);
                    mBodyText =  matcher.replaceAll("<a target=\"_blank\" href=\"$0\">$0</a>").replace("\n", "<br />");
                    String content =
                        NOTE_PREFIX +
                        "<table style='border-radius: 10px; background-color: #eeeeee'>" +
                        "<tr><td valign='top' style='padding: 10px'>" +
                        "<img src=\"" + mProfileImageUrl + "\"/>" +
                        " </td>" +
                        "<td style='padding: 10px'>" +
                        "<b>" + mUsername + "(<a href=\"http://twitter.com/" + mScreenName + "\">" + "@" + mScreenName + "</a>)</b>" +
                        "<p>" + mBodyText + "</p>" +
                        "<a style='color: #888888' href=\"http://twitter.com/"+ mScreenName + "/statuses/"+ mTweetId + "\">" + time.format("%m/%d %H:%M:%S") + "</a>" +
                        " <span style='color: #888888'>from " + mSource + "</span>" +
                        "</td></tr>" +
                        "</table>" +
                        NOTE_SUFFIX;

                    note.setContent(content);
                    getNoteStore().createNote(getAuthToken(), note);

                    mToastMessage = getString(R.string.message_clipped);
                    Log.d(LOG_TAG, "All done.");
                  } // if (getNoteStore)
              } catch (TTransportException e) {
                  mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                  Log.e(LOG_TAG, mToastMessage, e);
              } catch (Exception e) {
                  mToastMessage = getString(R.string.message_error_unknown);
                  Log.e(LOG_TAG, mToastMessage, e);
              } // try
              return null;
          } // do
    } // class
}
