package com.jzh.www.demo.bean;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Process;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import com.jzh.www.demo.R;
import com.jzh.www.demo.utils.LogUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static android.os.Environment.MEDIA_MOUNTED;

/**
 * apk更新管理类
 * */
public class AppDownloadManager {
    public static final String TAG = "AppDownloadManager";
    private WeakReference<Activity> weakReference;
    private DownloadManager mDownloadManager;
    private OnUpdateListener mUpdateListener;
    private DownloadReceiver mDownloadReceiver;
    private DownloadChangeObserver mDownLoadChangeObserver;
    private long mReqId;

    public AppDownloadManager(WeakReference<Activity> weakReference){
        this.weakReference = weakReference;
        mDownloadManager = (DownloadManager) weakReference.get().getSystemService(Context.DOWNLOAD_SERVICE);
        mDownLoadChangeObserver = new DownloadChangeObserver(new Handler());
        mDownloadReceiver = new DownloadReceiver();
    }

    public void setUpdateListener(OnUpdateListener mUpdateListener) {
        this.mUpdateListener = mUpdateListener;
    }



    public interface OnUpdateListener {
        void update(int currentByte, int totalByte);
    }

    public interface AndroidOInstallPermissionListener {
        void permissionSuccess();
        void permissionFail();
    }


    /**
     * @param updateDir
     * 就是可以执行的文件 修改文件的权限，可读、可写、可执行*/

    private void setUpdateDir(File updateDir) {
        try {
            java.lang.Process p = Runtime.getRuntime().exec("chmod 777" +updateDir);
            p.waitFor();
        }catch (IOException e) {
            e.printStackTrace();
        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


        /**
         * 获取apk文件
         * */
        private File getApkFile(){
            File apkFile;
            File updateDir;
            //判断SDK卡是否可用
            if (MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                updateDir = new File(Environment.getExternalStorageDirectory(),weakReference.get().getResources().getString(R.string.apk_downLoad_dir));
            }else {
                //没内存卡就存机身内存
                updateDir=new File(Environment.getDataDirectory().toString());
            }

            //判断文件夹是否存在
            if(!updateDir.exists()){
                updateDir.mkdirs();
            }
            apkFile = new File(updateDir.getPath(),weakReference.get().getResources().getString(R.string.apk_name));
            return apkFile;
        }



            public void downloadApk(String apkUrl,String title,String desc){
                //下载之前删除已有文件
                File apkFile = getApkFile();
                if (apkFile != null && apkFile.exists()) {
                    apkFile.delete();
                }
                setUpdateDir(apkFile);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
                //设置title
                request.setTitle(title);
                // 设置描述
                request.setDescription(desc);
                // 完成后显示通知栏
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                //设置下载目录
                Uri uri;
                //判断SDK卡是否可用
                if (MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                    if (Build.VERSION.SDK_INT < 23) { // 6.0以下
                        uri = Uri.parse("file://"+apkFile.getPath());
                    } else if (Build.VERSION.SDK_INT < 24) { // 6.0 - 7.0
                        uri = Uri.parse("content://"+apkFile.getPath());
                    } else { // Android 7.0 以上
                        uri = Uri.parse("content://"+apkFile.getPath());
                    }
                }else {
                    //没内存卡就存机身内存
                    if (Build.VERSION.SDK_INT < 23) { // 6.0以下
                        uri = Uri.parse("file://"+apkFile.getPath());
                    } else if (Build.VERSION.SDK_INT < 24) { // 6.0 - 7.0
                        uri = Uri.parse("content://"+apkFile.getPath());
                    } else { // Android 7.0 以上
                        uri = Uri.parse("content://"+apkFile.getPath());
                    }
                }
                request.setDestinationUri(uri);
                request.setMimeType("application/vnd.android.package-archive");
                mReqId = mDownloadManager.enqueue(request);
            }

            /**
             * 取消下载
             */
            public void cancel() {
                mDownloadManager.remove(mReqId);
            }

            public void resume() {
                Uri uri;
                File apkFile = getApkFile();
                if (Build.VERSION.SDK_INT < 23) { // 6.0以下
                    uri = Uri.parse("file://"+apkFile.getPath());
                } else if (Build.VERSION.SDK_INT < 24) { // 6.0 - 7.0
                    uri = Uri.parse("content://"+apkFile.getPath());
                } else { // Android 7.0 以上
                    uri = Uri.parse("content://"+apkFile.getPath());
                }

                //设置监听Uri.parse("content://downloads/my_downloads")
                weakReference.get().getContentResolver().registerContentObserver(uri,true,
                        mDownLoadChangeObserver);
                // 注册广播，监听APK是否下载完成
                weakReference.get().registerReceiver(mDownloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }

            public void onPause() {
                weakReference.get().getContentResolver().unregisterContentObserver(mDownLoadChangeObserver);
                weakReference.get().unregisterReceiver(mDownloadReceiver);
            }

            private void updateView() {
                int[] bytesAndStatus = new int[]{0, 0, 0};
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(mReqId);
                Cursor c = null;
                try {
                    c = mDownloadManager.query(query);
                    if (c != null && c.moveToFirst()) {
                        //已经下载的字节数
                        bytesAndStatus[0] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        //总需下载的字节数
                        bytesAndStatus[1] = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        //状态所在的列索引
                        bytesAndStatus[2] = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                if (mUpdateListener != null) {
                    mUpdateListener.update(bytesAndStatus[0], bytesAndStatus[1]);
                }
                //Log.i(TAG, "下载进度：" + bytesAndStatus[0] + "/" + bytesAndStatus[1] + "");
                LogUtils.logOut("i",TAG,"下载进度：" + bytesAndStatus[0] + "/" + bytesAndStatus[1] + "");
            }


            class DownloadChangeObserver extends ContentObserver {

                /**
                 * Creates a content observer.
                 *
                 * @param handler The handler to run {@link #onChange} on, or null if none.
                 */
                public DownloadChangeObserver(Handler handler) {
                    super(handler);
                }

                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    updateView();
                }
            }


            class DownloadReceiver extends BroadcastReceiver {
                @Override
                public void onReceive(final Context context, final Intent intent) {
                    //boolean haveInstallPermission;
                    // 兼容Android 8.0
                    if (Build.VERSION.SDK_INT >= 26) {
                        //先获取是否有安装未知来源应用的权限
                        //haveInstallPermission = context.getPackageManager().canRequestPackageInstalls();
//                        if (!haveInstallPermission) {//没有权限
//                            // 弹窗，并去设置页面授权
//                            final AndroidOInstallPermissionListener listener = new AndroidOInstallPermissionListener() {
//                                @Override
//                                public void permissionSuccess() {
//                                    installApk(context, intent);
//                                }
//
//                                @Override
//                                public void permissionFail() {
//                                    //ToastUtils.shortToast(context, "授权失败，无法安装应用");
//                                }
//                            };
//
//                            //AndroidOPermissionActivity.sListener = listener;
//                            //Intent intent1 = new Intent(context, AndroidOPermissionActivity.class);
//                            //context.startActivity(intent1);
//                        } else {
//                            installApk(context, intent);
//                        }
                    } else {
                        installApk(context, intent);
                    }
                }
            }



            private void installApk(Context context, Intent intent) {
                long completeDownLoadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                //Log.e(TAG, "收到广播");
                LogUtils.logOut("e",TAG,"收到广播");
                Uri uri;
                Intent intentInstall = new Intent();
                intentInstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intentInstall.setAction(Intent.ACTION_VIEW);

                if (completeDownLoadId == mReqId) {
                    if (Build.VERSION.SDK_INT < 23) { // 6.0以下
                        uri = mDownloadManager.getUriForDownloadedFile(completeDownLoadId);
                    } else if (Build.VERSION.SDK_INT < 24) { // 6.0 - 7.0
                        File apkFile = queryDownloadedApk(context, completeDownLoadId);
                        uri = Uri.fromFile(apkFile);
                    } else { // Android 7.0 以上
                        uri = FileProvider.getUriForFile(context,
                                "packgeName.fileProvider",
                                new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),context.getResources().getString(R.string.apk_name)));
                        intentInstall.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                    if(uri!=null){
                        // 安装应用
                        LogUtils.logOut("e","DownLoadFinished","下载完成了");
                        intentInstall.setDataAndType(uri, "application/vnd.android.package-archive");
                        context.startActivity(intentInstall);
                        android.os.Process.killProcess(Process.myPid());
                    }else{
                        //Log.e("DownloadManager", "download error");
                        LogUtils.logOut("e","DownloadManager","download error");
                    }
                }
            }

            //通过downLoadId查询下载的apk，解决6.0以后安装的问题
            public static File queryDownloadedApk(Context context, long downloadId) {
                File targetApkFile = null;
                DownloadManager downloader = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

                if (downloadId != -1) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL);
                    Cursor cur = downloader.query(query);
                    if (cur != null) {
                        if (cur.moveToFirst()) {
                            String uriString = cur.getString(cur.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI));
                            if (!TextUtils.isEmpty(uriString)) {
                                targetApkFile = new File(Uri.parse(uriString).getPath());
                            }
                        }
                        cur.close();
                    }
                }
                return targetApkFile;
            }
        }


