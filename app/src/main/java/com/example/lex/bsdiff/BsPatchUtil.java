package com.example.lex.bsdiff;

/**
 * 工具类
 * Created by lex on 2017/12/17.
 */

public class BsPatchUtil {
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("bspatch");
    }
    public static native int bspatch(String oldApk, String newApk, String patch);
}
