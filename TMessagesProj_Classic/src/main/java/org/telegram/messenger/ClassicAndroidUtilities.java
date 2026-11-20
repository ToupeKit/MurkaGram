package org.telegram.messenger;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.CalendarContract;
import android.provider.CallLog;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inspector.WindowInspector;
import android.webkit.MimeTypeMap;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.math.MathUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.exifinterface.media.ExifInterface;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import com.android.internal.telephony.ITelephony;
import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.auth.api.phone.SmsRetrieverClient;
import com.google.android.gms.tasks.Task;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import me.vkryl.core.BitwiseUtils;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.classic.ui.ActionBar.AlertDialog;
import org.telegram.classic.ui.ActionBar.BaseFragment;
import org.telegram.classic.ui.ActionBar.BottomSheet;
import org.telegram.classic.ui.ActionBar.INavigationLayout;
import org.telegram.classic.ui.ActionBar.Theme;
import org.telegram.classic.ui.ActionBar.ThemeBind;
import org.telegram.classic.ui.ChatActivity;
import org.telegram.classic.ui.Components.AlertsCreator;
import org.telegram.classic.ui.Components.Bulletin;
import org.telegram.classic.ui.Components.ButtonSpan;
import org.telegram.classic.ui.Components.CubicBezierInterpolator;
import org.telegram.classic.ui.Components.HideViewAfterAnimation;
import org.telegram.classic.ui.Components.LayoutHelper;
import org.telegram.classic.ui.Components.PipRoundVideoView;
import org.telegram.classic.ui.Components.RecyclerListView;
import org.telegram.classic.ui.Components.ShareAlert;
import org.telegram.classic.ui.Components.TableView;
import org.telegram.classic.ui.Components.TextHelper;
import org.telegram.classic.ui.Components.UndoView;
import org.telegram.classic.ui.Components.spoilers.SpoilersTextView;
import org.telegram.classic.ui.DebugRecordingCanvasReplayFragment;
import org.telegram.classic.ui.LaunchActivity;
import org.telegram.classic.ui.Stories.PeerStoriesView;
import org.telegram.classic.ui.Stories.StoryMediaAreasView;
import org.telegram.classic.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.classic.ui.ThemePreviewActivity;
import org.telegram.classic.ui.WallpapersListActivity;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.utils.CustomHtml;
import org.telegram.messenger.utils.DebugRecordingCanvas;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.ForegroundColorSpanThemable;
import org.telegram.ui.Components.ForegroundDetector;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.utils.proxy.ProxySettings;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

// MurkaGram: dual UI. Methods of AndroidUtilities whose parameters are UI types, copied from the core-13 (12.2.10 UI) version
// so that the classic UI can call them with its own types. Generated by murka-plans/tools/dual/classic_k10.py.
public final class ClassicAndroidUtilities {

    private ClassicAndroidUtilities() {
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.hasDialogOnTop
    public static boolean hasDialogOnTop(BaseFragment fragment) {
        if (fragment == null) return false;
        if (fragment.visibleDialog != null && !(fragment.visibleDialog instanceof AlertDialog) && !(
            fragment.visibleDialog instanceof BottomSheet && ((BottomSheet) fragment.visibleDialog).attachedFragment != null
        )) return true;
        if (fragment.getParentLayout() == null) return false;
        List<View> globalViews = AndroidUtilities.allGlobalViews();
        if (globalViews == null || globalViews.isEmpty()) return false;
        View lastGlobalView = null;
        for (int i = globalViews.size() - 1; i >= 0; --i) {
            lastGlobalView = globalViews.get(i);
            if (fragment.visibleDialog instanceof AlertDialog) {
                if (lastGlobalView == AndroidUtilities.getRootView((((AlertDialog) fragment.visibleDialog).getContainerView()))) {
                    continue;
                }
            }
            if (!(
                lastGlobalView instanceof AlertDialog.AlertDialogView ||
                lastGlobalView instanceof PipRoundVideoView.PipFrameLayout
            )) break;
        }
        return lastGlobalView != AndroidUtilities.getRootView(fragment.getParentLayout().getView());
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.isMapsInstalled
    public static boolean isMapsInstalled(BaseFragment fragment) {
        String pkg = ApplicationLoader.getMapsProvider().getMapsAppPackageName();
        try {
            ApplicationLoader.applicationContext.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            if (fragment.getParentActivity() == null) {
                return false;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setMessage(getString(ApplicationLoader.getMapsProvider().getInstallMapsString()));
            builder.setPositiveButton(getString(R.string.OK), (dialogInterface, i) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                    fragment.getParentActivity().startActivityForResult(intent, 500);
                } catch (Exception e1) {
                    FileLog.e(e1);
                }
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            fragment.showDialog(builder.create());
            return false;
        }
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.openDocument
    public static void openDocument(MessageObject message, Activity activity, BaseFragment parentFragment) {
        if (message == null) {
            return;
        }
        TLRPC.Document document = message.getDocument();
        if (document == null) {
            return;
        }
        File f = null;
        String fileName = message.messageOwner.media != null ? FileLoader.getAttachFileName(document) : "";
        if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
            f = new File(message.messageOwner.attachPath);
        }
        if (f == null || f != null && !f.exists()) {
            f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(message.messageOwner);
        }
        if (f != null && f.exists()) {
            String murkaDocName = message.getDocumentName(); // MurkaGram: пакет дизайна .mgdesign — окно предпросмотра
            if (parentFragment != null && murkaDocName != null && murkaDocName.toLowerCase(Locale.ROOT).endsWith(MurkaDesignPack.EXTENSION)) {
                org.telegram.classic.ui.MurkaDesignPacksActivity.openFile(parentFragment, f);
                return;
            }
            if (parentFragment != null && f.getName().toLowerCase().endsWith("attheme")) {
                Theme.ThemeInfo themeInfo = Theme.applyThemeFile(f, message.getDocumentName(), null, true);
                if (themeInfo != null) {
                    parentFragment.presentFragment(new ThemePreviewActivity(themeInfo));
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    Map<String, Integer> colorsReplacement = new HashMap<>();
                    colorsReplacement.put("info1", parentFragment.getThemedColor(Theme.key_dialogTopBackground));
                    colorsReplacement.put("info2", parentFragment.getThemedColor(Theme.key_dialogTopBackground));
                    builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, parentFragment.getThemedColor(Theme.key_dialogTopBackground), colorsReplacement);
                    builder.setTopAnimationIsNew(true);
                    builder.setMessage(getString(R.string.IncorrectTheme));
                    builder.setPositiveButton(getString(R.string.OK), null);
                    parentFragment.showDialog(builder.create());
                }
            } else {
                String realMimeType = null;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    MimeTypeMap myMime = MimeTypeMap.getSingleton();
                    int idx = fileName.lastIndexOf('.');
                    if (idx != -1) {
                        String ext = fileName.substring(idx + 1);
                        realMimeType = myMime.getMimeTypeFromExtension(ext.toLowerCase());
                        if (realMimeType == null) {
                            realMimeType = document.mime_type;
                            if (realMimeType == null || realMimeType.length() == 0) {
                                realMimeType = null;
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 24) {
                        intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), realMimeType != null ? realMimeType : "text/plain");
                    } else {
                        intent.setDataAndType(Uri.fromFile(f), realMimeType != null ? realMimeType : "text/plain");
                    }
                    if (realMimeType != null) {
                        try {
                            activity.startActivityForResult(intent, 500);
                        } catch (Exception e) {
                            if (Build.VERSION.SDK_INT >= 24) {
                                intent.setDataAndType(FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", f), "text/plain");
                            } else {
                                intent.setDataAndType(Uri.fromFile(f), "text/plain");
                            }
                            activity.startActivityForResult(intent, 500);
                        }
                    } else {
                        activity.startActivityForResult(intent, 500);
                    }
                } catch (Exception e) {
                    if (activity == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                    Map<String, Integer> colorsReplacement = new HashMap<>();
                    colorsReplacement.put("info1", parentFragment.getThemedColor(Theme.key_dialogTopBackground));
                    colorsReplacement.put("info2", parentFragment.getThemedColor(Theme.key_dialogTopBackground));
                    builder.setTopAnimation(R.raw.not_available, AlertsCreator.NEW_DENY_DIALOG_TOP_ICON_SIZE, false, parentFragment.getThemedColor(Theme.key_dialogTopBackground), colorsReplacement);
                    builder.setTopAnimationIsNew(true);
                    builder.setPositiveButton(getString(R.string.OK), null);
                    builder.setMessage(LocaleController.formatString("NoHandleAppInstalled", R.string.NoHandleAppInstalled, message.getDocument().mime_type));
                    if (parentFragment != null) {
                        parentFragment.showDialog(builder.create());
                    } else {
                        builder.show();
                    }
                }
            }
        }
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.scrollToFragmentRow
    public static void scrollToFragmentRow(INavigationLayout parentLayout, String rowName) {
        if (parentLayout == null || rowName == null) {
            return;
        }
        BaseFragment openingFragment = parentLayout.getFragmentStack().get(parentLayout.getFragmentStack().size() - 1);
        try {
            Field listViewField = openingFragment.getClass().getDeclaredField("listView");
            listViewField.setAccessible(true);
            RecyclerListView listView = (RecyclerListView) listViewField.get(openingFragment);
            RecyclerListView.IntReturnCallback callback = () -> {
                int position = -1;
                try {
                    Field rowField = openingFragment.getClass().getDeclaredField(rowName);
                    rowField.setAccessible(true);
                    LinearLayoutManager layoutManager = (LinearLayoutManager) listView.getLayoutManager();
                    position = rowField.getInt(openingFragment);
                    layoutManager.scrollToPositionWithOffset(position, AndroidUtilities.dp(60));
                    rowField.setAccessible(false);
                    return position;
                } catch (Throwable ignore) {

                }
                return position;
            };
            listView.highlightRow(callback);
            listViewField.setAccessible(false);
        } catch (Throwable ignore) {

        }
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.updateVisibleRow
    public static void updateVisibleRow(RecyclerListView listView, int position) {
        if (listView == null) {
            return;
        }
        RecyclerView.Adapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int p = listView.getChildAdapterPosition(child);
            if (p >= 0) {
                RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
                if (holder == null || holder.shouldIgnore() || holder.getAdapterPosition() != position) {
                    continue;
                }
                adapter.onBindViewHolder(holder, p);
            }
        }
    }

    // MurkaGram: dual UI, core-13 AndroidUtilities.updateVisibleRows
    public static void updateVisibleRows(RecyclerListView listView) {
        if (listView == null) {
            return;
        }
        RecyclerView.Adapter adapter = listView.getAdapter();
        if (adapter == null) {
            return;
        }
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            int p = listView.getChildAdapterPosition(child);
            if (p >= 0) {
                RecyclerView.ViewHolder holder = listView.getChildViewHolder(child);
                if (holder == null || holder.shouldIgnore()) {
                    continue;
                }
                adapter.onBindViewHolder(holder, p);
            }
        }
    }

}
