![image](http://upload-images.jianshu.io/upload_images/724493-5277e505c945f125.jpg?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)
#### 1、概述
Android很多应用没有使用到NDK开发，但想要开发更高级的应用，NDK的学习是必然之路。NDK的好处不多说，这里也应该说是JNI的好处，其中之一就是可以方便使用到C/C++世界里面的优秀开源库，这里要实战的是增量更新，其中用到的是bsdiff开源代码，而bsdiff又依赖bzip2开源代码。


一开始自己做过一些硬件开发，也使用过一些so库，使用的话按照文档指示一般没什么问题，但实际上对NDK开发流程总有一些疑问。比如说

- 如何创建so库，so库是怎么生成的？
- 假如我生成了so库，如何给其他应用调用这个so库？
- jni目录、cpp目录又是什么？为什么有些项目使用的不一样
- Android.mk、Application.mk是什么？
- NDK开发一定要使用到javah、ndk-build命令吗？
- CMake、CMakeLists.txt又是什么？

对这些问题同样存在疑问的，可以参考网上的一些博客，写的非常详细
[Android NDK 开发从 0 到 1](https://juejin.im/entry/58f9b4145c497d0058ebcead)
[Android NDK开发扫盲及最新CMake的编译使用](http://www.jianshu.com/p/6332418b12b1)
[AndroidStudio中使用JNI/NDK示例](http://blog.leanote.com/post/jay_richard/AndroidStudio%E4%B8%AD%E4%BD%BF%E7%94%A8NDK%E7%A4%BA%E4%BE%8B)

产生这些问题的原因在于平时只看书、博客是会忽略掉很多细节，从而产生这样的疑问，所以NDK的学习之路，必然需要动手操作。


增量更新不同于热更新，增量更新可以应用于app市场，避免用户用过多的流量去升级app，只需要下载差分部分的patch补丁就可以，更快速、节省流量的实现了app的更新。


#### 2、在mac上实现增量更新
我用的是mac系统，win应该也差不多，先下载文件

[Binary diff/patch utility](http://www.daemonology.net/bsdiff/)
[bzip2](http://www.bzip.org/downloads.html)
###### 2.1、使用make命令生成bsdiff、bspatch可执行文件
```
lexdeMacBook-Pro:bsdiff-4.3 lex$ make
```
遇到问题
```
Mac下xcrun: error: invalid active developer path (/Library/Developer/CommandLineTools), missing xcrun at: /Library/Developer/CommandLineTools/usr/bin/xcrun
```
解决方法
```
xcode-select --install
```
重新安装xcode-select就可以
执行make命令后还是出现以下问题
```
Makefile:13: *** missing separator.  Stop.
```
参考网上说对Makefile文件中 if/endif 做了缩进，没效果。这里是一个坑，我用AndroidStudio去编辑无效，后来使用vim来编辑就可以了。

![image](http://upload-images.jianshu.io/upload_images/724493-3d1fe9cc3c90ac64.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

这时候生成了bsdiff可执行文件，接着出现以下错误，无法生成bspatch可执行文件，虽然说合并这部分在Android上做就可以了。
```
cc -O3 -lbz2    bsdiff.c   -o bsdiff
cc -O3 -lbz2    bspatch.c   -o bspatch
bspatch.c:39:21: error: unknown type name 'u_char'; did you mean 'char'?
static off_t offtin(u_char *buf)
                    ^~~~~~
                    char
bspatch.c:65:8: error: expected ';' after expression
        u_char header[32],buf[8];
              ^
              ;
bspatch.c:65:2: error: use of undeclared identifier 'u_char'; did you mean
      'putchar'?
        u_char header[32],buf[8];
        ^~~~~~
        putchar
/usr/include/stdio.h:261:6: note: 'putchar' declared here
int      putchar(int);
         ^
bspatch.c:65:9: error: use of undeclared identifier 'header'
        u_char header[32],buf[8];
               ^
bspatch.c:65:20: error: use of undeclared identifier 'buf'
        u_char header[32],buf[8];
        
...
   
```
缺少头文件，编辑bspatch.c加入头文件可以解决
```
#include <sys/types.h>
```
该文件是存在于
```
/usr/include/sys/types.h
```
再次执行make命令，成功的生成了两个可执行文件bsdiff、bspatch，前者用于差分生成补丁包，后者用于合并补丁生成新的apk包。

###### 2.2、增量文件的生成与合并
使用AndroidStudio简单生成两个apk，old.apk、new.apk
注意：每次生成apk最好clean一次工程

使用以下命令./bsdiff old.apk new.apk patch.patch
```
lexdeMacBook-Pro:bsdiff-4.3 lex$ ./bsdiff old.apk new.apk patch.patch
```
得到了patch.patch补丁
然后使用以下命令./bspatch old.apk new2.apk patch.patch
```
lexdeMacBook-Pro:bsdiff-4.3 lex$ ./bspatch old.apk new2.apk patch.patch
```
得到了新的apk，new2.apk

这个时候可以安装查看，也可以使用MD5来校验new.apk、new2.apk是否一致

```
lexdeMacBook-Pro:bsdiff-4.3 lex$ md5 new.apk
MD5 (new.apk) = 322b5a702bc1507e547c24f05109d812
lexdeMacBook-Pro:bsdiff-4.3 lex$ md5 new2.apk
MD5 (new2.apk) = 322b5a702bc1507e547c24f05109d812
```

事实上已经可以说明两个文件几乎一致了，说明这个操作是可行的了。

#### 3、Android上实现增量更新
但这个怎么在Android上使用呢？这时候就要使用到NDK的知识了。

流程应该是这样的：
① 服务器端生成patch补丁
② 客户端通过对应的版本号获取相应的patch补丁
③ 客户端将本地apk跟patch补丁合并，生成新的apk
④ 安装新的apk从而实现了增量更新

这里作为demo的话，就只要实现将本地apk跟patch补丁合并生成新apk，然后安装看看是否成功就可以了。

###### 3.1、新建一个C++ support工程
具体步骤不赘述，AndroidStudio默认使用CMake的形式
不要忘记添加SD卡读写权限
```
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```
###### 3.2、NDK相关代码的配置
copy相关文件到工程jni目录下
复制bspatch.c到jni目录下
复制bzip2文件夹到jni目录下，只留下头文件.h和源文件.c

![image](http://upload-images.jianshu.io/upload_images/724493-617cc23d3fb8dfca.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

修改CMakeLists.txt，主要是添加了需要编译的.c源码和.h头文件，这里要全部都编译，所以全部都添加到编译列表里面去
```
cmake_minimum_required(VERSION 3.4.1)

add_library( 
             SHARED
             
             src/main/jni/bspatch.c
             src/main/jni/bzip2/blocksort.c
             src/main/jni/bzip2/bzip2.c
             src/main/jni/bzip2/bzip2recover.c
             src/main/jni/bzip2/bzlib.c
             src/main/jni/bzip2/bzlib.h
             src/main/jni/bzip2/bzlib_private.h
             src/main/jni/bzip2/compress.c
             src/main/jni/bzip2/crctable.c
             src/main/jni/bzip2/decompress.c
             src/main/jni/bzip2/dlltest.c
             src/main/jni/bzip2/huffman.c
             src/main/jni/bzip2/mk251.c
             src/main/jni/bzip2/randtable.c
             src/main/jni/bzip2/spewG.c
             src/main/jni/bzip2/unzcrash.c )

find_library(
              log-lib

              log )

target_link_libraries(
                       bspatch

                       ${log-lib} )
```
修改bspatch.c里面的include，以及include jni
```
#include "bzip2/bzlib.h"
#include <jni.h>
```
编写增量更新工具类BsPatchUtil.java，写native方法
```java
/**
 * 增量更新工具类
 * Created by lex.
 */

public class BsPatchUtil {
    static {
        System.loadLibrary("bspatch");
    }
    public static native int bspatch(String oldApk, String newApk, String patch);
}
```
选中bspatch方法，按alt + enter，生成源代码方法，编写jni代码，传入参数。patchMethod()就是bspatch.c中的main()方法
```c
JNIEXPORT jint JNICALL
Java_com_example_lex_bsdiff_BsPatchUtil_bspatch(JNIEnv *env, jclass type, jstring oldApk_, jstring newApk_, jstring patch_) {
    const char *oldApk = (*env)->GetStringUTFChars(env, oldApk_, 0);
    const char *newApk = (*env)->GetStringUTFChars(env, newApk_, 0);
    const char *patch = (*env)->GetStringUTFChars(env, patch_, 0);

    // TODO
    int argc = 4;
    char *argv[argc];
    argv[0] = "bspatch";
    argv[1] = (char *) oldApk;
    argv[2] = (char *) newApk;
    argv[3] = (char *) patch;
    int ret = patchMethod(argc, argv);

    (*env)->ReleaseStringUTFChars(env, oldApk_, oldApk);
    (*env)->ReleaseStringUTFChars(env, newApk_, newApk);
    (*env)->ReleaseStringUTFChars(env, patch_, patch);

    return ret;
}
```
编译后会发现，报main方法重复的错误
```
Error:(70) multiple definition of `main'
```
找到各个对应文件，找到main方法，注释掉就可以编译成功了。

###### 3.3、编写Android代码实现增量更新
```java
/**
 * 点击按钮触发本地apk与补丁的合并
 */
public void patch(View btn) {
    String oldApk = getSourceDir();
    String newApk = getNewApkPath();
    String patch = getPatchPath();
    // 注意文件判空，否则崩溃
    BsPatchUtil.bspatch(oldApk, newApk, patch);
    // 如果成功了，调用intent去自动去安装apk
    // ...
}

/**
 * 获取本地apk的路径
 */
public String getSourceDir() {
    String sourceDir = getApplicationInfo().sourceDir;
    Log.i(TAG, "sourceDir = " + sourceDir);

    return sourceDir;
}

/**
 * 生成新apk的路径
 */
public String getNewApkPath() {
    String newApkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar + "newApk.apk";
    Log.i(TAG, "newApkPath = " + newApkPath);
    return newApkPath;
}

/**
 * 获取补丁patch的路径
 */
public String getPatchPath() {
    String patch = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separatorChar + "patch.patch";
    Log.i(TAG, "patch = " + patch);
    return patch;
}
```
安装新的apk，成功！

源码已上传到 [github](https://github.com/lextime2013/bsdiff/)
感谢鸿洋_大神的 [Android 增量更新完全解析 是增量不是热修复](http://blog.csdn.net/lmj623565791/article/details/52761658)
