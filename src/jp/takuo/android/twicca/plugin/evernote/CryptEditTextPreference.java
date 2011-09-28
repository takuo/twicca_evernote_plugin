package jp.takuo.android.twicca.plugin.evernote;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;

public class CryptEditTextPreference extends EditTextPreference {

    public CryptEditTextPreference(Context context) {
        super(context);
    }

    public CryptEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            String value = getEditText().getText().toString();
            try {
                if (value.length() > 0) {
                    value = SimpleCrypt.encrypt(TwiccaEvernoteUploader.SEED, value);
                }
            } catch (Exception e) {
                Log.d("CryptEditTextPreference", "could not encrypt value: " + value);
            }
            if (callChangeListener(value)) {
                setText(value);
            }
        }
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        String value = getText();
        EditText editText = getEditText();
        if (TwiccaEvernoteUploader.SEED != null) {
            try {
                value = SimpleCrypt.decrypt(TwiccaEvernoteUploader.SEED, value);
            } catch (Exception e) {
                Log.d("CryptEditTextPreference", "could not decrypt value: " + value);
            }
        }
        editText.setText(value);

        ViewParent oldParent = editText.getParent();
        if (oldParent != view) {
            if (oldParent != null) {
                ((ViewGroup) oldParent).removeView(editText);
            }
            onAddEditTextToDialogView(view, editText);
        }
    }

}
