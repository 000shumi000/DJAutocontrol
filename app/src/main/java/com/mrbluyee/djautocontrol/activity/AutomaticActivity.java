package com.mrbluyee.djautocontrol.activity;

import android.app.Activity;
import android.os.Bundle;

import com.mrbluyee.djautocontrol.R;

public class AutomaticActivity extends Activity {
    private static final String TAG = AutomaticActivity.class.getName();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_automatic_camera);
    }
}
