package com.aftac.lib.preferences;

/**
 * Created by Josh Van Horne on 3/14/2018.
 *
 * Attributes
 *  android:id
 *      id of view object
 *  android:title
 *      Title of setting shown to user
 *  android:key
 *      Key string for setting in settings file
 *  android:summary
 *      Summary of setting shown to user
 *  android:gravity
 *      Gravity of setting text in input field (right, left, center)
 *  android:inputType
 *      Input method type (reference: https://developer.android.com/training/keyboard-input/style)
 *  android:defaultValue
 *      The default value of the setting
 *  unit:
 *      String shown to user at the end of the input field.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.Preference;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.aftac.plugs.R;

public class EditTextPreference extends Preference implements View.OnFocusChangeListener {
    private final static int FORMAT_NULL    = 0;
    private final static int FORMAT_INTEGER = 1;
    private final static int FORMAT_FLOAT   = 2;
    private final static int FORMAT_STRING  = 3;
    private final static int FORMAT_DATE    = 4;

    private View myView;
    private TextWatcher textWatcher;
    private EditText textField = null;
    private String title = null;
    private String unit = "";
    private String summary = "";
    private String curValue;
    private String defValue;
    private int defaultColor;
    private int inputType = InputType.TYPE_CLASS_TEXT;
    private int outputFormat = FORMAT_STRING;
    private int gravity = Gravity.NO_GRAVITY;

    public EditTextPreference(Context context) {
        super(context);
        //properties = new DialogProperties();
        //setOnPreferenceClickListener(this);
        initTextWatcher();
    }

    public EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initProperties(attrs);
        initTextWatcher();
    }

    public EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initProperties(attrs);
        initTextWatcher();
    }

    @Override
    public View getView(View convertView, ViewGroup parent) {
        if (myView != null && myView == convertView)
            return convertView;

        View view = null;
        textField = null;

        if (convertView != null) {
            view = convertView;
            textField = convertView.findViewById(R.id.inputText);
        }

        if (textField == null) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            view = inflater.inflate(R.layout.preference_text_input, parent);
            textField = view.findViewById(R.id.inputText);
        }

        TextView titleField = view.findViewById(R.id.title);
        titleField.setText(title);

        TextView unitField = view.findViewById(R.id.unit);
        if (unit.equals("")) {
            unitField.setWidth(0);
            removeMargins(unitField);
        } else
            unitField.setText(unit);

        TextView summaryField = view.findViewById(R.id.summary);
        if (summary.equals("")) {
            summaryField.setHeight(0);
            removeMargins(summaryField);
        } else
            summaryField.setText(summary);

        textField.setText(String.valueOf(curValue), TextView.BufferType.EDITABLE);
        textField.setInputType(inputType);
        textField.setRawInputType(inputType);
        textField.addTextChangedListener(textWatcher);
        textField.setOnFocusChangeListener(this);
        textField.setGravity(gravity);
        defaultColor = textField.getCurrentTextColor();

        Log.v("INPUT_TYPE", String.valueOf(inputType));

        return myView = view;
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return super.onGetDefaultValue(a, index);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        super.onSetInitialValue(restorePersistedValue, defaultValue);
        if (defaultValue != null) {
            defValue = String.valueOf(defaultValue);
        }
        if (restorePersistedValue) {
            SharedPreferences prefs = getSharedPreferences();
            curValue = getValue(defValue);
        } else
            curValue = defValue;
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

    }

    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();

        return new SavedState(superState);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
    }

    @Override
    protected void onPrepareForRemoval() {
        super.onPrepareForRemoval();
        if (textField != null)
            textField.removeTextChangedListener(textWatcher);
    }

    @Override
    protected void onAttachedToActivity() {
        if (textField != null)
            textField.addTextChangedListener(textWatcher);
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            Boolean errorOccurred = false;
            String valueStr = textField.getText().toString();
            if (valueStr.equals(""))
                valueStr = "0";
            try {
                switch (outputFormat) {
                    case FORMAT_INTEGER:
                        valueStr = String.valueOf(Integer.parseInt(valueStr));
                        break;
                    case FORMAT_FLOAT:
                        valueStr = String.valueOf(Float.parseFloat(valueStr));
                        break;
                }
            } catch (Exception ex) {
                textField.setTextColor(Color.RED);
                errorOccurred = true;
            }
            if (!errorOccurred) {
                textField.setTextColor(defaultColor);
                storeValue(valueStr);
            }
        }
    }

    private static class SavedState extends BaseSavedState {
        Bundle dialogBundle;

        public SavedState(Parcel source) {
            super(source);
            dialogBundle = source.readBundle(getClass().getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeBundle(dialogBundle);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("unused")
        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    private void initTextWatcher() {
        textWatcher = new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                boolean errorOccurred = false;
                String valueStr = s.toString();
                if (valueStr.equals(""))
                    valueStr = "0";
                try {
                    switch (outputFormat) {
                        case FORMAT_INTEGER:
                            valueStr = String.valueOf(Integer.parseInt(valueStr));
                        break;
                        case FORMAT_FLOAT:
                            valueStr = String.valueOf(Float.parseFloat(valueStr));
                        break;
                    }
                } catch (Exception ex) {
                    errorOccurred = true;
                }

                textField.setTextColor((errorOccurred) ? Color.RED : defaultColor);
                //storeValue(valueStr);
            }
        };
    }

    private void initProperties(AttributeSet attrs) {
        defValue = "";
        for (int i=0;i<attrs.getAttributeCount();i++) {
            String name = attrs.getAttributeName(i);
            String value = attrs.getAttributeValue(i);
            Log.v("SETTING_ATTR: ", name + ": " + attrs.getAttributeValue(i));
            switch (name.toLowerCase()) {
                case "inputtype":
                    String inputTypeStr;
                    if (value.length() >= 1 && value.charAt(0) == '@')
                        inputTypeStr = getResourceValue(Integer.parseInt(value.substring(1)));
                    else
                        inputTypeStr = value;

                    if (value.length() >= 2 && inputTypeStr.startsWith("0x"))
                        setInputType(Integer.parseInt(inputTypeStr.substring(2), 16));
                    else
                        setInputType(Integer.parseInt(inputTypeStr));
                break;
                case "gravity":
                    String gravityStr;
                    if (value.length() >= 1 && value.charAt(0) == '@')
                        gravityStr = getResourceValue(Integer.parseInt(value.substring(1)));
                    else
                        gravityStr = value;

                    if (value.length() >= 2 && gravityStr.startsWith("0x"))
                        gravity = Integer.parseInt(gravityStr.substring(2), 16);
                    else
                        gravity = Integer.parseInt(gravityStr);
                break;
                case "title":
                    if (value.length() >= 1 && value.charAt(0) == '@') {
                        Resources res = getContext().getResources();
                        title = res.getString(Integer.parseInt(value.substring(1)));
                    } else
                        title = value;
                break;
                case "unit":
                    if (value.length() >= 1 && value.charAt(0) == '@') {
                        Resources res = getContext().getResources();
                        unit = res.getString(Integer.parseInt(value.substring(1)));
                    } else
                        unit = value;
                break;
                case "summary":
                    if (value.length() >= 1 && value.charAt(0) == '@') {
                        Resources res = getContext().getResources();
                        summary = res.getString(Integer.parseInt(value.substring(1)));
                    } else
                        summary = value;
                    break;
                case "defaultvalue":
                    defValue = value;
                break;
            }
        }

        if (defValue.length() > 0 && defValue.charAt(0) == '@')
            defValue = getResourceValue(Integer.parseInt(defValue.substring(1)));

        curValue = defValue;
    }

    private String getValue(String defaultValue) {
        switch (outputFormat) {
            case FORMAT_INTEGER:
                return String.valueOf(getPersistedInt(Integer.valueOf(defaultValue)));
            case FORMAT_FLOAT:
                return String.valueOf(getPersistedFloat(Float.valueOf(defaultValue)));
            default:
                return getPersistedString(defaultValue);
        }
    }

    private String getResourceValue(int id) {
        Resources res = getContext().getResources();
        String valueType = res.getResourceTypeName(id);
        switch (valueType.toLowerCase()) {
            case "bool":
                return String.valueOf(res.getBoolean(id));
            case "integer":
                return String.valueOf(res.getInteger(id));
            case "string":
            default:
                return res.getString(id);
        }
    }

    private void storeValue(String value) {
        curValue = value;
        switch (outputFormat) {
            case FORMAT_INTEGER:
                persistInt(Integer.parseInt(curValue));
            break;
            case FORMAT_FLOAT:
                persistFloat(Float.parseFloat(curValue));
            break;
            case FORMAT_STRING:
                persistString(curValue);
            break;
            case FORMAT_DATE:
                persistString(curValue);
            break;
        }
    }

    private void setInputType(int type) {
        int typeClass = type & InputType.TYPE_MASK_CLASS;
        int typeVar = type & InputType.TYPE_MASK_VARIATION;
        int typeFlag = type & InputType.TYPE_MASK_FLAGS;

        Log.v("SETTING_TYPE:", String.valueOf(type));

        switch (typeClass) {
            case InputType.TYPE_CLASS_DATETIME:
                outputFormat = FORMAT_DATE;
            break;

            case InputType.TYPE_CLASS_NUMBER:
                if ((typeFlag & InputType.TYPE_NUMBER_FLAG_DECIMAL) > 0)
                    outputFormat = FORMAT_FLOAT;
                else
                    outputFormat = FORMAT_INTEGER;
            break;

            case InputType.TYPE_CLASS_TEXT:
                outputFormat = FORMAT_STRING;
            break;
        }

        inputType = type;
        if (textField != null) {
            textField.setInputType(inputType);
            textField.setRawInputType(inputType);
        }
    }

    private void removeMargins(View view) {
        if (view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(0, 0, 0, 0);
            view.requestLayout();
        }
    }
}