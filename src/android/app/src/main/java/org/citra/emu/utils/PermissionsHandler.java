package org.citra.emu.utils;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.citra.emu.R;

public final class PermissionsHandler {
    public static final int REQUEST_CODE_WRITE_PERMISSION = 500;
    public static final int REQUEST_CODE_CAMERA_PERMISSION = 501;
    public static final int REQUEST_CODE_RECORD_PERMISSION = 502;
    public static final int REQUEST_CODE_MANAGE_STORAGE = 503;

    /**
     * 检查存储访问权限。
     * - Android 11+（API R+）：需要 MANAGE_EXTERNAL_STORAGE，通过跳转系统设置页授权
     * - Android 10 及以下：用运行时权限 WRITE_EXTERNAL_STORAGE
     * 参考 afeimod/NesStation 的权限引导流程。
     */
    public static boolean checkWritePermission(final Activity activity) {
        // Android 11+ 走 MANAGE_EXTERNAL_STORAGE（所有文件访问权限）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return checkManageStoragePermission(activity);
        }
        // Android 10 及以下走 WRITE_EXTERNAL_STORAGE 运行时权限
        int hasWritePermission =
            ContextCompat.checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE);

        if (hasWritePermission != PackageManager.PERMISSION_GRANTED) {
            if (activity.shouldShowRequestPermissionRationale(WRITE_EXTERNAL_STORAGE)) {
                showMessageOKCancel(
                    activity, activity.getString(R.string.write_permission_needed),
                    (dialog, which)
                        -> activity.requestPermissions(new String[] {WRITE_EXTERNAL_STORAGE},
                                                       REQUEST_CODE_WRITE_PERMISSION));
                return false;
            }

            activity.requestPermissions(new String[] {WRITE_EXTERNAL_STORAGE},
                                        REQUEST_CODE_WRITE_PERMISSION);
            return false;
        }

        return true;
    }

    /**
     * 检查 MANAGE_EXTERNAL_STORAGE 权限（Android 11+）。
     * 如果没授权，弹对话框说明，点 OK 后跳转到系统的"所有文件访问权限"设置页。
     */
    public static boolean checkManageStoragePermission(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return true;
        }
        if (Environment.isExternalStorageManager()) {
            return true;
        }
        // 没授权 —— 弹框引导用户去设置页授权
        new AlertDialog.Builder(activity)
            .setTitle(R.string.write_permission_needed)
            .setMessage(R.string.manage_storage_permission_needed)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                try {
                    // 优先跳转到本 app 的专属授权页
                    Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivityForResult(intent, REQUEST_CODE_MANAGE_STORAGE);
                } catch (Exception e) {
                    // 部分 ROM（TV 盒子等）不响应上面那个 action，fallback 到全局设置页
                    try {
                        Intent fallback = new Intent(
                            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        activity.startActivityForResult(fallback, REQUEST_CODE_MANAGE_STORAGE);
                    } catch (Exception e2) {
                        Toast.makeText(activity,
                            R.string.write_permission_needed, Toast.LENGTH_LONG).show();
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                Toast.makeText(activity, R.string.write_permission_needed,
                    Toast.LENGTH_SHORT).show();
            })
            .setCancelable(false)
            .show();
        return false;
    }

    public static boolean checkCameraPermission(final Activity activity) {
        int permission = ContextCompat.checkSelfPermission(activity, CAMERA);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            if (activity.shouldShowRequestPermissionRationale(CAMERA)) {
                showMessageOKCancel(activity, activity.getString(R.string.camera_permission_needed),
                                    (dialog, which)
                                        -> activity.requestPermissions(
                                            new String[] {CAMERA}, REQUEST_CODE_CAMERA_PERMISSION));
                return false;
            }

            activity.requestPermissions(new String[] {CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
            return false;
        }

        return true;
    }

    public static boolean checkRecordPermission(final Activity activity) {
        int permission = ContextCompat.checkSelfPermission(activity, RECORD_AUDIO);
        if (permission != PackageManager.PERMISSION_GRANTED) {
            if (activity.shouldShowRequestPermissionRationale(RECORD_AUDIO)) {
                showMessageOKCancel(
                    activity, activity.getString(R.string.record_permission_needed),
                    (dialog, which)
                        -> activity.requestPermissions(new String[] {RECORD_AUDIO},
                                                       REQUEST_CODE_RECORD_PERMISSION));
                return false;
            }

            activity.requestPermissions(new String[] {RECORD_AUDIO},
                                        REQUEST_CODE_RECORD_PERMISSION);
            return false;
        }
        return true;
    }

    public static boolean hasWriteAccess(Context context) {
        // Android 11+：检查 isExternalStorageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        int hasWritePermission =
            ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE);
        return hasWritePermission == PackageManager.PERMISSION_GRANTED;

    }

    private static void showMessageOKCancel(final Context context, String message,
                                            DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, okListener)
            .setNegativeButton(
                android.R.string.cancel,
                (dialogInterface, i)
                    -> Toast.makeText(context, R.string.write_permission_needed, Toast.LENGTH_SHORT)
                           .show())
            .create()
            .show();
    }
}
