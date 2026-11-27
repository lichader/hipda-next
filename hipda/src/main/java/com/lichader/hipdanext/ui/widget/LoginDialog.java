package com.lichader.hipdanext.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.lichader.hipdanext.R;
import com.lichader.hipdanext.async.LoginHelper;
import com.lichader.hipdanext.bean.HiSettingsHelper;
import com.lichader.hipdanext.ui.MainFrameActivity;
import com.lichader.hipdanext.ui.adapter.KeyValueArrayAdapter;
import com.lichader.hipdanext.utils.UIUtils;

/**
 * dialog for login
 * Created by GreenSkinMonster on 2015-04-18.
 */
public class LoginDialog extends Dialog {

    private MainFrameActivity mActivity;
    private final LoginHelper mLoginHelper;

    private String mUsername = "";
    private String mPassword = "";
    private String mSecQuestion = "";
    private String mSecAnswer = "";

    public LoginDialog(MainFrameActivity activity, LoginHelper loginHelper) {
        super(activity);
        mActivity = activity;
        mLoginHelper = loginHelper;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_login, null);

        final EditText etUsername = view.findViewById(R.id.login_username);
        final EditText etPassword = view.findViewById(R.id.login_password);
        final Spinner spSecQuestion = view.findViewById(R.id.login_question);
        final EditText etSecAnswer = view.findViewById(R.id.login_answer);

        final KeyValueArrayAdapter adapter = new MyKeyValueArrayAdapter(mActivity, R.layout.spinner_row);
        adapter.setEntryValues(mActivity.getResources().getStringArray(R.array.pref_login_question_list_values));
        adapter.setEntries(mActivity.getResources().getStringArray(R.array.pref_login_question_list_titles));
        spSecQuestion.setAdapter(adapter);

        etUsername.setText(mUsername);
        etPassword.setText(mPassword);
        if (!TextUtils.isEmpty(mSecQuestion)
                && TextUtils.isDigitsOnly(mSecQuestion)) {
            int idx = Integer.parseInt(mSecQuestion);
            if (idx > 0 && idx < adapter.getCount())
                spSecQuestion.setSelection(idx);
        }
        etSecAnswer.setText(mSecAnswer);

        Button btnLogin = view.findViewById(R.id.login_btn);
        btnLogin.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {

                if (TextUtils.isEmpty(etUsername.getText().toString())
                        || TextUtils.isEmpty(etPassword.getText().toString())) {
                    UIUtils.toast("请填写有效用户名和密码");
                    return;
                }

                UIUtils.hideSoftKeyboard(mActivity);

                mUsername = etUsername.getText().toString();
                mPassword = etPassword.getText().toString();
                mSecQuestion = adapter.getEntryValue(spSecQuestion.getSelectedItemPosition());
                mSecAnswer = etSecAnswer.getText().toString();

                if (mLoginHelper.isLoggedIn())
                    mLoginHelper.logout();

                HiSettingsHelper.getInstance().setUsername(mUsername);
                HiSettingsHelper.getInstance().setPassword(mPassword);
                HiSettingsHelper.getInstance().setSecQuestion(mSecQuestion);
                HiSettingsHelper.getInstance().setSecAnswer(mSecAnswer);
                HiSettingsHelper.getInstance().setUid("");

                mActivity.doLoginProgress();
                dismiss();
            }
        });

        setContentView(view);
    }

    private static class MyKeyValueArrayAdapter extends KeyValueArrayAdapter {
        public MyKeyValueArrayAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            view.setPadding(0, view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
            return view;
        }
    }

}
