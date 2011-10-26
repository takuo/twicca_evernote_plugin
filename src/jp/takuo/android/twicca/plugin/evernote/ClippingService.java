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

import java.util.Hashtable;
import java.util.List;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TTransportException;

import com.evernote.android.edam.TAndroidHttpClient;
import com.evernote.edam.error.EDAMUserException;
import com.evernote.edam.notestore.NoteStore;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.Notebook;
import com.evernote.edam.type.Tag;
import com.evernote.edam.type.User;
import com.evernote.edam.userstore.AuthenticationResult;
import com.evernote.edam.userstore.Constants;
import com.evernote.edam.userstore.UserStore;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

public class ClippingService extends IntentService {
    private Context mContext;

    private static final String LOG_TAG = "ClippingService";
    private static final String CONSUMER_KEY = "sample";
    private static final String CONSUMER_SECRET = "abcdef0123456789";
    private static final String USER_AGENT = "TwiccaEvernotePlugin (Android EDAM)/" +
        Constants.EDAM_VERSION_MAJOR + "." + 
        Constants.EDAM_VERSION_MINOR;
    private String mToastMessage = null;

    // Change this value to "www.evernote.com" to use the Evernote production
    // server instead of the sandbox server.
    private static final String EVERNOTE_HOST = "sandbox.evernote.com";
    private static final String USERSTORE_URL = "https://" + EVERNOTE_HOST + "/edam/user";
    private static final String NOTESTORE_URL_BASE = "https://" + EVERNOTE_HOST + "/edam/note/";

    private Hashtable<String, String> mNoteTable;
    private String mAuthToken;
    // Client classes used to interact with the Evernote web service
    private User mUser;
    private UserStore.Client mUserStore;
    private NoteStore.Client mNoteStore;
    private List<Notebook> mNotebooks;

    // preference values
    private String mEvernoteUsername;
    private String mEvernotePassword;

    /* from Intent */
    private String mNotebookName;
    private String mTags;
    private String mNoteTitle;
    private String mBodyText;
    private Handler mHandler;
    private ECacheManager cacheManager;

    public ClippingService(String name) {
        super(name);
    }

    public ClippingService () {
        super("ClippingService");
        mHandler = new Handler();
    }

    private boolean doAuth() {
        for (int i = 0 ; i < 5 ; i++) {
            try {
                AuthenticationResult authResult;
                TAndroidHttpClient userStoreTrans =
                    new TAndroidHttpClient(USERSTORE_URL, USER_AGENT, getFilesDir());
                TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
                setUserStore(new UserStore.Client(userStoreProt, userStoreProt));

                Log.d(LOG_TAG, "Check protocol version...");

                boolean versionOk = mUserStore.checkVersion("twiccaEvernotePlugin (EDAM Android)",
                        com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                        com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);
                if (!versionOk) {
                    mToastMessage = getString(R.string.message_error_version);
                    Log.e(LOG_TAG, mToastMessage);
                    return false;
                } // if versionOK

                Log.d(LOG_TAG, "Authenticate user...");

                try {
                    authResult = getUserStore().authenticate(mEvernoteUsername, mEvernotePassword, CONSUMER_KEY, CONSUMER_SECRET);
                } catch (EDAMUserException ex) {
                    mToastMessage = getString(R.string.message_error_auth);
                    Log.e(LOG_TAG, mToastMessage, ex);
                    return false;
                } // try
                mUser = authResult.getUser();
                setAuthToken(authResult.getAuthenticationToken());
                cacheManager.writeAuthCache(authResult.getAuthenticationToken(), authResult.getExpiration());
                return true;
            } catch (Exception e) {
                mToastMessage = "Error: " + e.getMessage();
                Log.e(LOG_TAG, mToastMessage);
            } // try
        }
        return false;
    }

    private boolean refreshAuth() {
        AuthenticationResult authResult;
        if (getUserStore() == null) return false;
        Log.d(LOG_TAG, "Refresh authtoken...");
        try {
            authResult = getUserStore().refreshAuthentication(getAuthToken());
            setAuthToken(authResult.getAuthenticationToken());
            cacheManager.writeAuthCache(authResult.getAuthenticationToken(), authResult.getExpiration());
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

    @Override
    protected void onHandleIntent(Intent intent) {
        mContext = getApplicationContext();
        cacheManager = new ECacheManager(mContext);
        mNoteTable = cacheManager.getNoteTable();
        mNotebookName     = intent.getStringExtra("notebook");
        mTags             = intent.getStringExtra("tags");
        mNoteTitle        = intent.getStringExtra("title");
        mBodyText         = intent.getStringExtra(Intent.EXTRA_TEXT);
        mEvernoteUsername = intent.getStringExtra("username");
        mEvernotePassword = intent.getStringExtra("password");
        String token = null;

        token = cacheManager.getAuthToken();
        if (token != null) {
            setAuthToken(token);
        } else {
            if (doAuth() == false) {
                mToastMessage = getString(R.string.message_error_auth);
            }
        }

        if (getAuthToken() != null) {
            doEvernoteApi();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run () {
                Toast.makeText(mContext, mToastMessage, Toast.LENGTH_LONG).show();
            }
        });
        if (token != null) {
            refreshAuth();
        }
    }

    private void doEvernoteApi() {
        for (int i=0; i<5;i++) {
            try {
                TAndroidHttpClient userStoreTrans =
                    new TAndroidHttpClient(USERSTORE_URL, USER_AGENT, getFilesDir());
                TBinaryProtocol userStoreProt = new TBinaryProtocol(userStoreTrans);
                setUserStore(new UserStore.Client(userStoreProt, userStoreProt));
                if (mUser == null) {
                    mUser = getUserStore().getUser(getAuthToken());
                }
                Log.d(LOG_TAG, "Getting the NoteStore...");
                String noteStoreUrl = NOTESTORE_URL_BASE + mUser.getShardId();
                TAndroidHttpClient noteStoreTrans =
                    new TAndroidHttpClient(noteStoreUrl, USER_AGENT, getFilesDir());
                TBinaryProtocol noteStoreProt = new TBinaryProtocol(noteStoreTrans);
                setNoteStore(new NoteStore.Client(noteStoreProt, noteStoreProt));
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

    private void doUpload() {
        if (getNoteStore() == null) return;
        for (int i = 0; i < 5; i++) {
            try {
                String guid = null;
                Notebook notebook = null;
                Note note = new Note();
                if (mNotebookName.length() > 0) {
                    Log.d(LOG_TAG, "Search notebook: '" + mNotebookName + "'");
                    if (mNoteTable.containsKey(mNotebookName.toLowerCase())) {
                        guid = mNoteTable.get(mNotebookName.toLowerCase());
                    } else {
                        Log.d(LOG_TAG, "Create new notebook: '" + mNotebookName + "'");
                        notebook = new Notebook();
                        notebook.setName(mNotebookName);
                        try {
                            notebook = getNoteStore().createNotebook(getAuthToken(), notebook);
                        } catch (EDAMUserException e) {
                            // maybe already exists.
                            mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                            Log.e(LOG_TAG, mToastMessage, e);
                            notebook = null;
                        }
                        if (notebook == null) {
                            Log.d(LOG_TAG, "Retreive notebook list...");
                            mNotebooks = getNoteStore().listNotebooks(getAuthToken());
                            for (Notebook n : mNotebooks) {
                                if (mNotebookName.equalsIgnoreCase(n.getName())) {
                                    notebook = n;
                                    break;
                                } // if
                            } // for
                        } // notebook == null
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
                getNoteStore().createNote(getAuthToken(), note);

                mToastMessage = getString(R.string.message_clipped) + ": " + mNoteTitle;
                Log.d(LOG_TAG, "done clipping");
                return;
            } catch (TTransportException e) {
                mToastMessage = getString(R.string.message_error_server) + "\n" + e.getMessage();
                Log.e(LOG_TAG, mToastMessage, e);
            } catch (Exception e) {
                mToastMessage = getString(R.string.message_error_unknown);
                Log.e(LOG_TAG, mToastMessage, e);
            } // try
        } // while
    }

    private UserStore.Client getUserStore() {
        return this.mUserStore;
    }

    private NoteStore.Client getNoteStore() {
        return this.mNoteStore;
    }

    private void setNoteStore(NoteStore.Client noteStore) {
        this.mNoteStore = noteStore;
    }

    private void setUserStore(UserStore.Client userStore) {
        this.mUserStore = userStore;
    }

    private String getAuthToken() {
        return this.mAuthToken;
    }

    private void setAuthToken(String authToken) {
        this.mAuthToken = authToken;
    }
}
