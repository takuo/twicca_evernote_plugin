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

import java.util.Hashtable;
import java.util.List;

import org.apache.thrift.transport.TTransportException;

import com.evernote.client.conn.ApplicationInfo;
import com.evernote.client.oauth.android.EvernoteSession;
import com.evernote.edam.error.EDAMErrorCode;
import com.evernote.edam.error.EDAMNotFoundException;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteAttributes;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;

import android.app.AlertDialog;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class ClippingService extends IntentService {
    private Context mContext;

    private static final String LOG_TAG = "ClippingService";
    public static final String CONSUMER_KEY = "northeye-7638";
    public static final String CONSUMER_SECRET = "";
    private String mToastMessage = null;

    // Change this value to "www.evernote.com" to use the Evernote production
    // server instead of the sandbox server.
    public static final String APP_NAME = "Twicca Evernote plugin";
    public static final String APP_VERSION = "1.7.4";
    public static final String EVERNOTE_HOST = "sandbox.evernote.com";
    public static final String USERSTORE_URL = "https://" + EVERNOTE_HOST + "/edam/user";
    public static final String NOTESTORE_URL_BASE = "https://" + EVERNOTE_HOST + "/edam/note/";

    private Hashtable<String, String> mNoteTable;
    private EvernoteSession mSession;
    private String mAuthToken;
    // Client classes used to interact with the Evernote web service
    private NoteStore.Client mNoteStore;
    private List<Notebook> mNotebooks;

    /* from Intent */
    private String mNotebookName;
    private String mTags;
    private String mNoteTitle;
    private String mBodyText;
    private String mTweetURL;
    private Handler mHandler;
    private ECacheManager cacheManager;

    public ClippingService(String name) {
        super(name);
    }

    public ClippingService () {
        super("ClippingService");
        mHandler = new Handler();
    }

    private boolean refreshAuth() {
        try {
            List<Tag> tags = getNoteStore().listTags(getAuthToken());
            cacheManager.writeTagsCache(tags);
            if (mNotebooks == null) {
                mNotebooks = getNoteStore().listNotebooks(getAuthToken());
            }
            cacheManager.writeNoteCache(mNotebooks);
            return true;
        } catch (EDAMUserException ex) {
            Log.e(LOG_TAG, mToastMessage, ex);
            return false;
        } catch (Exception e)  {
            return false;
        } // try
    }

    private void setupSession() {
        ApplicationInfo info =
                new ApplicationInfo(ClippingService.CONSUMER_KEY,
                        ClippingService.CONSUMER_SECRET, ClippingService.EVERNOTE_HOST,
                        ClippingService.APP_NAME,ClippingService.APP_VERSION);
        mSession = new EvernoteSession(info, getSharedPreferences(TwiccaPluginSettings.SHARED_PREF, MODE_PRIVATE), getFilesDir());
    }
    
    @Override
    protected void onHandleIntent(Intent intent) {
        mContext = getApplicationContext();
        cacheManager = new ECacheManager(mContext);
        mNoteTable = cacheManager.getNoteTable();
        mNotebookName     = intent.getStringExtra("notebook");
        mTags             = intent.getStringExtra("tags");
        mNoteTitle        = intent.getStringExtra("title");
        mBodyText         = intent.getStringExtra(Intent.EXTRA_TEXT);
        mTweetURL         = intent.getStringExtra("url");

        setupSession();

        mAuthToken = mSession.getAuthToken();

        doEvernoteApi();

        mHandler.post(new Runnable() {
            @Override
            public void run () {
                if (mToastMessage != null) {
                    Toast.makeText(mContext, mToastMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
        if (getAuthToken() != null) {
            refreshAuth();
        }
    }

    private void doEvernoteApi() {
        for (int i=0; i<5;i++) {
            try {
                mNoteStore = mSession.createNoteStore();
                doUpload();
                return; // escape from loop()
            } catch (TTransportException e) {
                mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                Log.e(LOG_TAG, mToastMessage, e);
            } catch (Throwable t) {
                mToastMessage = getString(R.string.message_error_api);
                Log.e(LOG_TAG, mToastMessage, t);
            } // try
        } // for loop (retry)
    } // method

    private Notebook createNotebook(String name) {
        Log.d(LOG_TAG, "Create new notebook: '" + mNotebookName + "'");

        Notebook notebook = new Notebook();
        notebook.setName(mNotebookName);
        for (int i = 0; i < 5 ; i++) { // retry count 5
            try {
                try {
                    notebook = getNoteStore().createNotebook(getAuthToken(), notebook);
                    mNoteTable.put(mNotebookName.toLowerCase(), notebook.getGuid());
                } catch (EDAMUserException e) {
                    // maybe already exists.
                    notebook = null;
                }
                if (notebook == null) {
                    Log.d(LOG_TAG, "Retrieve notebook list...");
                    mNotebooks = getNoteStore().listNotebooks(getAuthToken());
                    for (Notebook n : mNotebooks) {
                        if (mNotebookName.equalsIgnoreCase(n.getName())) {
                            notebook = n;
                            break;
                        } // if
                    } // for
                } // notebook == null
                return notebook;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Error: " + e.getMessage());
            }
        }
        return null;
    }

    private void doUpload() {
        if (getNoteStore() == null) return;
        for (int i = 0; i < 5; i++) {
            try {
                String guid = null;
                Notebook notebook = null;
                Note note = new Note();
                if (mNotebookName.length() > 0) {
                    Log.d(LOG_TAG, "Search notebook from cache: '" + mNotebookName + "'");
                    if (mNoteTable.containsKey(mNotebookName.toLowerCase())) {
                        guid = mNoteTable.get(mNotebookName.toLowerCase());
                    } else {
                        notebook = createNotebook(mNotebookName);
                        if (notebook != null) guid = notebook.getGuid();
                    } // mNoteTable...
                } // mEvernoteNotebook != ""
                Log.d(LOG_TAG, "Clipping the note...");

                note.setTitle(mNoteTitle);
                if (mTags.length() > 0) {
                    note.setTagNames(java.util.Arrays.asList(mTags.split(", *")));
                } // if
                if (guid != null) {
                    note.setNotebookGuid(guid);
                } // if
                note.setContent(mBodyText);
                NoteAttributes attrs = new NoteAttributes();
                attrs.setSourceURL(mTweetURL);
                note.setAttributes(attrs);
                try {
                    getNoteStore().createNote(getAuthToken(), note);
                } catch (EDAMNotFoundException ee) {
                    notebook = createNotebook(mNotebookName);
                    if (notebook != null) {
                        note.setNotebookGuid(notebook.getGuid());
                    } else {
                        note.setNotebookGuid(null);
                    }
                    getNoteStore().createNote(getAuthToken(), note);
                }
                mToastMessage = getString(R.string.message_clipped) + ": " + mNoteTitle;
                Log.d(LOG_TAG, "done clipping");
                return;
            } catch (EDAMUserException eue) {
                mToastMessage = null;
                if (eue.isSetErrorCode() && eue.getErrorCode() == EDAMErrorCode.AUTH_EXPIRED) {
                    // Token EXPIRED
                    Intent intent = new Intent(this, TwiccaPluginSettings.class);
                    intent.putExtra("reset_auth", true);
                    intent.setAction(Intent.ACTION_MAIN);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } else {
                    // failed to auth,
                }
            } catch (TTransportException e) {
                mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                Log.e(LOG_TAG, mToastMessage, e);
            } catch (Exception e) {
                mToastMessage = getString(R.string.message_error_unknown);
                Log.e(LOG_TAG, mToastMessage, e);
            } // try
        } // while
    }

    private NoteStore.Client getNoteStore() {
        return this.mNoteStore;
    }

    private String getAuthToken() {
        return this.mAuthToken;
    }
}
