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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Android */
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
    private static final String NOTE_PREFIX =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<!DOCTYPE en-note SYSTEM \"http://xml.evernote.com/pub/enml2.dtd\">" +
        "<en-note>";
    private static final String NOTE_SUFFIX = "</en-note>";

    // Preferences keys
    public static final String PREF_EVERNOTE_USERNAME = "pref_evernote_username";
    public static final String PREF_EVERNOTE_PASSWORD = "pref_evernote_password";
    public static final String PREF_EVERNOTE_NOTEBOOK = "pref_evernote_notebook";
    public static final String PREF_EVERNOTE_TAGS     = "pref_evernote_tags";
    public static final String PREF_EVERNOTE_CRYPTED  = "pref_evernote_crypted";
    public static final String PREF_CONFIRM_DIALOG    = "pref_confirm_dialog";

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

    private void requestUpload ()
    {
        Time time = new Time();
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
        Intent intent = new Intent(this, ClippingService.class);
        intent.putExtra("notebook", mEvernoteNotebook);
        intent.putExtra("tags", mEvernoteTags);
        intent.putExtra("title", "Tweet by " + mUsername +" (@" + mScreenName + ")");
        intent.putExtra("username", mEvernoteUsername);
        intent.putExtra("password", mEvernotePassword);
        intent.putExtra(Intent.EXTRA_TEXT, content);
        Toast.makeText(mContext, getString(R.string.message_do_background), Toast.LENGTH_SHORT).show();
        startService(intent);
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
                    dialog.dismiss();
                    requestUpload();
                    finish();
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
            requestUpload();
            finish();
        }
    }
}
