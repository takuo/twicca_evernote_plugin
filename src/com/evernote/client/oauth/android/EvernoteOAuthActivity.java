/*
 * Copyright 2012 Evernote Corporation
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *  
 * 1. Redistributions of source code must retain the above copyright notice, this 
 *    list of conditions and the following disclaimer.
 *     
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *  
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.client.oauth.android;

import jp.takuo.android.twicca.plugin.evernote.R;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.EvernoteApi;
import org.scribe.exceptions.OAuthException;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import com.evernote.client.oauth.EvernoteAuthToken;
import com.evernote.client.oauth.YinxiangApi;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * An Android Activity for authenticating to Evernote using OAuth.
 * Third parties should not need to use this class directly.
 */
public class EvernoteOAuthActivity extends Activity {

  private static final String TAG = "EvernoteOAuthActivity";

  static final String EXTRA_EVERNOTE_HOST = "EVERNOTE_HOST";
  static final String EXTRA_CONSUMER_KEY = "CONSUMER_KEY";
  static final String EXTRA_CONSUMER_SECRET = "CONSUMER_SECRET";

  static final String EXTRA_AUTH_TOKEN = "AUTH_TOKEN";
  static final String EXTRA_NOTESTORE_URL = "NOTESTORE_URL";
  static final String EXTRA_WEBAPI_URL_PREFIX = "WEBAPI_URL_PREFIX";
  static final String EXTRA_USERID = "USER_ID";
  
  private String evernoteHost = null;
  private String consumerKey = null;
  private String consumerSecret = null;
  private String requestToken = null;
  private String requestTokenSecret = null;
  private WebView mWebView;
  static EvernoteAuthToken authToken = null;
  
  private ProgressDialog mProgressDialog;
  private Context mContext;
  private String mAuthorizationUrl = null;
  
  // Activity state variables
  static boolean startedAuthentication;
  private boolean receivedCallback = false;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      evernoteHost = savedInstanceState.getString("evernoteHost");
      consumerKey = savedInstanceState.getString("consumerKey");
      consumerSecret = savedInstanceState.getString("consumerSecret");
      requestToken = savedInstanceState.getString("requestToken");
      requestTokenSecret = savedInstanceState.getString("requestTokenSecret");
      startedAuthentication = savedInstanceState.getBoolean("startedAuthentication");
    }

    if (consumerKey == null) {
      Intent intent = getIntent();
      evernoteHost = intent.getStringExtra(EXTRA_EVERNOTE_HOST);
      consumerKey = intent.getStringExtra(EXTRA_CONSUMER_KEY);
      consumerSecret = intent.getStringExtra(EXTRA_CONSUMER_SECRET);
    }

    // setTheme(android.R.style.Theme_NoDisplay);
    mContext = getApplication();
    getWindow().requestFeature(Window.FEATURE_PROGRESS);
    getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setContentView(R.layout.oauth);
    mWebView = (WebView)findViewById(R.id.webview);
    mWebView.getSettings().setAppCacheEnabled(false);
    mWebView.getSettings().setJavaScriptEnabled(true);
    mWebView.clearCache(true);
    mWebView.clearFormData();
    getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_VISIBILITY_ON);
    final Activity activity = this;
    mWebView.setWebViewClient(new WebViewClient(){
        @Override
        public boolean shouldOverrideUrlLoading (WebView view, String url) {
            if (url.startsWith(getCallbackScheme())) {
                Uri uri = Uri.parse(url);
                authToken = completeAuth(uri);
                receivedCallback = true;
                finish();
                return true;
            }
            return false;
        }
    });
    mWebView.setWebChromeClient(new WebChromeClient() {
        public void onProgressChanged(WebView w, int p) {
            activity.setProgress(p * 100);
        }
    });
    startedAuthentication = false;
    super.onCreate(savedInstanceState);
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putString("evernoteHost", evernoteHost);
    outState.putString("consumerKey", consumerKey);
    outState.putString("consumerSecret", consumerSecret);
    outState.putString("requestToken", requestToken);
    outState.putString("requestTokenSecret", requestTokenSecret);
    outState.putBoolean("startedAuthentication", startedAuthentication);
  }

  @Override
  protected void onResume() {
    super.onResume();

    if ((evernoteHost == null) || (consumerKey == null) || (consumerSecret == null)) {
      finish();
      return;
    }

    if (!startedAuthentication) {
      receivedCallback = false;
      beginAuthentication();
      startedAuthentication = true;
    } else if (!receivedCallback) {
      authToken = null;
      finish();
      return;
    }
  }
  
  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent); 
    Log.d(TAG, "onNewIntent");
    Uri uri = intent.getData();
    if ((uri != null) && uri.getScheme().equals(getCallbackScheme())) {
      authToken = completeAuth(uri);
      receivedCallback = true;
      finish();
    }
  }

  public static void reset() {
    authToken = null;
    startedAuthentication = false;
  }
  
  private String getCallbackScheme() {
    return "en-" + consumerKey;
  }

  @SuppressWarnings("unchecked")
  private OAuthService createService() {
    Class apiClass = EvernoteApi.class;
    if (evernoteHost.equals("sandbox.evernote.com")) {
      apiClass = EvernoteApi.Sandbox.class;
    } else if (evernoteHost.equals("app.yinxiang.com")) {
      apiClass = YinxiangApi.class;
    }
    return new ServiceBuilder()
      .provider(apiClass)
      .apiKey(consumerKey)
      .apiSecret(consumerSecret)
      .callback(getCallbackScheme() + "://callback")
      .build();    
  }
  
  /**
   * Get a request token from the Evernote web service and send the user
   * to a browser to authorize access. 
   */
  private void beginAuthentication() {
    try {
      OAuthService service = createService();
      Log.i(TAG, "Retrieving OAuth request token...");
      Token reqToken = service.getRequestToken();
      this.requestToken = reqToken.getToken();
      this.requestTokenSecret = reqToken.getSecret();
      
      // Open a browser to allow the user to authorize access to their account
      Log.i(TAG, "Redirecting user for authorization...");
      mAuthorizationUrl = service.getAuthorizationUrl(reqToken);
      //Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
      //intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      //intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      //startActivity(intent);
      mWebView.loadUrl(mAuthorizationUrl);
      mWebView.requestFocus();
    } catch (OAuthException oax) {
      Log.e(TAG, "Failed to obtain OAuth request token", oax);
      finish();
    } catch (Exception ex) {
      Log.e(TAG, "Failed to obtain OAuth request token", ex);
      finish();
    }
  }

  /**
   * Called when the user authorizes or denies access and is redirected
   * back to our app. If the user authorized access, this method
   * exchanges our temporary request token for an access token that
   * can be used to make authenticated API calls.
   * 
   * @param uri The callback URL, which contains the mandatory oauth_verifier.
   */
  private EvernoteAuthToken completeAuth(Uri uri) {
    EvernoteAuthToken accessToken = null;

    if (requestToken != null) {
      OAuthService service = createService();
      String verifierString = uri.getQueryParameter("oauth_verifier");
      if (verifierString == null || verifierString.length() == 0) {
        Log.i(TAG, "User did not authorize access");
      } else {
        Verifier verifier = new Verifier(verifierString);
        Log.i(TAG, "Retrieving OAuth access token...");  
        try {
          Token reqToken = new Token(requestToken, requestTokenSecret);
          accessToken = 
            new EvernoteAuthToken(service.getAccessToken(reqToken, verifier));
        } catch (OAuthException oax) {
          Log.e(TAG, "Failed to obtain OAuth access token", oax);
        } catch (Exception ex) {
          Log.e(TAG, "Failed to obtain OAuth access token", ex);
        }
      }
    } else {
      Log.d(TAG, "Unable to retrieve OAuth access token, no request token");
    }
    
    return accessToken;
  }  
}
