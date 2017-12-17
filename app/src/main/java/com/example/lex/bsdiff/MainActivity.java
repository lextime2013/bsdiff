package com.example.lex.bsdiff;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void patch(View btn) {
        String oldApk = getSourceDir();
        String newApk = getNewApkPath();
        String patch = getPatchPath();
        BsPatchUtil.bspatch(oldApk, newApk, patch);
    }

    public String getSourceDir() {
        String sourceDir = getApplicationInfo().sourceDir;
        Log.i(TAG, "sourceDir = " + sourceDir);

        return sourceDir;
    }

    public String getNewApkPath() {
        String newApkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar + "newApk.apk";
        Log.i(TAG, "newApkPath = " + newApkPath);
        return newApkPath;
    }

    public String getPatchPath() {
        String patch = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar + "patch.patch";
        Log.i(TAG, "patch = " + patch);
        return patch;
    }
}