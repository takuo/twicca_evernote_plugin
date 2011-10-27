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

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.MultiAutoCompleteTextView;

public class MultiAutoCompleteEditTextPreference extends DialogPreference {

    private MultiAutoCompleteTextView mEditText;
    private String mText;

    public MultiAutoCompleteEditTextPreference(Context context,
            AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mEditText = new MultiAutoCompleteTextView(context, attrs);
        mEditText.setId(android.R.id.edit);
        mEditText.setEnabled(true);
        mEditText.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());
    }

    public MultiAutoCompleteEditTextPreference(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.editTextPreferenceStyle);
    }

    public MultiAutoCompleteEditTextPreference(Context context) {
        this(context, null);
    }

    public void setText(String text) {
        final boolean wasBlocking = shouldDisableDependents();
        mText = text;
        persistString(text);
        final boolean isBlocking = shouldDisableDependents();
        if (isBlocking == wasBlocking) {
            notifyDependencyChange(isBlocking);
        }
    }

    public String getText() {
        return mText;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        MultiAutoCompleteTextView textView = mEditText;
        textView.setText(getText());

        ViewParent oldParent = textView.getParent();
        if (oldParent != view) {
            if (oldParent != null) {
                ((ViewGroup) oldParent).removeView(textView);
            }
            onAddEditTextToDialogView(view, textView);
        }
    }

    protected void onAddEditTextToDialogView(View dialogView,
            MultiAutoCompleteTextView textView) {
        ViewGroup container = (ViewGroup) dialogView
            .findViewById(R.id.edittext_container);
        if (container != null) {
            container.addView(textView, ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            Log.e("E", "Container is NULL!!");
        }
    }

    @Override
    protected View onCreateDialogView() {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.edittext_preference, null);
        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = mEditText.getText().toString().trim();
            while (value.endsWith(",")) {
                value = value.substring(0, value.length() - 1);
            }
            if (callChangeListener(value)) {
                setText(value);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setText(restoreValue ? getPersistedString(mText) : (String) defaultValue);
    }

    @Override
    public boolean shouldDisableDependents() {
        return TextUtils.isEmpty(mText) || super.shouldDisableDependents();
    }

    public MultiAutoCompleteTextView getEdit() {
        return mEditText;
    }

    public void setStringArray(String [] array) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(),
                R.layout.list_item, array);
        getEdit().setAdapter(adapter);
    }
    
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            return superState;
        }
        final SavedState myState = new SavedState(superState);
        myState.text = getText();
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setText(myState.text);
    }

    private static class SavedState extends BaseSavedState {
        String text;

        public SavedState(Parcel source) {
            super(source);
            text = source.readString();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(text);
        }

        public SavedState (Parcelable superState) {
            super(superState);
        }

        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
                
            }
        };
    }
}
