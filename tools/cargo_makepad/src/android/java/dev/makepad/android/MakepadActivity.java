package dev.makepad.android;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ActionMode;
import android.view.Display;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import dev.mpmux.android.MpmuxUpdateManifestVerifier;
import dev.mpmux.android.MpmuxVoiceInputBridge;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;
import java.util.concurrent.CompletableFuture;

// note: //% is a special miniquad's pre-processor for plugins
// when there are no plugins - //% whatever will be replaced to an empty string
// before compiling

//% IMPORTS

class MakepadImeInsets {
    private static final int MIN_KEYBOARD_HEIGHT_DP = 80;

    // True while a soft-keyboard (IME) show/hide animation is running. While an
    // animation is in flight the per-frame WindowInsetsAnimation callback
    // (onProgress/onEnd) is the authoritative inset source; the layout-driven
    // fallbacks (onApplyWindowInsets, onGlobalLayout) observe a contradictory
    // mix of target and stale insets mid-animation, so they defer to it.
    static boolean imeAnimationInProgress = false;

    private static int keyboardThresholdPx(View view) {
        return Math.max(1, (int) (view.getResources().getDisplayMetrics().density * MIN_KEYBOARD_HEIGHT_DP));
    }

    private static int rootHeightPx(View view) {
        View root = view.getRootView();
        return root == null ? 0 : root.getHeight();
    }

    // Distance in pixels from the bottom edge of the render surface up to the
    // bottom edge of the window. Zero when the window is edge-to-edge; equal to
    // the navigation-bar height when it is not (the framework then lays the
    // surface out above the navigation bar). The IME and visible-frame
    // measurements below are relative to the window bottom, so this gap must be
    // subtracted to get the IME's overlap with the surface itself.
    private static int surfaceBottomGapPx(View view) {
        View root = view.getRootView();
        if (root == null || root.getHeight() <= 0 || view.getHeight() <= 0) {
            return 0;
        }
        int[] loc = new int[2];
        view.getLocationInWindow(loc);
        int surfaceBottom = loc[1] + view.getHeight();
        return Math.max(0, root.getHeight() - surfaceBottom);
    }

    private static int clampToRootHeight(View view, int overlap) {
        int rootHeight = rootHeightPx(view);
        if (rootHeight <= 0 || overlap <= 0) {
            return 0;
        }
        return Math.min(overlap, rootHeight);
    }

    private static boolean isNearFullHeightOverlap(View view, int overlap) {
        int rootHeight = rootHeightPx(view);
        return rootHeight > 0 && rootHeight - overlap <= keyboardThresholdPx(view);
    }

    private static int visibleFrameBottomOverlapPx(View view) {
        Rect visibleFrame = new Rect();
        view.getWindowVisibleDisplayFrame(visibleFrame);

        View root = view.getRootView();
        if (root == null || root.getHeight() <= 0) {
            return 0;
        }

        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        if (visibleFrame.isEmpty() || visibleFrame.bottom <= rootLocation[1]) {
            return 0;
        }

        int rootBottomOnScreen = rootLocation[1] + root.getHeight();
        // visibleFrame.bottom is relative to the window; subtract the gap below
        // the surface so the fallback also measures overlap with the surface.
        return Math.max(0, rootBottomOnScreen - visibleFrame.bottom - surfaceBottomGapPx(view));
    }

    static int bottomOverlapPx(View view, WindowInsets insets) {
        int imeBottom = 0;
        if (Build.VERSION.SDK_INT >= 30 && insets != null) {
            Insets imeInsets = insets.getInsets(WindowInsets.Type.ime());
            // imeInsets.bottom is measured from the window bottom; subtract the
            // gap below the surface so only the IME's overlap with the surface
            // shifts content (a non-edge-to-edge window would otherwise
            // over-shift the content by the navigation-bar height).
            int imeOverlap = Math.max(0, imeInsets.bottom - surfaceBottomGapPx(view));
            imeBottom = clampToRootHeight(view, imeOverlap);
        }

        if (imeBottom > 0 && !isNearFullHeightOverlap(view, imeBottom)) {
            return imeBottom;
        }

        // Fallback for Android/OEM paths where Type.ime().bottom reports 0,
        // most commonly landscape keyboards. Using only the bottom edge avoids
        // counting status-bar differences at the top of the window.
        int fallback = visibleFrameBottomOverlapPx(view);
        if (fallback <= keyboardThresholdPx(view)) {
            return 0;
        }

        // A visible frame that is basically empty is not a keyboard measurement;
        // it is a transient/invalid layout result. Do not turn it into a
        // near-full-screen IME height.
        if (isNearFullHeightOverlap(view, fallback)) {
            return 0;
        }
        return clampToRootHeight(view, fallback);
    }

    // Whether the IME should be reported as "open" to native code.
    //
    // This must reflect the *target* (settled) IME visibility, not the
    // per-frame animated inset. During a show animation onProgress() delivers
    // insets whose IME height ramps up from 0, and at height 0 those animated
    // insets report isVisible(ime)==false — treating that first frame as
    // "closed" makes showing the keyboard look like an instant dismissal (and
    // the native side then actually hides it). getRootWindowInsets() reflects
    // the requested IME visibility and stays stable for the whole animation,
    // so it is the authoritative source for the open/closed flag; the
    // per-frame bottomOverlap height still drives the content-shift animation.
    static boolean isVisible(View view, int bottomOverlapPx) {
        if (Build.VERSION.SDK_INT >= 30 && view != null) {
            WindowInsets root = view.getRootWindowInsets();
            if (root != null && root.isVisible(WindowInsets.Type.ime())) {
                // Target is "shown": open for the whole show animation, even
                // while the animated height is still ramping up from 0.
                return true;
            }
        }
        // Target is "hidden" (or pre-API-30): still open while the IME
        // occupies space, so a hide animation reports open until it finishes
        // collapsing and then closed.
        return bottomOverlapPx > 0;
    }

    static void report(View view, WindowInsets insets, String src) {
        // While an IME animation is running, only the per-frame
        // WindowInsetsAnimation callback (onProgress/onEnd) is authoritative.
        // The layout-driven fallbacks (onApplyWindowInsets, onGlobalLayout) see
        // contradictory insets mid-animation and would fight the animation
        // callback, so they defer to it.
        if (imeAnimationInProgress
                && (src.equals("onApplyWindowInsets") || src.equals("onGlobalLayout"))) {
            return;
        }
        int bottomOverlap = bottomOverlapPx(view, insets);
        boolean visible = isVisible(view, bottomOverlap);
        MakepadNative.surfaceOnResizeTextIME(bottomOverlap, visible);
    }
}

class MakepadSystemInsets {
    final float top;
    final float right;
    final float bottom;
    final float left;

    private MakepadSystemInsets(float top, float right, float bottom, float left) {
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.left = left;
    }

    // Computes the safe-area insets the render surface actually needs.
    //
    // The system-bar + display-cutout insets describe bands at the *window*
    // edges. When the window is not edge-to-edge (the default below Android 15
    // / API 35, where targetSdk-35 edge-to-edge enforcement does not apply),
    // the framework already lays our content out *inside* the system bars, so
    // the surface does not overlap them at all. Reporting the raw window-edge
    // insets there would pad the content twice — once by the OS, once by
    // Makepad — leaving an oversized gap. To stay correct in both regimes we
    // report only the part of each bar band that actually overlaps the
    // surface's on-screen rectangle (the same overlap approach used for the
    // IME inset). Edge-to-edge: overlap == full bar size. Content inside the
    // bars: overlap == 0.
    @SuppressWarnings("deprecation")
    static MakepadSystemInsets from(View view, WindowInsets insets, float density) {
        if (insets == null || view == null || density <= 0.0f) {
            return new MakepadSystemInsets(0, 0, 0, 0);
        }

        // Raw system-bar + display-cutout insets, in pixels, at the window edges.
        int barTop, barRight, barBottom, barLeft;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(
                WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
            );
            barTop = bars.top;
            barRight = bars.right;
            barBottom = bars.bottom;
            barLeft = bars.left;
        } else {
            barTop = insets.getSystemWindowInsetTop();
            barRight = insets.getSystemWindowInsetRight();
            barBottom = insets.getSystemWindowInsetBottom();
            barLeft = insets.getSystemWindowInsetLeft();
        }

        View root = view.getRootView();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return new MakepadSystemInsets(0, 0, 0, 0);
        }
        int windowWidth = root.getWidth();
        int windowHeight = root.getHeight();

        // The surface rectangle in window coordinates (same space as the
        // system-bar insets above). An un-laid-out view yields a degenerate
        // rect, which the intersections below collapse to zero insets.
        int[] loc = new int[2];
        view.getLocationInWindow(loc);
        int surfaceLeft = loc[0];
        int surfaceTop = loc[1];
        int surfaceRight = surfaceLeft + view.getWidth();
        int surfaceBottom = surfaceTop + view.getHeight();

        // Intersection length of each window-edge bar band with the surface.
        int top = Math.max(0,
            Math.min(barTop, surfaceBottom) - Math.max(0, surfaceTop));
        int left = Math.max(0,
            Math.min(barLeft, surfaceRight) - Math.max(0, surfaceLeft));
        int bottom = Math.max(0,
            Math.min(windowHeight, surfaceBottom) - Math.max(windowHeight - barBottom, surfaceTop));
        int right = Math.max(0,
            Math.min(windowWidth, surfaceRight) - Math.max(windowWidth - barRight, surfaceLeft));

        return new MakepadSystemInsets(
            top / density,
            right / density,
            bottom / density,
            left / density
        );
    }

    // Computes the safe-area (system-bar + display-cutout) insets and pushes
    // them to native code. Called from both ResizingLayout.onApplyWindowInsets
    // (the primary inset dispatch) and MakepadSurface.onGlobalLayout (a
    // per-layout fallback). The fallback is what makes the safe area correct
    // from launch: onApplyWindowInsets is not reliably dispatched with settled
    // system-bar insets on a cold start, so without the fallback the app
    // renders edge-to-edge (content under the status bar) until an IME show or
    // a rotation forces a fresh inset dispatch. The native side dedups
    // unchanged values, so calling this on every layout pass is cheap.
    static void report(View view, WindowInsets insets) {
        float density = view.getResources().getDisplayMetrics().density;
        MakepadSystemInsets i = MakepadSystemInsets.from(view, insets, density);
        MakepadNative.surfaceOnSafeAreaInsets(i.top, i.right, i.bottom, i.left);
    }
}

class MakepadSurface
    extends
        SurfaceView
    implements
        View.OnTouchListener,
        View.OnKeyListener,
        View.OnLongClickListener,
        ViewTreeObserver.OnGlobalLayoutListener,
        SurfaceHolder.Callback
{
    // IME InputConnection for handling composition text
    private MakepadInputConnection mInputConnection;

    // Shared Editable buffer for IME - this is the source of truth for Java side
    private SpannableStringBuilder mEditable = new SpannableStringBuilder();

    // Keyboard configuration constants (must match Rust KeyboardType enum)
    static final int INPUT_MODE_TEXT = 0;
    static final int INPUT_MODE_ASCII = 1;
    static final int INPUT_MODE_URL = 2;
    static final int INPUT_MODE_NUMERIC = 3;
    static final int INPUT_MODE_TEL = 4;
    static final int INPUT_MODE_EMAIL = 5;
    static final int INPUT_MODE_DECIMAL = 6;
    static final int INPUT_MODE_SEARCH = 7;
    static final int INPUT_MODE_NONE = 8;

    // Autocapitalize constants (must match Rust Autocapitalize enum)
    static final int AUTOCAP_NONE = 0;
    static final int AUTOCAP_WORDS = 1;
    static final int AUTOCAP_SENTENCES = 2;
    static final int AUTOCAP_ALL = 3;

    // Autocorrect constants (must match Rust Autocorrect enum)
    static final int AUTOCORRECT_DEFAULT = 0;
    static final int AUTOCORRECT_YES = 1;
    static final int AUTOCORRECT_NO = 2;

    // Return key type constants (must match Rust ReturnKeyType enum)
    static final int RETURN_KEY_DEFAULT = 0;
    static final int RETURN_KEY_GO = 1;
    static final int RETURN_KEY_SEARCH = 2;
    static final int RETURN_KEY_SEND = 3;
    static final int RETURN_KEY_NEXT = 4;
    static final int RETURN_KEY_DONE = 5;
    static final int RETURN_KEY_NONE = 6;
    static final int RETURN_KEY_PREVIOUS = 7;

    // Keyboard configuration (set by Rust via configureKeyboard)
    private int mInputMode = INPUT_MODE_TEXT;
    private int mAutocapitalize = AUTOCAP_SENTENCES;
    private int mAutocorrect = AUTOCORRECT_DEFAULT;
    private int mReturnKeyType = RETURN_KEY_DEFAULT;
    private boolean mIsMultiline = true;
    private boolean mIsSecure = false;

    // Package-private getters for MakepadInputConnection to access shared state
    Editable getEditable() {
        return mEditable;
    }

    int getInputMode() {
        return mInputMode;
    }

    boolean isMultiline() {
        return mIsMultiline;
    }

    // The X,Y coordinates and pointer ID of the most recent ACTION_DOWN touch.
    private float latestDownTouchX = Float.NaN;
    private float latestDownTouchY = Float.NaN;
    private int latestDownTouchPointerId = -1;

    // The X,Y coordinates and pointer ID of the most recent non-ACTION_DOWN touch event.
    private float latestTouchX = Float.NaN;
    private float latestTouchY = Float.NaN;
    private int latestTouchPointerId = -1;


    public MakepadSurface(Context context){
        super(context);
        getHolder().addCallback(this);

        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        setOnTouchListener(this);
        setOnKeyListener(this);
        setOnLongClickListener(this);

        getViewTreeObserver().addOnGlobalLayoutListener(this);

        Selection.setSelection(mEditable, 0, 0);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        //surface.setFrameRate(120f,0);
        MakepadNative.surfaceOnSurfaceCreated(surface);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Context context = getContext();
        if (context instanceof MakepadActivity) {
            MakepadActivity activity = (MakepadActivity) context;
            if (activity.hasRecoverySnapshotAvailable()) {
                activity.setSurfaceCoverVisible(true);
            }
        }
        Surface surface = holder.getSurface();
        MakepadNative.surfaceOnSurfaceDestroyed(surface);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder,
                               int format,
                               int width,
                               int height) {
        Surface surface = holder.getSurface();
        //surface.setFrameRate(120f,0);
        MakepadNative.surfaceOnSurfaceChanged(surface, width, height);

    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        // By default, we return false so that `onLongClick` will trigger.
        boolean retval = false;

        int actionMasked = event.getActionMasked();
        int index = event.getActionIndex();
        int pointerId = event.getPointerId(index);

        // Save the details of the latest touch-down event,
        // such that we can use them in the `onLongClick` method.
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            latestDownTouchX = event.getX(index);
            latestDownTouchY = event.getY(index);
            latestDownTouchPointerId = pointerId;
            // Re-set the latestTouchX/Y values on each down-touch.
            latestTouchX = latestDownTouchX;
            latestTouchY = latestDownTouchY;
            latestTouchPointerId = -1;
        }
        else if (actionMasked == MotionEvent.ACTION_MOVE) {
            latestTouchX = event.getX(index);
            latestTouchY = event.getY(index);
            latestTouchPointerId = pointerId;
            if (pointerId == latestDownTouchPointerId) {
                if (isTouchBeyondSlopDistance(view)) {
                    retval = true;
                }
            }
        }

        // Every sample the input system batched into this move goes first
        // (a fling's velocity is read from all of them), then the event.
        if (actionMasked == MotionEvent.ACTION_MOVE) {
            int history = event.getHistorySize();
            for (int h = 0; h < history; h++) {
                long nanos = event.getHistoricalEventTime(h) * 1000000L;
                MakepadNative.surfaceOnTouchHistory(event, h, nanos);
            }
        }
        // (The build's SDK predates getEventTimeNanos: millisecond times.)
        long nanos = event.getEventTime() * 1000000L;
        MakepadNative.surfaceOnTouchNanos(event, nanos);
        return retval;
    }

    @Override
    public boolean onLongClick(View view) {
        long timeMillis = SystemClock.uptimeMillis();

        if (isTouchBeyondSlopDistance(view)) {
            return false;
        }

        // Here: a valid long click did occur, and we should send that event to makepad.

        // Use the latest touch coordinates if they're the same pointer ID as the initial down touch.
        if (latestTouchPointerId == latestDownTouchPointerId) {
            MakepadNative.surfaceOnLongClick(latestTouchX, latestTouchY, latestDownTouchPointerId, timeMillis);
        }
        // Otherwise, use the coordinates from the original down touch.
        else {
            MakepadNative.surfaceOnLongClick(latestDownTouchX, latestDownTouchY, latestDownTouchPointerId, timeMillis);
        }

        // Returning true here indicates that we have handled the long click event,
        // which triggers the haptic feedback (vibration motor) to buzz.
        return true;
    }

    // Returns true if the distance from the latest touch event to the prior down-touch event
    // is greated than the touch slop distance.
    //
    // If true, this indicates that the touch event shouldn't be considered a press/tap,
    // and is likely a drag or swipe.
    private boolean isTouchBeyondSlopDistance(View view) {
        int touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        float deltaX = latestTouchX - latestDownTouchX;
        float deltaY = latestTouchY - latestDownTouchY;
        double dist = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY));
        return dist > touchSlop;
    }

    @Override
    public void onGlobalLayout() {
        // Fallback path: the parent ResizingLayout's OnApplyWindowInsetsListener
        // is the primary source of IME inset updates (it fires per-frame during
        // the keyboard animation on API 30+). This handler stays as a safety
        // net for layout changes that arrive without an inset dispatch, for
        // example, a focus change that retargets the IME to a different field.
        WindowInsets insets = this.getRootWindowInsets();
        MakepadImeInsets.report(this, insets, "onGlobalLayout");
        // Safe-area insets also flow through here. onApplyWindowInsets is not
        // reliably dispatched with settled system-bar insets on a cold start,
        // so without this the app renders edge-to-edge (content under the
        // status bar) until an IME show or rotation forces a fresh inset
        // dispatch. onGlobalLayout fires on every layout pass and picks up the
        // real insets as soon as the window settles.
        MakepadSystemInsets.report(this, insets);
    }

    // docs says getCharacters are deprecated
    // but somehow on non-latyn input all keyCode and all the relevant fields in the KeyEvent are zeros
    // and only getCharacters has some usefull data
    @SuppressWarnings("deprecation")
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode != 0) {
            int metaState = event.getMetaState();
            boolean isRepeat = event.getRepeatCount() > 0;
            MakepadNative.surfaceOnKeyDown(keyCode, metaState, isRepeat);
        }

        if (event.getAction() == KeyEvent.ACTION_UP && keyCode != 0) {
            int metaState = event.getMetaState();
            MakepadNative.surfaceOnKeyUp(keyCode, metaState);
        }

        if (event.getAction() == KeyEvent.ACTION_UP || event.getAction() == KeyEvent.ACTION_MULTIPLE) {
            int character = event.getUnicodeChar();
            if (character == 0) {
                String characters = event.getCharacters();
                if (characters != null && characters.length() > 0) {
                    character = characters.charAt(0);
                }
            }

            if (character != 0) {
                MakepadNative.surfaceOnCharacter(character);
            }
        }

        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP) || (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            return super.onKeyUp(keyCode, event);
        }

        return true;
    }

    // There is an Android bug when screen is in landscape,
    // the keyboard inset height is reported as 0.
    // This code is a workaround which fixes the bug.
    // See https://groups.google.com/g/android-developers/c/50XcWooqk7I
    // For some reason it only works if placed here and not in the parent layout.
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        int inputType = InputType.TYPE_CLASS_TEXT;

        switch (mInputMode) {
            case INPUT_MODE_NONE:
                inputType = InputType.TYPE_NULL;
                break;
            case INPUT_MODE_ASCII:
                // TYPE_TEXT_VARIATION_VISIBLE_PASSWORD shows ASCII keyboard without masking
                // This is the closest Android equivalent to iOS's UIKeyboardTypeASCIICapable
                inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
                break;
            case INPUT_MODE_URL:
                inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI;
                break;
            case INPUT_MODE_NUMERIC:
                inputType = InputType.TYPE_CLASS_NUMBER;
                break;
            case INPUT_MODE_TEL:
                inputType = InputType.TYPE_CLASS_PHONE;
                break;
            case INPUT_MODE_EMAIL:
                inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
                break;
            case INPUT_MODE_DECIMAL:
                inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
                break;
            case INPUT_MODE_SEARCH:
                inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
                break;
            default: // INPUT_MODE_TEXT
                inputType = InputType.TYPE_CLASS_TEXT;
                break;
        }

        if ((inputType & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT) {
            // Autocapitalization
            switch (mAutocapitalize) {
                case AUTOCAP_NONE:
                    // No flag needed
                    break;
                case AUTOCAP_WORDS:
                    inputType |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
                    break;
                case AUTOCAP_SENTENCES:
                    inputType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
                    break;
                case AUTOCAP_ALL:
                    inputType |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
                    break;
            }

            // Autocorrect
            switch (mAutocorrect) {
                case AUTOCORRECT_DEFAULT:
                    break;
                case AUTOCORRECT_YES:
                    inputType |= InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
                    break;
                case AUTOCORRECT_NO:
                    inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
                    break;
            }

            // Multiline - important for SwiftKey vertical cursor control
            if (mIsMultiline) {
                inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            }

            // Secure/password
            if (mIsSecure) {
                // Clear variation bits and set password variation
                inputType = (inputType & ~InputType.TYPE_MASK_VARIATION) | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            }
        }

        outAttrs.inputType = inputType;

        int imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN | EditorInfo.IME_FLAG_NO_EXTRACT_UI;

        // Return key type
        switch (mReturnKeyType) {
            case RETURN_KEY_NONE:
                imeOptions |= EditorInfo.IME_ACTION_NONE;
                break;
            case RETURN_KEY_GO:
                imeOptions |= EditorInfo.IME_ACTION_GO;
                break;
            case RETURN_KEY_SEARCH:
                imeOptions |= EditorInfo.IME_ACTION_SEARCH;
                break;
            case RETURN_KEY_SEND:
                imeOptions |= EditorInfo.IME_ACTION_SEND;
                break;
            case RETURN_KEY_NEXT:
                imeOptions |= EditorInfo.IME_ACTION_NEXT;
                break;
            case RETURN_KEY_DONE:
                imeOptions |= EditorInfo.IME_ACTION_DONE;
                break;
            case RETURN_KEY_PREVIOUS:
                imeOptions |= EditorInfo.IME_ACTION_PREVIOUS;
                break;
            default: // RETURN_KEY_DEFAULT
                if (!mIsMultiline) {
                    imeOptions |= EditorInfo.IME_ACTION_DONE;
                } else {
                    imeOptions |= EditorInfo.IME_FLAG_NO_ENTER_ACTION;
                }
                break;
        }

        // Prevent personalized learning for secure/password fields
        if (mIsSecure) {
            imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }

        // Add IME_FLAG_FORCE_ASCII for ASCII input mode
        if (mInputMode == INPUT_MODE_ASCII) {
            imeOptions |= EditorInfo.IME_FLAG_FORCE_ASCII;
        }

        outAttrs.imeOptions = imeOptions;

        // Set initial selection from our Editable
        int selStart = Selection.getSelectionStart(mEditable);
        int selEnd = Selection.getSelectionEnd(mEditable);
        outAttrs.initialSelStart = Math.max(0, selStart);
        outAttrs.initialSelEnd = Math.max(0, selEnd);
        // EditorInfo.setInitialSurroundingSubText is API 30+. It's only an
        // optimization (it hands the IME the surrounding text up-front); on
        // older devices the IME just queries it on demand through the
        // InputConnection. Calling it unconditionally crashes API 26-29 with
        // NoSuchMethodError.
        if (Build.VERSION.SDK_INT >= 30) {
            outAttrs.setInitialSurroundingSubText(mEditable, 0);
        }

        // Create InputConnection with fullEditor=true since we have an Editable
        mInputConnection = new MakepadInputConnection(this, true);

        return mInputConnection;
    }

    // Configure keyboard settings - called from Rust before showing keyboard
    public void configureKeyboard(int inputMode, int autocapitalize, int autocorrect,
                                  int returnKeyType, boolean isMultiline, boolean isSecure) {
        boolean changed = (mInputMode != inputMode || mAutocapitalize != autocapitalize ||
                          mAutocorrect != autocorrect || mReturnKeyType != returnKeyType ||
                          mIsMultiline != isMultiline || mIsSecure != isSecure);

        mInputMode = inputMode;
        mAutocapitalize = autocapitalize;
        mAutocorrect = autocorrect;
        mReturnKeyType = returnKeyType;
        mIsMultiline = isMultiline;
        mIsSecure = isSecure;

        // If config changed and keyboard is already showing, restart input to apply new settings
        if (changed && mInputConnection != null) {
            // Finalize any in-progress composition before restart to avoid stale state
            BaseInputConnection.removeComposingSpans(mEditable);
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.restartInput(this);
            }
        }
    }

    // Called from Rust to update text state (for programmatic changes, not IME input)
    public void updateImeTextState(String fullText, int selStart, int selEnd,
                                   int composingStart, int composingEnd) {
        String currentText = mEditable.toString();
        boolean textChanged = !currentText.equals(fullText);

        // ECHO PREVENTION: Check if this is Rust echoing back text we recently sent.
        // This happens because:
        //   1. Java sends text to Rust via onImeTextStateChanged
        //   2. Rust widget processes it and updates internal state
        //   3. Rust may sync state back via SyncImeState -> updateImeTextState
        //   4. Without this check, we'd overwrite fresh IME state with stale echo
        if (textChanged && mInputConnection != null) {
            if (mInputConnection.wasRecentlySentToRust(fullText)) {
                return;  // Stale echo - ignore to prevent rollback
            }
        }

        // Clamp selection
        int textLen = textChanged ? fullText.length() : currentText.length();
        selStart = Math.max(0, Math.min(selStart, textLen));
        selEnd = Math.max(selStart, Math.min(selEnd, textLen));
        boolean hasComposition = composingStart >= 0 && composingEnd >= composingStart;
        if (hasComposition) {
            composingStart = Math.max(0, Math.min(composingStart, textLen));
            composingEnd = Math.max(composingStart, Math.min(composingEnd, textLen));
        } else {
            composingStart = -1;
            composingEnd = -1;
        }

        if (textChanged) {
            // Text content changed - update Editable and notify IME
            BaseInputConnection.removeComposingSpans(mEditable);
            mEditable.replace(0, mEditable.length(), fullText);
            Selection.setSelection(mEditable, selStart, selEnd);
            if (hasComposition && mInputConnection != null) {
                mInputConnection.setComposingRegion(composingStart, composingEnd);
            }

            // ECHO PREVENTION: Clear the sent buffer after applying Rust's authoritative
            // state update. This ensures the next text we send to Rust won't be incorrectly
            // detected as an echo. Only clear here, NOT in recordSentToRust().
            if (mInputConnection != null) {
                mInputConnection.clearRecentSentBuffer();
            }

            // Notify IME of text change without restarting input
            // restartInput() destroys composition state and causes IME flicker;
            // updateExtractedText() + updateSelection() is the lightweight alternative
            if (mInputConnection != null) {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    if (mInputConnection.mExtractedTextRequest != null) {
                        ExtractedText et = new ExtractedText();
                        et.text = fullText;
                        et.startOffset = 0;
                        et.selectionStart = selStart;
                        et.selectionEnd = selEnd;
                        imm.updateExtractedText(this, mInputConnection.mExtractedTextToken, et);
                    }
                    imm.updateSelection(this, selStart, selEnd, composingStart, composingEnd);
                }
            }
        } else {
            // Only selection changed - just update selection, no restart needed
            int currentSelStart = Selection.getSelectionStart(mEditable);
            int currentSelEnd = Selection.getSelectionEnd(mEditable);
            int currentCompStart = BaseInputConnection.getComposingSpanStart(mEditable);
            int currentCompEnd = BaseInputConnection.getComposingSpanEnd(mEditable);
            if (currentSelStart != selStart || currentSelEnd != selEnd
                    || currentCompStart != composingStart || currentCompEnd != composingEnd) {
                if (hasComposition && mInputConnection != null) {
                    mInputConnection.setComposingRegion(composingStart, composingEnd);
                } else {
                    BaseInputConnection.removeComposingSpans(mEditable);
                }
                Selection.setSelection(mEditable, selStart, selEnd);
                // Notify IME of selection change without restart
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.updateSelection(this, selStart, selEnd, composingStart, composingEnd);
                }
            }
        }
    }

    public Surface getNativeSurface() {
        return getHolder().getSurface();
    }

    // Select all text in the InputConnection's Editable and notify IME
    // Used by ActionMode's Select All to sync Java-side selection with Rust
    public void selectAllInEditable() {
        int len = mEditable.length();
        Selection.setSelection(mEditable, 0, len);
        // Notify IME of the selection change
        if (mInputConnection != null) {
            mInputConnection.notifyImeOfSelectionUpdate();
        }
    }
}

class CameraPreviewSurface extends SurfaceView implements SurfaceHolder.Callback {
    private final long mVideoId;

    public CameraPreviewSurface(Context context, long videoId) {
        super(context);
        mVideoId = videoId;
        getHolder().addCallback(this);
        setZOrderMediaOverlay(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Surface surface = holder.getSurface();
        if (surface != null) {
            MakepadNative.onCameraPreviewSurfaceReady(mVideoId, surface, getWidth(), getHeight());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Surface surface = holder.getSurface();
        if (surface != null) {
            MakepadNative.onCameraPreviewSurfaceReady(mVideoId, surface, width, height);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        MakepadNative.onCameraPreviewSurfaceDestroyed(mVideoId);
    }
}

class SelectionHandleView extends View {
    public SelectionHandleView(Context context, int color, int sizePx) {
        super(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        setBackground(bg);
        setClickable(true);
        setFocusable(false);
        setLayoutParams(new FrameLayout.LayoutParams(sizePx, sizePx));
    }
}

class ResizingLayout
    extends
        LinearLayout
    implements
        View.OnApplyWindowInsetsListener {

    public ResizingLayout(Context context){
        super(context);
        // Keep a stable non-black fallback behind the SurfaceView for task snapshots
        // and system transition frames that cannot capture the separate surface layer.
        setBackgroundResource(R.drawable.makepad_launch_background);
        setOnApplyWindowInsetsListener(this);

        // The IME animation API (API 30+) gives us an authoritative per-frame
        // dispatch of the IME inset that does NOT depend on softInputMode or
        // on the listener returning the right thing. `onApplyWindowInsets`
        // alone is unreliable across Android versions and orientations
        // (we've observed it firing in landscape but not portrait, and on
        // some OEMs not at all). With this callback attached we are
        // guaranteed to hear about every IME show / hide / animation
        // progress event.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            setWindowInsetsAnimationCallback(
                new android.view.WindowInsetsAnimation.Callback(
                    android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
                ) {
                    @Override
                    public void onPrepare(android.view.WindowInsetsAnimation animation) {
                        if ((animation.getTypeMask() & WindowInsets.Type.ime()) != 0) {
                            MakepadImeInsets.imeAnimationInProgress = true;
                        }
                    }

                    @Override
                    public android.view.WindowInsets onProgress(
                        android.view.WindowInsets insets,
                        java.util.List<android.view.WindowInsetsAnimation> runningAnimations
                    ) {
                        MakepadImeInsets.report(ResizingLayout.this, insets, "onProgress");
                        return insets;
                    }

                    @Override
                    public void onEnd(android.view.WindowInsetsAnimation animation) {
                        if ((animation.getTypeMask() & WindowInsets.Type.ime()) != 0) {
                            MakepadImeInsets.imeAnimationInProgress = false;
                        }
                        // The framework usually delivers a final-state inset
                        // through onProgress just before onEnd, but on some
                        // OEM devices it skips that last frame. Fetch the
                        // current insets directly to make sure native code
                        // sees the settled state.
                        android.view.WindowInsets insets = getRootWindowInsets();
                        if (insets == null) return;
                        MakepadImeInsets.report(ResizingLayout.this, insets, "onEnd");
                    }
                }
            );
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        // Report IME inset directly to native code. The in-app KeyboardView
        // is the single source of truth for shifting content above the soft
        // keyboard. We do not shrink the SurfaceView via setPadding; that
        // would double-count the obstruction (system shrinks the surface
        // *and* the KeyboardView shifts). The activity is configured with
        // `windowSoftInputMode="adjustNothing"` in the manifest, so the
        // system doesn't auto-resize either.
        MakepadImeInsets.report(v, insets, "onApplyWindowInsets");

        // Safe-area (system-bar + display-cutout) insets. Also reported from
        // MakepadSurface.onGlobalLayout as a cold-start fallback — see
        // MakepadSystemInsets.report.
        MakepadSystemInsets.report(v, insets);

        return insets;
    }
}

public class MakepadActivity
    extends Activity
    implements MidiManager.OnDeviceOpenedListener
{
    private static final String LOG_TAG = "Makepad";
    private static final String MPMUX_DEFAULT_UPDATE_MANIFEST_URL = "https://mightypainting.dev/mpmux/android/latest.json";
    private static final String MPMUX_UPDATE_PREFS = "mpmux-update";
    private static final String MPMUX_UPDATE_PREF_DOWNLOAD_ID = "download_id";
    private static final String MPMUX_UPDATE_PREF_SHA256 = "sha256";
    private static final String MPMUX_UPDATE_PREF_VERSION_LABEL = "version_label";
    private static final long MPMUX_STARTUP_UPDATE_CHECK_DELAY_MS = 2500;
    private static final long SURFACE_COVER_FADE_OUT_MS = 100;
    private static final long WARM_RESUME_SNAPSHOT_MAX_AGE_MS = 10000;
    private static final int TASK_DESCRIPTION_BACKGROUND_COLOR = 0xFF000000;
    private static Bitmap sWarmResumeSurfaceSnapshot;
    private static long sWarmResumeSurfaceSnapshotUptimeMs;
    private static int sWarmResumeSurfaceSnapshotOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED;
    //% MAIN_ACTIVITY_BODY

    private MakepadSurface view;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private InputManager mInputManager;
    private InputManager.InputDeviceListener mInputDeviceListener;
    private Boolean mPhysicalKeyboardConnected;
    private boolean mIsResumed = false;
    private BroadcastReceiver mMpmuxUpdateDownloadReceiver;
    private long mMpmuxUpdateDownloadId = -1;
    private String mMpmuxUpdateExpectedSha256;
    private String mMpmuxUpdateVersionLabel;
    private boolean mMpmuxStartupUpdateCheckScheduled = false;

    // video playback
    Handler mVideoPlaybackHandler;
    HandlerThread mVideoPlaybackThread;
    HashMap<Long, VideoPlayerRunnable> mVideoPlayerRunnables;

    // networking, make these static because of activity switching
    static HandlerThread mWebSocketsThread;
    static Handler mWebSocketsHandler;
    static HashMap<Long, MakepadWebSocket> mActiveWebsockets = new HashMap<>();
    static HashMap<Long, MakepadWebSocketReader> mActiveWebsocketsReaders = new HashMap<>();
    static HashMap<Long, MakepadSocketStream> mActiveSocketStreams = new HashMap<>();
    private boolean mIsSwitchingActivity = false;

    // Desired system-bar (status/navigation bar) icon tint, set from Rust via
    // setSystemBarAppearance(). true = dark icons (for light app backgrounds).
    private boolean mSystemBarDarkIcons = false;

    // File/folder dialogs (Storage Access Framework). Request codes we handed
    // out and are still waiting on; anything else arriving in onActivityResult
    // belongs to somebody else and must be left alone.
    private final HashSet<Integer> mFileDialogRequests = new HashSet<>();

    // clipboard actions (ActionMode for copy/paste/cut)
    private ActionMode mActionMode;
    private boolean mHasSelection = false;
    private int[] mSelectionBounds = new int[4]; // left, top, right, bottom
    private int mKeyboardShift = 0; // keyboard shift amount from Rust

    // native camera preview overlays
    private FrameLayout mRootLayout;
    private FrameLayout mSurfaceCoverOverlay;
    private ImageView mSurfaceSnapshotBackdrop;
    private ImageView mSurfaceSnapshotOverlay;
    private FrameLayout mCameraPreviewOverlay;
    private HashMap<Long, CameraPreviewSurface> mCameraPreviewViews = new HashMap<>();
    private FrameLayout mMpmuxQrScannerOverlay;
    private SurfaceView mMpmuxQrScannerPreview;
    private View mMpmuxQrScannerResultOverlay;
    private android.hardware.Camera mMpmuxQrScannerCamera;
    private boolean mMpmuxQrScannerActive = false;
    private boolean mMpmuxQrScannerFound = false;
    private boolean mMpmuxQrScannerPairingNotificationPending = false;
    private ResultPoint[] mMpmuxQrScannerResultPoints;
    private int mMpmuxQrScannerResultWidth;
    private int mMpmuxQrScannerResultHeight;
    private final Runnable mMpmuxQrScannerAutofocus = new Runnable() {
        @Override
        public void run() {
            if (!mMpmuxQrScannerActive || mMpmuxQrScannerCamera == null) {
                return;
            }
            try {
                mMpmuxQrScannerCamera.autoFocus((success, camera) -> {
                    if (mMpmuxQrScannerActive) {
                        mHandler.postDelayed(mMpmuxQrScannerAutofocus, 1200);
                    }
                });
            } catch (RuntimeException err) {
                mHandler.postDelayed(mMpmuxQrScannerAutofocus, 1500);
            }
        }
    };
    private Bitmap mLatestSurfaceSnapshot;
    private int mLatestSurfaceSnapshotOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED;
    private boolean mSurfaceSnapshotCopyInFlight = false;
    private boolean mSurfaceRecoveryOverlayVisible = false;

    // selection handles overlay
    private static final int SELECTION_HANDLE_START = 0;
    private static final int SELECTION_HANDLE_END = 1;
    private static final int SELECTION_DRAG_BEGIN = 0;
    private static final int SELECTION_DRAG_MOVE = 1;
    private static final int SELECTION_DRAG_END = 2;
    private FrameLayout mSelectionHandleOverlay;
    private SelectionHandleView mSelectionHandleStart;
    private SelectionHandleView mSelectionHandleEnd;
    private int mSelectionHandleSizePx;

    static {
        System.loadLibrary("makepad");
    }

    private boolean isPhysicalTextKeyboard(InputDevice device) {
        return device != null
            && device.isEnabled()
            && !device.isVirtual()
            && device.supportsSource(InputDevice.SOURCE_KEYBOARD)
            && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
    }

    private boolean hasPhysicalTextKeyboard() {
        if (mInputManager == null) {
            return false;
        }
        for (int id : mInputManager.getInputDeviceIds()) {
            if (isPhysicalTextKeyboard(mInputManager.getInputDevice(id))) {
                return true;
            }
        }
        return false;
    }

    private void reportPhysicalKeyboardIfChanged() {
        boolean connected = hasPhysicalTextKeyboard();
        if (mPhysicalKeyboardConnected != null
                && mPhysicalKeyboardConnected.booleanValue() == connected) {
            return;
        }
        mPhysicalKeyboardConnected = Boolean.valueOf(connected);
        MakepadNative.surfaceOnPhysicalKeyboardChanged(connected);
    }

    private void registerPhysicalKeyboardListener() {
        if (mInputManager == null) {
            mInputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        }
        if (mInputManager == null) {
            return;
        }
        if (mInputDeviceListener == null) {
            mInputDeviceListener = new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    reportPhysicalKeyboardIfChanged();
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    reportPhysicalKeyboardIfChanged();
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    reportPhysicalKeyboardIfChanged();
                }
            };
            mInputManager.registerInputDeviceListener(mInputDeviceListener, mHandler);
        }
        reportPhysicalKeyboardIfChanged();
    }

    private void unregisterPhysicalKeyboardListener() {
        if (mInputManager != null && mInputDeviceListener != null) {
            mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
        }
        mInputDeviceListener = null;
        mInputManager = null;
    }

    private void cacheWarmResumeSurfaceSnapshot(Bitmap snapshot) {
        if (snapshot == null) {
            return;
        }
        sWarmResumeSurfaceSnapshot = snapshot;
        sWarmResumeSurfaceSnapshotUptimeMs = SystemClock.uptimeMillis();
        sWarmResumeSurfaceSnapshotOrientation = getResources().getConfiguration().orientation;
    }

    private boolean canRestoreWarmResumeSurfaceSnapshot() {
        if (sWarmResumeSurfaceSnapshot == null) {
            return false;
        }
        if (SystemClock.uptimeMillis() - sWarmResumeSurfaceSnapshotUptimeMs > WARM_RESUME_SNAPSHOT_MAX_AGE_MS) {
            clearWarmResumeSurfaceSnapshot();
            return false;
        }
        int orientation = getResources().getConfiguration().orientation;
        return sWarmResumeSurfaceSnapshotOrientation == android.content.res.Configuration.ORIENTATION_UNDEFINED
            || sWarmResumeSurfaceSnapshotOrientation == orientation;
    }

    private void clearWarmResumeSurfaceSnapshot() {
        sWarmResumeSurfaceSnapshot = null;
        sWarmResumeSurfaceSnapshotUptimeMs = 0;
        sWarmResumeSurfaceSnapshotOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED;
    }

    private void clearLatestSurfaceSnapshot() {
        mLatestSurfaceSnapshot = null;
        mLatestSurfaceSnapshotOrientation = android.content.res.Configuration.ORIENTATION_UNDEFINED;
    }

    private void trimSurfaceSnapshotCaches() {
        clearWarmResumeSurfaceSnapshot();
        clearLatestSurfaceSnapshot();

        if (mSurfaceSnapshotBackdrop != null) {
            mSurfaceSnapshotBackdrop.setImageBitmap(null);
            mSurfaceSnapshotBackdrop.setVisibility(View.GONE);
        }
        if (mSurfaceSnapshotOverlay != null) {
            mSurfaceSnapshotOverlay.animate().cancel();
            mSurfaceSnapshotOverlay.setImageBitmap(null);
            mSurfaceSnapshotOverlay.setAlpha(1.0f);
            mSurfaceSnapshotOverlay.setVisibility(View.GONE);
        }
        if (mSurfaceRecoveryOverlayVisible && mSurfaceCoverOverlay != null) {
            mSurfaceCoverOverlay.animate().cancel();
            mSurfaceCoverOverlay.setAlpha(1.0f);
            mSurfaceCoverOverlay.setVisibility(View.VISIBLE);
            mSurfaceCoverOverlay.bringToFront();
        }
    }

    boolean hasRecoverySnapshotAvailable() {
        return mLatestSurfaceSnapshot != null;
    }

    private boolean hasCurrentOrientationRecoverySnapshot() {
        if (mLatestSurfaceSnapshot == null) {
            return false;
        }
        int orientation = getResources().getConfiguration().orientation;
        return mLatestSurfaceSnapshotOrientation == android.content.res.Configuration.ORIENTATION_UNDEFINED
            || mLatestSurfaceSnapshotOrientation == orientation;
    }

    private void restoreWarmResumeSurfaceSnapshotIfAvailable() {
        if (!canRestoreWarmResumeSurfaceSnapshot()) {
            return;
        }
        mLatestSurfaceSnapshot = sWarmResumeSurfaceSnapshot;
        mLatestSurfaceSnapshotOrientation = getResources().getConfiguration().orientation;
        updateSurfaceSnapshotBackdrop();
        clearWarmResumeSurfaceSnapshot();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (mWebSocketsThread == null || !mWebSocketsThread.isAlive()) {
            mWebSocketsThread = new HandlerThread("WebSocketsThread");
            mWebSocketsThread.start();
            mWebSocketsHandler = new Handler(mWebSocketsThread.getLooper());
        }

        // On API 30+, Theme.NoTitleBar.Fullscreen sets FLAG_FULLSCREEN which positions
        // the window below the status bar, conflicting with the modern WindowInsetsController.
        // Switch from the launch theme to the app theme and handle fullscreen programmatically.
        if (Build.VERSION.SDK_INT >= 30) {
            setTheme(R.style.MakepadAppTheme);
        }
        
        super.onCreate(savedInstanceState);
        
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setSoftInputMode(
            LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                | LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        );

        // Default state: content below system bars (status bar visible).
        // Apps that want fullscreen can request CxOsOp::FullscreenWindow which
        // calls applyFullScreen(true) to hide bars and extend content behind them.

        view = new MakepadSurface(this);
        // Put it inside a parent layout which can resize it using padding
        ResizingLayout layout = new ResizingLayout(this);
        FrameLayout surfaceContentLayout = new FrameLayout(this);
        surfaceContentLayout.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        surfaceContentLayout.setBackgroundResource(R.drawable.makepad_launch_background);

        mSurfaceSnapshotBackdrop = new ImageView(this);
        mSurfaceSnapshotBackdrop.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mSurfaceSnapshotBackdrop.setBackgroundResource(R.drawable.makepad_launch_background);
        mSurfaceSnapshotBackdrop.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mSurfaceSnapshotBackdrop.setClickable(false);
        mSurfaceSnapshotBackdrop.setFocusable(false);
        mSurfaceSnapshotBackdrop.setVisibility(View.GONE);
        surfaceContentLayout.addView(mSurfaceSnapshotBackdrop);

        view.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        surfaceContentLayout.addView(view);
        layout.addView(surfaceContentLayout);

        mRootLayout = new FrameLayout(this);
        mRootLayout.addView(layout);

        mSurfaceCoverOverlay = new FrameLayout(this);
        mSurfaceCoverOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mSurfaceCoverOverlay.setBackgroundResource(R.drawable.makepad_launch_background);
        mSurfaceCoverOverlay.setClickable(false);
        mSurfaceCoverOverlay.setFocusable(false);
        mSurfaceCoverOverlay.setAlpha(1.0f);
        mSurfaceCoverOverlay.setVisibility(View.GONE);
        mRootLayout.addView(mSurfaceCoverOverlay);

        mSurfaceSnapshotOverlay = new ImageView(this);
        mSurfaceSnapshotOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mSurfaceSnapshotOverlay.setBackgroundResource(R.drawable.makepad_launch_background);
        mSurfaceSnapshotOverlay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mSurfaceSnapshotOverlay.setClickable(false);
        mSurfaceSnapshotOverlay.setFocusable(false);
        mSurfaceSnapshotOverlay.setAlpha(1.0f);
        mSurfaceSnapshotOverlay.setVisibility(View.GONE);
        mRootLayout.addView(mSurfaceSnapshotOverlay);

        mCameraPreviewOverlay = new FrameLayout(this);
        mRootLayout.addView(mCameraPreviewOverlay);

        mSelectionHandleOverlay = new FrameLayout(this);
        mSelectionHandleOverlay.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mSelectionHandleOverlay.setClickable(false);
        mSelectionHandleOverlay.setFocusable(false);
        mSelectionHandleOverlay.setVisibility(View.GONE);
        mRootLayout.addView(mSelectionHandleOverlay);

        mSelectionHandleSizePx = Math.max(24, (int) (getResources().getDisplayMetrics().density * 24.0f));
        mSelectionHandleStart = new SelectionHandleView(this, 0xFF4A90E2, mSelectionHandleSizePx);
        mSelectionHandleEnd = new SelectionHandleView(this, 0xFF4A90E2, mSelectionHandleSizePx);
        mSelectionHandleStart.setOnTouchListener(createSelectionHandleDragListener(SELECTION_HANDLE_START));
        mSelectionHandleEnd.setOnTouchListener(createSelectionHandleDragListener(SELECTION_HANDLE_END));
        mSelectionHandleOverlay.addView(mSelectionHandleStart);
        mSelectionHandleOverlay.addView(mSelectionHandleEnd);

        setContentView(mRootLayout);
        restoreWarmResumeSurfaceSnapshotIfAvailable();
        updateTaskDescription();

        persistMpmuxPairingIntent(getIntent(), false);
        MakepadNative.activityOnCreate(this);
        registerPhysicalKeyboardListener();

        mVideoPlaybackThread = new HandlerThread("VideoPlayerThread");
        mVideoPlaybackThread.start(); // TODO: only start this if its needed.
        mVideoPlaybackHandler = new Handler(mVideoPlaybackThread.getLooper());
        mVideoPlayerRunnables = new HashMap<Long, VideoPlayerRunnable>();



        String cache_path = this.getCacheDir().getAbsolutePath();
        String data_path = this.getFilesDir().getAbsolutePath();
        float density = getResources().getDisplayMetrics().density;
        boolean isEmulator = this.isEmulator();
        String androidVersion = Build.VERSION.RELEASE;
        String buildNumber = Build.DISPLAY;
        int sdkVersion = Build.VERSION.SDK_INT;

        // Makepad ignores the kernel version, but the slot stays so the native
        // signature doesn't change.
        MakepadNative.onAndroidParams(cache_path, data_path, density, isEmulator, androidVersion, buildNumber, "");

        // Set volume keys to control music stream, we might want make this flexible for app devs
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        float refreshRate = getDeviceRefreshRate();
        MakepadNative.initChoreographer(refreshRate, sdkVersion);
        //% MAIN_ACTIVITY_ON_CREATE
        
    }

    @Override
    protected void onStart() {
        super.onStart();
        restoreSurfaceViewForWarmResumeIfNeeded();
        MakepadNative.activityOnStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsResumed = true;
        restoreSurfaceViewForWarmResumeIfNeeded();
        updateTaskDescription();
        MakepadNative.activityOnResume();
        reportPhysicalKeyboardIfChanged();
        resumePendingMpmuxUpdateDownload();
        scheduleMpmuxStartupUpdateCheck();

        //% MAIN_ACTIVITY_ON_RESUME
    }
    @Override
    protected void onPause() {
        mIsResumed = false;
        stopMpmuxQrScanner();
        prepareSurfaceSnapshotOverlayForPause();
        super.onPause();
        MakepadNative.activityOnPause();

        //% MAIN_ACTIVITY_ON_PAUSE
    }

    @Override
    protected void onStop() {
        super.onStop();
        MakepadNative.activityOnStop();
    }

    @Override
    protected void onDestroy() {
        unregisterPhysicalKeyboardListener();
        stopMpmuxQrScanner();
        clearMpmuxUpdateDownloadReceiver();
        if (mCameraPreviewOverlay != null) {
            for (Long videoId : mCameraPreviewViews.keySet()) {
                MakepadNative.onCameraPreviewSurfaceDestroyed(videoId);
            }
            mCameraPreviewViews.clear();
            mCameraPreviewOverlay.removeAllViews();
        }
        if (mSelectionHandleOverlay != null) {
            mSelectionHandleOverlay.removeAllViews();
            mSelectionHandleOverlay = null;
            mSelectionHandleStart = null;
            mSelectionHandleEnd = null;
        }
        if (mSurfaceCoverOverlay != null) {
            mRootLayout.removeView(mSurfaceCoverOverlay);
            mSurfaceCoverOverlay = null;
        }
        if (mSurfaceSnapshotBackdrop != null) {
            mSurfaceSnapshotBackdrop.setImageBitmap(null);
            mSurfaceSnapshotBackdrop = null;
        }
        if (mSurfaceSnapshotOverlay != null) {
            mSurfaceSnapshotOverlay.setImageBitmap(null);
            mRootLayout.removeView(mSurfaceSnapshotOverlay);
            mSurfaceSnapshotOverlay = null;
        }
        clearLatestSurfaceSnapshot();
        mSurfaceSnapshotCopyInFlight = false;
        if (mSpeech != null) {
            mSpeech.shutdown();
        }
        cleanupVideoPlaybackState();
        shutdownVideoPlaybackThread();
        if (!mIsSwitchingActivity) {
            cleanupNetworkState();
            shutdownWebSocketsThread();
        }
        super.onDestroy();
        MakepadNative.activityOnDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                trimSurfaceSnapshotCaches();
                break;
            default:
                break;
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        trimSurfaceSnapshotCaches();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && mMpmuxQrScannerActive) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
                stopMpmuxQrScanner();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // Navigation is handled asynchronously by the Makepad UI. The superclass
        // would finish/background this activity before that UI can dismiss an overlay.
        if (mMpmuxQrScannerActive) {
            stopMpmuxQrScanner();
            return;
        }
        MakepadNative.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        MakepadNative.activityOnWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onUserLeaveHint() {
        prepareSurfaceSnapshotOverlayForPause();
        super.onUserLeaveHint();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        persistMpmuxPairingIntent(intent, true);
        restoreSurfaceViewForWarmResumeIfNeeded();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mFileDialogRequests.remove(requestCode)) {
            handleFileDialogResult(requestCode, resultCode, data);
            return;
        }
        //% MAIN_ACTIVITY_ON_ACTIVITY_RESULT
    }

    @Override
    public void onRequestPermissionsResult(int requestId, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestId, permissions, grantResults);
        MpmuxVoiceInputBridge.onRequestPermissionsResult(requestId, permissions, grantResults);

        for (int i = 0; i < permissions.length; i++) {
            int status;
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                status = 1; // Granted
            } else {
                // Permission denied - check if we can ask again
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && shouldShowRequestPermissionRationale(permissions[i])) {
                    status = 2; // DeniedCanRetry (can show rationale and retry)
                } else {
                    status = 3; // DeniedPermanent (user selected "Don't ask again" or hit limit)
                }
            }
            
            // Use the new unified callback
            MakepadNative.onPermissionResult(permissions[i], requestId, status);
        }
    }

    public int checkPermission(String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return 1; // Granted
            } else {
                // Check if permission was previously denied
                if (shouldShowRequestPermissionRationale(permission)) {
                    return 2; // DeniedCanRetry (user previously declined but can show rationale)
                } else {
                    // This could be either:
                    // - NotDetermined (never asked before) 
                    // - DeniedPermanent (user selected "Don't ask again" or hit Android 11+ limit)
                    // We return 0 for NotDetermined as the safest assumption - let the app request and find out
                    return 0; // NotDetermined (assume we can still ask)
                }
            }
        } else {
            // Permissions are granted at install time on older Android versions
            return 1; // Granted
        }
    }

    public void requestPermission(String permission, int requestId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{permission}, requestId);
            } else {
                // Permission already granted
                MakepadNative.onPermissionResult(permission, requestId, 1); // 1 = Granted
            }
        } else {
            // Permissions are granted at install time on older Android versions
            MakepadNative.onPermissionResult(permission, requestId, 1); // 1 = Granted
        }
    }

    // Storage Access Framework picker. Called from Rust on the render thread,
    // from inside the platform-op drain that holds the Cx borrow, so the Intent
    // is built and started on the looper: Activity methods are main-thread only,
    // and startActivityForResult must not run under that borrow.
    //
    // kind: 0 = ACTION_OPEN_DOCUMENT, 1 = ACTION_CREATE_DOCUMENT,
    //       2 = ACTION_OPEN_DOCUMENT_TREE.
    public void openFileDialog(
        final int requestCode,
        final int kind,
        final String mimeType,
        final String[] mimeTypes,
        final boolean allowMultiple,
        final String fileName
    ) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent;
                    if (kind == 2) {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    } else if (kind == 1) {
                        intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(mimeType);
                        if (fileName != null && !fileName.isEmpty()) {
                            intent.putExtra(Intent.EXTRA_TITLE, fileName);
                        }
                    } else {
                        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType(mimeType);
                        if (allowMultiple) {
                            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        }
                    }
                    if (kind != 2 && mimeTypes != null && mimeTypes.length > 0) {
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                    }
                    // Ask for a grant that outlives this process, so a URI handed
                    // to Rust is still openable after the app is killed and
                    // relaunched (see takePersistableUriPermission below).
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    if (kind != 0) {
                        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    }
                    mFileDialogRequests.add(requestCode);
                    startActivityForResult(intent, requestCode);
                } catch (Throwable e) {
                    // No document provider on the device, or the activity is
                    // gone. Cancelling is the honest answer: the user gets no
                    // picker, and Rust must not be left waiting forever.
                    Log.e(LOG_TAG, "openFileDialog failed", e);
                    mFileDialogRequests.remove(requestCode);
                    MakepadNative.onFileDialogResult(requestCode, new String[0]);
                }
            }
        });
    }

    // An empty URI array means cancelled, which is a normal outcome.
    private void handleFileDialogResult(int requestCode, int resultCode, Intent data) {
        ArrayList<String> uris = new ArrayList<>();
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                // Multi-select answers through ClipData; single-select through
                // getData(). A picker set to allow multiple still uses getData()
                // when the user picked exactly one.
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) {
                        takePersistableUriPermission(uri, data.getFlags());
                        uris.add(uri.toString());
                    }
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                takePersistableUriPermission(uri, data.getFlags());
                uris.add(uri.toString());
            }
        }
        MakepadNative.onFileDialogResult(requestCode, uris.toArray(new String[0]));
    }

    private void takePersistableUriPermission(Uri uri, int intentFlags) {
        int grant = intentFlags
            & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (grant == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, grant);
        } catch (Throwable e) {
            // Not every provider offers a persistable grant. The URI is still
            // usable for this run, which is all most callers need.
            Log.w(LOG_TAG, "takePersistableUriPermission failed: " + e);
        }
    }

    @SuppressWarnings("deprecation")
    public void setFullScreen(final boolean fullscreen) {
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    applyFullScreen(fullscreen);
                }
            });
    }

    // Tints the system bar (status/navigation bar) icons and text. A "light"
    // system bar has a light background, so it needs dark icons for contrast;
    // we therefore request dark icons when the app's background is light.
    public void setSystemBarAppearance(final boolean darkIcons) {
        runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSystemBarDarkIcons = darkIcons;
                    applySystemBarAppearance();
                }
            });
    }

    // Applies the currently desired system-bar icon tint (mSystemBarDarkIcons)
    // to the window. Safe to call repeatedly. It is also re-invoked from
    // applyFullScreen(), because the legacy (pre-API-30) fullscreen path
    // rewrites the whole systemUiVisibility bitmask and would otherwise drop
    // the light-status/navigation-bar bits.
    @SuppressWarnings("deprecation")
    private void applySystemBarAppearance() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(mSystemBarDarkIcons ? mask : 0, mask);
            }
        } else {
            View decorView = window.getDecorView();
            int lightBars = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int flags = decorView.getSystemUiVisibility();
            if (mSystemBarDarkIcons) {
                flags |= lightBars;
            } else {
                flags &= ~lightBars;
            }
            decorView.setSystemUiVisibility(flags);
        }
    }

    private boolean canCaptureSurfaceSnapshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false;
        }
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        Surface surface = view.getHolder().getSurface();
        return surface != null && surface.isValid();
    }

    private void refreshSurfaceSnapshotCache() {
        if (!canCaptureSurfaceSnapshot() || mSurfaceSnapshotCopyInFlight) {
            return;
        }
        mSurfaceSnapshotCopyInFlight = true;

        final Bitmap snapshot = Bitmap.createBitmap(
            view.getWidth(),
            view.getHeight(),
            Bitmap.Config.ARGB_8888
        );
        PixelCopy.request(view, snapshot, copyResult -> {
            mSurfaceSnapshotCopyInFlight = false;
            if (copyResult != PixelCopy.SUCCESS) {
                return;
            }
            mLatestSurfaceSnapshot = snapshot;
            mLatestSurfaceSnapshotOrientation = getResources().getConfiguration().orientation;
            cacheWarmResumeSurfaceSnapshot(snapshot);
            updateSurfaceSnapshotBackdrop();
            if (mSurfaceRecoveryOverlayVisible) {
                showSurfaceRecoverySnapshotIfAvailable();
            }
        }, mHandler);
    }

    private void prepareSurfaceSnapshotOverlayForPause() {
        if (mSurfaceRecoveryOverlayVisible && view != null && view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (!hasRecoverySnapshotAvailable()) {
            refreshSurfaceSnapshotCache();
            return;
        }
        mSurfaceRecoveryOverlayVisible = true;
        cacheWarmResumeSurfaceSnapshot(mLatestSurfaceSnapshot);
        updateSurfaceSnapshotBackdrop();
        // Don't hide the SurfaceView or make it invisible. That destroys its surface
        // and causes visual flashing behind any system overlay (like a share sheet).
        showSurfaceRecoverySnapshotIfAvailable();
        refreshSurfaceSnapshotCache();
    }

    private void restoreSurfaceViewForWarmResumeIfNeeded() {
        if (!mSurfaceRecoveryOverlayVisible || view == null) {
            return;
        }

        Surface surface = view.getHolder().getSurface();
        boolean surfaceValid = surface != null && surface.isValid();
        if (view.getVisibility() == View.VISIBLE && surfaceValid) {
            return;
        }

        // Keep the recovery overlay visible, but restore the SurfaceView itself
        // so Android can recreate its surface on same-activity warm resumes.
        view.setVisibility(View.VISIBLE);
        if (!showSurfaceRecoverySnapshotIfAvailable() && mSurfaceCoverOverlay != null) {
            mSurfaceCoverOverlay.setAlpha(1.0f);
            mSurfaceCoverOverlay.setVisibility(View.VISIBLE);
            mSurfaceCoverOverlay.bringToFront();
        }
        updateSurfaceSnapshotBackdrop();
    }

    private Bitmap createTaskDescriptionIconBitmap() {
        int iconResId = getApplicationIconResId();
        if (iconResId == 0) {
            return null;
        }
        Drawable drawable = getDrawable(iconResId);
        if (drawable == null) {
            return null;
        }
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return bitmap;
    }

    @SuppressWarnings("deprecation")
    private void updateTaskDescription() {
        try {
            String label = getApplicationName();
            int iconResId = getApplicationIconResId();
            ActivityManager.TaskDescription taskDescription;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                taskDescription = new ActivityManager.TaskDescription.Builder()
                    .setLabel(label)
                    .setIcon(iconResId)
                    .setPrimaryColor(TASK_DESCRIPTION_BACKGROUND_COLOR)
                    .setBackgroundColor(TASK_DESCRIPTION_BACKGROUND_COLOR)
                    .build();
            }
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                taskDescription = new ActivityManager.TaskDescription(
                    label,
                    iconResId,
                    TASK_DESCRIPTION_BACKGROUND_COLOR
                );
            }
            else {
                taskDescription = new ActivityManager.TaskDescription(
                    label,
                    createTaskDescriptionIconBitmap(),
                    TASK_DESCRIPTION_BACKGROUND_COLOR
                );
            }
            setTaskDescription(taskDescription);
        }
        catch (Throwable throwable) {
            Log.w(LOG_TAG, "Failed to update task description", throwable);
        }
    }

    private void updateSurfaceSnapshotBackdrop() {
        if (mSurfaceSnapshotBackdrop == null) {
            return;
        }

        if (hasCurrentOrientationRecoverySnapshot()) {
            mSurfaceSnapshotBackdrop.setImageBitmap(mLatestSurfaceSnapshot);
            mSurfaceSnapshotBackdrop.setVisibility(View.VISIBLE);
            return;
        }

        mSurfaceSnapshotBackdrop.setImageBitmap(null);
        mSurfaceSnapshotBackdrop.setVisibility(View.GONE);
    }

    private boolean showSurfaceRecoverySnapshotIfAvailable() {
        if (mSurfaceSnapshotOverlay == null || mSurfaceCoverOverlay == null) {
            return false;
        }

        if (hasCurrentOrientationRecoverySnapshot()) {
            mSurfaceSnapshotOverlay.setImageBitmap(mLatestSurfaceSnapshot);
            mSurfaceSnapshotOverlay.setAlpha(1.0f);
            mSurfaceSnapshotOverlay.setVisibility(View.VISIBLE);
            mSurfaceSnapshotOverlay.bringToFront();
            mSurfaceCoverOverlay.setVisibility(View.GONE);
            mSurfaceCoverOverlay.setAlpha(1.0f);
            return true;
        }

        mSurfaceSnapshotOverlay.setImageBitmap(null);
        mSurfaceSnapshotOverlay.setVisibility(View.GONE);
        return false;
    }

    private void applySurfaceCoverVisibility(boolean visible) {
        if (mSurfaceCoverOverlay == null || mSurfaceSnapshotOverlay == null) {
            return;
        }

        boolean wasRecoveryOverlayVisible = mSurfaceRecoveryOverlayVisible;
        if (visible && !hasRecoverySnapshotAvailable()) {
            mSurfaceRecoveryOverlayVisible = true;
            if (view != null) {
                view.setVisibility(View.INVISIBLE);
            }
            mSurfaceCoverOverlay.animate().cancel();
            mSurfaceSnapshotOverlay.animate().cancel();
            mSurfaceSnapshotOverlay.setImageBitmap(null);
            mSurfaceSnapshotOverlay.setVisibility(View.GONE);
            mSurfaceSnapshotOverlay.setAlpha(1.0f);
            mSurfaceCoverOverlay.setAlpha(1.0f);
            mSurfaceCoverOverlay.setVisibility(View.VISIBLE);
            mSurfaceCoverOverlay.bringToFront();
            if (!wasRecoveryOverlayVisible) {
                refreshSurfaceSnapshotCache();
            }
            return;
        }
        mSurfaceRecoveryOverlayVisible = visible;
        if (view != null) {
            view.setVisibility(visible ? View.INVISIBLE : view.getVisibility());
        }
        if (visible && !wasRecoveryOverlayVisible) {
            refreshSurfaceSnapshotCache();
        }

        mSurfaceCoverOverlay.animate().cancel();
        mSurfaceSnapshotOverlay.animate().cancel();
        if (visible) {
            if (showSurfaceRecoverySnapshotIfAvailable()) {
                return;
            }
            mSurfaceCoverOverlay.setAlpha(1.0f);
            mSurfaceCoverOverlay.setVisibility(View.VISIBLE);
            mSurfaceCoverOverlay.bringToFront();
            return;
        }

        if (mSurfaceCoverOverlay.getVisibility() != View.VISIBLE
            && mSurfaceSnapshotOverlay.getVisibility() != View.VISIBLE) {
            mSurfaceCoverOverlay.setAlpha(1.0f);
            mSurfaceSnapshotOverlay.setAlpha(1.0f);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
            clearWarmResumeSurfaceSnapshot();
            return;
        }

        final FrameLayout surfaceCoverOverlay = mSurfaceCoverOverlay;
        final ImageView surfaceSnapshotOverlay = mSurfaceSnapshotOverlay;
        if (surfaceSnapshotOverlay.getVisibility() == View.VISIBLE) {
            surfaceCoverOverlay.setVisibility(View.GONE);
            surfaceCoverOverlay.setAlpha(1.0f);
            surfaceSnapshotOverlay.animate()
                .alpha(0.0f)
                .setDuration(SURFACE_COVER_FADE_OUT_MS)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        if (mSurfaceCoverOverlay != surfaceCoverOverlay
                            || mSurfaceSnapshotOverlay != surfaceSnapshotOverlay) {
                            return;
                        }
                        surfaceSnapshotOverlay.setVisibility(View.GONE);
                        surfaceSnapshotOverlay.setAlpha(1.0f);
                        if (view != null) {
                            view.setVisibility(View.VISIBLE);
                        }
                        clearWarmResumeSurfaceSnapshot();
                    }
                });
            return;
        }

        surfaceCoverOverlay.animate()
            .alpha(0.0f)
            .setDuration(SURFACE_COVER_FADE_OUT_MS)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (mSurfaceCoverOverlay != surfaceCoverOverlay) {
                        return;
                    }
                    surfaceCoverOverlay.setVisibility(View.GONE);
                    surfaceCoverOverlay.setAlpha(1.0f);
                    if (view != null) {
                        view.setVisibility(View.VISIBLE);
                    }
                    clearWarmResumeSurfaceSnapshot();
                }
            });
    }

    public void setSurfaceCoverVisible(final boolean visible) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            applySurfaceCoverVisibility(visible);
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                applySurfaceCoverVisibility(visible);
            }
        });
    }

    public void requestSurfaceSnapshotRefresh() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshSurfaceSnapshotCache();
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshSurfaceSnapshotCache();
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void applyFullScreen(boolean fullscreen) {
        View decorView = getWindow().getDecorView();

        if (fullscreen) {
            // WindowManager.LayoutParams.layoutInDisplayCutoutMode is API 28+
            // (display cutouts didn't exist before Android 9). Touching the
            // field at all on API 26-27 throws NoSuchFieldError, so guard it.
            // LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3 is API 30+; on 28-29 we
            // fall back to SHORT_EDGES.
            if (Build.VERSION.SDK_INT >= 28) {
                getWindow().getAttributes().layoutInDisplayCutoutMode =
                    Build.VERSION.SDK_INT >= 30 ? 3 : LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            if (Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(false);
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_GESTURE = 2
                    controller.setSystemBarsBehavior(2);
                }
            } else {
                int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                decorView.setSystemUiVisibility(uiOptions);
            }
        }
        else {
            if (Build.VERSION.SDK_INT >= 30) {
                getWindow().setDecorFitsSystemWindows(true);
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                }
            } else {
                decorView.setSystemUiVisibility(0);
            }
        }

        // The legacy (pre-API-30) branches above replace the entire
        // systemUiVisibility bitmask, so re-assert the system-bar icon tint
        // on top of the new flags. On API 30+ this is an independent,
        // idempotent re-apply.
        applySystemBarAppearance();

        // Force a layout pass so the SurfaceView gets the new dimensions
        if (view != null) {
            view.requestLayout();
        }
    }
    
    public void switchActivityClass(Class c){
        mIsSwitchingActivity = true;
        Intent intent = new Intent(getApplicationContext(), c);
        Intent currentIntent = getIntent();
        if (currentIntent != null && currentIntent.getExtras() != null) {
            intent.putExtras(currentIntent.getExtras());
        }
        startActivity(intent);
        finish();
    }

    private void cleanupVideoPlaybackState() {
        if (mVideoPlayerRunnables != null) {
            ArrayList<Long> videoIds = new ArrayList<>(mVideoPlayerRunnables.keySet());
            for (Long videoId : videoIds) {
                cleanupVideoPlaybackResources(videoId);
            }
            mVideoPlayerRunnables.clear();
        }
        if (mVideoPlaybackHandler != null) {
            mVideoPlaybackHandler.removeCallbacksAndMessages(null);
        }
    }

    private void shutdownVideoPlaybackThread() {
        if (mVideoPlaybackThread == null) {
            mVideoPlaybackHandler = null;
            return;
        }
        mVideoPlaybackThread.quitSafely();
        try {
            mVideoPlaybackThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mVideoPlaybackThread = null;
        mVideoPlaybackHandler = null;
    }

    private static void cleanupNetworkState() {
        for (MakepadWebSocket socket : new ArrayList<>(mActiveWebsockets.values())) {
            if (socket != null) {
                socket.closeSocketAndClearCallback();
            }
        }
        mActiveWebsockets.clear();
        mActiveWebsocketsReaders.clear();

        for (MakepadSocketStream socket : new ArrayList<>(mActiveSocketStreams.values())) {
            if (socket != null) {
                socket.close();
            }
        }
        mActiveSocketStreams.clear();

        if (mWebSocketsHandler != null) {
            mWebSocketsHandler.removeCallbacksAndMessages(null);
        }
    }

    private static void shutdownWebSocketsThread() {
        if (mWebSocketsThread == null) {
            mWebSocketsHandler = null;
            return;
        }
        mWebSocketsThread.quitSafely();
        try {
            mWebSocketsThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mWebSocketsThread = null;
        mWebSocketsHandler = null;
    }
    
    // Configure keyboard settings before showing - called from Rust
    public void configureKeyboard(final int keyboardType, final int autocapitalize,
                                   final int autocorrect, final int returnKeyType,
                                   final boolean isMultiline, final boolean isSecure) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (view != null) {
                    view.configureKeyboard(keyboardType, autocapitalize, autocorrect,
                                          returnKeyType, isMultiline, isSecure);
                }
            }
        });
    }

    public void showKeyboard(final boolean show) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (show) {
                    if (view == null || view.getInputMode() == MakepadSurface.INPUT_MODE_NONE) {
                        return;
                    }
                    // The IME only shows for the view that currently holds
                    // focus and is "served" by the InputMethodManager. The
                    // SurfaceView can end up not focused (window-focus churn,
                    // surface re-creation, returning from another activity,
                    // etc.); after that, showSoftInput() is silently ignored —
                    // logcat shows "Ignoring showSoftInput() as view=... is
                    // not served". Re-focus the SurfaceView before every show
                    // so it becomes the served editor. This is the canonical
                    // precondition for showSoftInput(); the previous code
                    // relied on the view simply staying focused from the
                    // one-time requestFocus() in the MakepadSurface
                    // constructor, which is not guaranteed.
                    view.requestFocus();
                    InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(view, 0);
                } else {
                    // Hiding the IME via the legacy InputMethodManager
                    // .hideSoftInputFromWindow() is unreliable on modern Android:
                    // with an edge-to-edge window (targetSdk 35) and on
                    // OEM-customized builds (e.g. OxygenOS / OnePlus) the request
                    // is silently dropped and the keyboard stays up. The
                    // WindowInsetsController.hide(ime()) path is the canonical
                    // API 30+ way, and matches how this app already drives the
                    // system bars and reads IME insets.
                    if (Build.VERSION.SDK_INT >= 30) {
                        android.view.WindowInsetsController controller = getWindow().getInsetsController();
                        if (controller != null) {
                            controller.hide(WindowInsets.Type.ime());
                        }
                    } else {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null && view != null) {
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                    }
                }
            }
        });
    }

    // Update IME text state for programmatic changes - called from Rust
    // Note: This should only be called for programmatic text changes (e.g., clear button),
    // NOT during normal IME input (which flows Java to Rust via onImeTextStateChanged)
    public void updateImeTextState(final String fullText, final int selStart, final int selEnd,
                                   final int composingStart, final int composingEnd) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (view != null) {
                    view.updateImeTextState(
                        fullText,
                        selStart,
                        selEnd,
                        composingStart,
                        composingEnd
                    );
                }
            }
        });
    }

    public void copyToClipboard(String content) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // User-facing description of the clipboard content
        String clipLabel = getApplicationName() + " clip";
        ClipData clip = ClipData.newPlainText(clipLabel, content);
        clipboard.setPrimaryClip(clip);
    }

    public String pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                ClipData.Item item = clipData.getItemAt(0);
                CharSequence text = item.coerceToText(this);
                if (text != null) {
                    return text.toString();
                }
            }
        }
        return "";
    }

    // `cx.haptic_feedback`: 0 click, 1 virtual key, 2 tick (the Pixel
    // Launcher's EFFECT_CLICK, VIRTUAL_KEY and low tick call sites).
    public void performHaptic(final int kind) {
        runOnUiThread(new Runnable() {
            public void run() {
                int constant = kind == 1 ? android.view.HapticFeedbackConstants.VIRTUAL_KEY
                    : kind == 2 ? android.view.HapticFeedbackConstants.CLOCK_TICK
                    : android.view.HapticFeedbackConstants.CONTEXT_CLICK;
                getWindow().getDecorView().performHapticFeedback(constant);
            }
        });
    }

    // `cx.open_url`: hand the URL to whatever the system opens it with.
    // Hosted child processes of a window manager ask the WM for this.
    public void openUrl(final String url) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e("Makepad", "openUrl failed for " + url + ": " + e);
                }
            }
        });
    }

    // Copy a picked `content://` document into `dest` (a plain file the
    // app owns), so a process without a JVM can open it. Returns false
    // when the document cannot be read.
    public boolean copyContentUri(String uri, String dest) {
        try (java.io.InputStream in = getContentResolver().openInputStream(Uri.parse(uri));
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
            if (in == null) {
                return false;
            }
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return true;
        } catch (Exception e) {
            Log.e("Makepad", "copyContentUri failed for " + uri + ": " + e);
            return false;
        }
    }

    private String getApplicationName() {
        ApplicationInfo applicationInfo = getApplicationContext().getApplicationInfo();
        CharSequence appName = applicationInfo.loadLabel(getPackageManager());
        return appName.toString();
    }

    private int getApplicationIconResId() {
        ApplicationInfo applicationInfo = getApplicationContext().getApplicationInfo();
        return applicationInfo.icon;
    }

    public void showClipboardActions(final boolean hasSelection, final int left, final int top, final int right, final int bottom, final int keyboardShift) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mHasSelection = hasSelection;
                mSelectionBounds[0] = left;
                mSelectionBounds[1] = top;
                mSelectionBounds[2] = right;
                mSelectionBounds[3] = bottom;
                mKeyboardShift = keyboardShift;

                // If ActionMode is already showing, finish it first
                if (mActionMode != null) {
                    mActionMode.finish();
                }

                // Start ActionMode with our callback
                // Use TYPE_FLOATING (API 23+) to show near finger, falls back to primary for older versions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mActionMode = startActionMode(new ActionMode.Callback2() {
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            return onCreateActionModeInternal(mode, menu);
                        }

                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return onPrepareActionModeInternal(mode, menu);
                        }

                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            return onActionItemClickedInternal(mode, item);
                        }

                        @Override
                        public void onDestroyActionMode(ActionMode mode) {
                            onDestroyActionModeInternal(mode);
                        }

                        @Override
                        public void onGetContentRect(ActionMode mode, View view, android.graphics.Rect outRect) {
                            // The content rect tells Android what area to AVOID covering (not where to position)
                            // Android's FloatingToolbar will automatically position itself above or below this rect
                            // based on available screen space

                            // Use asymmetric padding: more above (for better spacing when popup appears above),
                            // less below (already looks good), and some on sides for visual balance
                            int topPadding = 16;      // More padding above pushes popup higher
                            int bottomPadding = 2;    // Minimal padding below (already good spacing)
                            int sidePadding = 2;      // Horizontal padding for visual balance

                            int left = mSelectionBounds[0] - sidePadding;
                            int top = mSelectionBounds[1] - topPadding;
                            int right = mSelectionBounds[2] + sidePadding;
                            int bottom = mSelectionBounds[3] + bottomPadding;

                            outRect.set(left, top, right, bottom);
                        }
                    }, ActionMode.TYPE_FLOATING);
                } else {
                    mActionMode = startActionMode(new ActionMode.Callback() {
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            return onCreateActionModeInternal(mode, menu);
                        }

                        @Override
                        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                            return onPrepareActionModeInternal(mode, menu);
                        }

                        @Override
                        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                            return onActionItemClickedInternal(mode, item);
                        }

                        @Override
                        public void onDestroyActionMode(ActionMode mode) {
                            onDestroyActionModeInternal(mode);
                        }
                    });
                }
            }
        });
    }

    public void dismissClipboardActions() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }
            }
        });
    }

    // Helper methods for ActionMode callbacks (shared between Callback and Callback2)
    private boolean onCreateActionModeInternal(ActionMode mode, Menu menu) {
        // Add menu items: Copy, Cut, Paste, Select All
        menu.add(0, android.R.id.copy, 0, android.R.string.copy);
        menu.add(0, android.R.id.cut, 0, android.R.string.cut);
        menu.add(0, android.R.id.paste, 0, android.R.string.paste);
        menu.add(0, android.R.id.selectAll, 0, android.R.string.selectAll);
        return true;
    }

    private boolean onPrepareActionModeInternal(ActionMode mode, Menu menu) {
        boolean hasSelection = mHasSelection;
        boolean hasClipboard = false;

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            hasClipboard = true;
        }

        MenuItem copyItem = menu.findItem(android.R.id.copy);
        MenuItem cutItem = menu.findItem(android.R.id.cut);
        MenuItem pasteItem = menu.findItem(android.R.id.paste);

        if (copyItem != null) copyItem.setVisible(hasSelection);
        if (cutItem != null) cutItem.setVisible(hasSelection);
        if (pasteItem != null) pasteItem.setVisible(hasClipboard);

        return true;
    }

    private boolean onActionItemClickedInternal(ActionMode mode, MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.copy) {
            MakepadNative.onClipboardAction("copy");
            mode.finish();
            return true;
        } else if (id == android.R.id.cut) {
            MakepadNative.onClipboardAction("cut");
            mode.finish();
            return true;
        } else if (id == android.R.id.paste) {
            String content = pasteFromClipboard();
            MakepadNative.onClipboardPaste(content);
            mode.finish();
            return true;
        } else if (id == android.R.id.selectAll) {
            MakepadNative.onClipboardAction("select_all");
            // Sync Java-side selection with Rust so backspace/delete will work
            // This updates mEditable's selection and notifies the IME
            view.selectAllInEditable();
            mode.finish();
            return true;
        }
        return false;
    }

    private void onDestroyActionModeInternal(ActionMode mode) {
        mActionMode = null;
        mHasSelection = false;
    }

    private View.OnTouchListener createSelectionHandleDragListener(final int handleKind) {
        return new View.OnTouchListener() {
            private final int[] rootLocation = new int[2];

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mRootLayout == null) {
                    return false;
                }
                mRootLayout.getLocationOnScreen(rootLocation);
                float absX = event.getRawX() - rootLocation[0];
                float absY = event.getRawY() - rootLocation[1];
                int action = event.getActionMasked();

                if (action == MotionEvent.ACTION_DOWN) {
                    setSelectionHandlePosition(v, absX, absY);
                    MakepadNative.onSelectionHandleDrag(handleKind, SELECTION_DRAG_BEGIN, absX, absY, event.getEventTime());
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    setSelectionHandlePosition(v, absX, absY);
                    MakepadNative.onSelectionHandleDrag(handleKind, SELECTION_DRAG_MOVE, absX, absY, event.getEventTime());
                    return true;
                }
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    setSelectionHandlePosition(v, absX, absY);
                    MakepadNative.onSelectionHandleDrag(handleKind, SELECTION_DRAG_END, absX, absY, event.getEventTime());
                    return true;
                }
                return false;
            }
        };
    }

    private void setSelectionHandlePosition(View handle, float x, float y) {
        if (handle == null) {
            return;
        }
        handle.setX(x - (mSelectionHandleSizePx * 0.5f));
        handle.setY(y - (mSelectionHandleSizePx * 0.5f));
    }

    public void showSelectionHandles(final float startX, final float startY, final float endX, final float endY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSelectionHandleOverlay == null || mSelectionHandleStart == null || mSelectionHandleEnd == null) {
                    return;
                }
                mSelectionHandleOverlay.setVisibility(View.VISIBLE);
                setSelectionHandlePosition(mSelectionHandleStart, startX, startY);
                setSelectionHandlePosition(mSelectionHandleEnd, endX, endY);
                mSelectionHandleStart.setVisibility(View.VISIBLE);
                mSelectionHandleEnd.setVisibility(View.VISIBLE);
                mSelectionHandleOverlay.bringToFront();
            }
        });
    }

    public void updateSelectionHandles(final float startX, final float startY, final float endX, final float endY) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSelectionHandleOverlay == null || mSelectionHandleStart == null || mSelectionHandleEnd == null) {
                    return;
                }
                mSelectionHandleOverlay.setVisibility(View.VISIBLE);
                setSelectionHandlePosition(mSelectionHandleStart, startX, startY);
                setSelectionHandlePosition(mSelectionHandleEnd, endX, endY);
                mSelectionHandleStart.setVisibility(View.VISIBLE);
                mSelectionHandleEnd.setVisibility(View.VISIBLE);
            }
        });
    }

    public void hideSelectionHandles() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mSelectionHandleOverlay == null || mSelectionHandleStart == null || mSelectionHandleEnd == null) {
                    return;
                }
                mSelectionHandleStart.setVisibility(View.GONE);
                mSelectionHandleEnd.setVisibility(View.GONE);
                mSelectionHandleOverlay.setVisibility(View.GONE);
            }
        });
    }

    public void requestHttp(long id, long metadataId, String url, String method, String headers, byte[] body) {
        try {
            MakepadNetwork network = new MakepadNetwork();

            CompletableFuture<HttpResponse> future = network.performHttpRequest(url, method, headers, body);

            future.thenAccept(response -> {
                runOnUiThread(() -> MakepadNative.onHttpResponse(id, metadataId, response.getStatusCode(), response.getHeaders(), response.getBody()));
            }).exceptionally(ex -> {
                runOnUiThread(() -> MakepadNative.onHttpRequestError(id, metadataId, ex.toString()));
                return null;
            });
        } catch (Exception e) {
            MakepadNative.onHttpRequestError(id, metadataId, e.toString());
        }
    }

    public void openWebSocket(long id, String url, long callback) {
        MakepadWebSocket webSocket = new MakepadWebSocket(id, url, callback);
        mActiveWebsockets.put(id, webSocket);
        webSocket.connect();

        if (webSocket.isConnected()) {
            MakepadWebSocketReader reader = new MakepadWebSocketReader(this, webSocket);
            mWebSocketsHandler.post(reader);
            mActiveWebsocketsReaders.put(id, reader);
        } else {
            Log.e("Makepad", "openWebSocket failed id=" + id + " url=" + url);
        }
    }

    public void sendWebSocketMessage(long id, byte[] message) {
      
        MakepadWebSocket webSocket = mActiveWebsockets.get(id);
        if (webSocket != null) {
            webSocket.sendMessage(message);
        }
    }

    public void closeWebSocket(long id) {
        
        MakepadWebSocket socket = mActiveWebsockets.get(id);
        if (socket != null) {
            socket.closeSocketAndClearCallback();
        }
        MakepadWebSocketReader reader = mActiveWebsocketsReaders.get(id);
        if (reader != null) {
            mWebSocketsHandler.removeCallbacks(reader);
        }
        
        mActiveWebsocketsReaders.remove(id);
        mActiveWebsockets.remove(id);
    }

    public boolean openSocketStream(long id, String host, int port, boolean useTls, boolean ignoreSslCert) {
        MakepadSocketStream socket = new MakepadSocketStream();
        if (!socket.connect(host, port, useTls, ignoreSslCert)) {
            return false;
        }
        mActiveSocketStreams.put(id, socket);
        return true;
    }

    public byte[] socketStreamRead(long id, int maxBytes) {
        MakepadSocketStream socket = mActiveSocketStreams.get(id);
        if (socket == null) {
            return null;
        }
        return socket.read(maxBytes);
    }

    public int socketStreamWrite(long id, byte[] message) {
        MakepadSocketStream socket = mActiveSocketStreams.get(id);
        if (socket == null) {
            return -1;
        }
        return socket.write(message);
    }

    public void socketStreamSetReadTimeout(long id, int timeoutMs) {
        MakepadSocketStream socket = mActiveSocketStreams.get(id);
        if (socket != null) {
            socket.setReadTimeout(timeoutMs);
        }
    }

    public void socketStreamSetWriteTimeout(long id, int timeoutMs) {
        MakepadSocketStream socket = mActiveSocketStreams.get(id);
        if (socket != null) {
            socket.setWriteTimeout(timeoutMs);
        }
    }

    public void closeSocketStream(long id) {
        MakepadSocketStream socket = mActiveSocketStreams.get(id);
        if (socket != null) {
            socket.close();
            mActiveSocketStreams.remove(id);
        }
    }

    public void webSocketConnectionDone(long id, long callback) {
        mActiveWebsockets.remove(id);
        MakepadNative.onWebSocketClosed(callback);
    }

    public String[] getAudioDevices(long flag){
        try{

            AudioManager am = (AudioManager)this.getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo[] devices = null;
            ArrayList<String> out = new ArrayList<String>();
            if(flag == 0){
                devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
            }
            else{
                devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            }
            for(AudioDeviceInfo device: devices){
                int[] channel_counts = device.getChannelCounts();
                for(int cc: channel_counts){
                    out.add(String.format(
                        "%d$$%d$$%d$$%s",
                        device.getId(),
                        device.getType(),
                        cc,
                        device.getProductName().toString()
                    ));
                }
            }
            return out.toArray(new String[0]);
        }
        catch(Exception e){
            Log.e("Makepad", "exception: " + e.getMessage());
            Log.e("Makepad", "exception: " + e.toString());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    public void openAllMidiDevices(long delay){
        Runnable runnable = () -> {
            try{
                BluetoothManager bm = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ba = bm.getAdapter();
                Set<BluetoothDevice> bluetooth_devices = ba.getBondedDevices();
                ArrayList<String> bt_names = new ArrayList<String>();
                MidiManager mm = (MidiManager)this.getSystemService(Context.MIDI_SERVICE);
                for(BluetoothDevice device: bluetooth_devices){
                    if(device.getType() == BluetoothDevice.DEVICE_TYPE_LE){
                        String name =device.getName();
                        bt_names.add(name);
                        mm.openBluetoothDevice(device, this, mHandler);
                    }
                }
                // this appears to give you nonworking BLE midi devices. So we skip those by name (not perfect but ok)
                for (MidiDeviceInfo info : mm.getDevices()){
                    String name = info.getProperties().getCharSequence(MidiDeviceInfo.PROPERTY_NAME).toString();
                    boolean found = false;
                    for (String bt_name : bt_names){
                        if (bt_name.equals(name)){
                            found = true;
                            break;
                        }
                    }
                    if(!found){
                        mm.openDevice(info, this, mHandler);
                    }
                }
            }
            catch(Exception e){
                Log.e("Makepad", "exception: " + e.getMessage());
                Log.e("Makepad", "exception: " + e.toString());
            }
        };
        if(delay != 0){
            mHandler.postDelayed(runnable, delay);
        }
        else{ // run now
            runnable.run();
        }
    }

    public void onDeviceOpened(MidiDevice device) {
        if(device == null){
            return;
        }
        MidiDeviceInfo info = device.getInfo();
        if(info != null){
            String name = info.getProperties().getCharSequence(MidiDeviceInfo.PROPERTY_NAME).toString();
            MakepadNative.onMidiDeviceOpened(name, device);
        }
    }

    // location (Cx::start_location_updates)
    private LocationManager mLocationManager;
    private LocationListener mLocationListener;

    private void sendLocationUpdate(Location loc) {
        MakepadNative.onLocationUpdate(
            loc.getLongitude(), loc.getLatitude(), loc.getAccuracy(),
            loc.hasAltitude(), loc.getAltitude(),
            loc.hasSpeed(), loc.getSpeed(),
            loc.hasBearing(), loc.getBearing(),
            loc.getTime());
    }

    public void startLocationUpdates(final long minIntervalMs, final float minDistanceM) {
        runOnUiThread(() -> {
            if (mLocationListener != null) {
                return; // already running
            }
            try {
                mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                if (mLocationManager == null) {
                    MakepadNative.onLocationError(2, "no location service");
                    return;
                }
                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(Location loc) {
                        sendLocationUpdate(loc);
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                };
                boolean any = false;
                if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM,
                        listener, Looper.getMainLooper());
                    any = true;
                }
                if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, minIntervalMs, minDistanceM,
                        listener, Looper.getMainLooper());
                    any = true;
                }
                if (!any) {
                    MakepadNative.onLocationError(2, "location providers disabled");
                    return;
                }
                mLocationListener = listener;
                // seed with the last known fix so the app has a position immediately
                Location last = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (last == null) {
                    last = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                }
                if (last != null) {
                    sendLocationUpdate(last);
                }
            }
            catch (SecurityException e) {
                mLocationListener = null;
                MakepadNative.onLocationError(1, "location permission missing");
            }
            catch (Exception e) {
                mLocationListener = null;
                MakepadNative.onLocationError(2, e.toString());
            }
        });
    }

    public void stopLocationUpdates() {
        runOnUiThread(() -> {
            try {
                if (mLocationManager != null && mLocationListener != null) {
                    mLocationManager.removeUpdates(mLocationListener);
                }
            }
            catch (Exception e) {
                Log.e("Makepad", "stopLocationUpdates: " + e.toString());
            }
            mLocationListener = null;
        });
    }

    // The OS speech engines (makepad-system-speech). Everything real lives in
    // MakepadSpeech; these are just the names the JNI side resolves on the
    // activity, because a natively attached thread's class loader cannot find
    // app classes with FindClass.
    private MakepadSpeech mSpeech;

    private synchronized MakepadSpeech speech() {
        if (mSpeech == null) {
            mSpeech = new MakepadSpeech(this);
        }
        return mSpeech;
    }

    public boolean speechSttAvailable() {
        return speech().sttAvailable();
    }

    public void speechSttStart(long session, String languageTag, boolean partial, boolean preferOffline) {
        speech().sttStart(session, languageTag, partial, preferOffline);
    }

    public void speechSttStop(long session) {
        speech().sttStop(session);
    }

    public boolean speechTtsAvailable() {
        return speech().ttsAvailable();
    }

    public String[] speechTtsVoices() {
        return speech().ttsVoices();
    }

    public byte[] speechTtsSynthesize(String text, String voiceName, String languageTag, float rate, float pitch) {
        return speech().ttsSynthesize(text, voiceName, languageTag, rate, pitch);
    }

    public String speechTtsLastError() {
        return speech().ttsLastError();
    }

    public void attachCameraNativePreview(final long videoId, final int left, final int top, final int right, final int bottom) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mCameraPreviewOverlay == null) {
                    return;
                }
                CameraPreviewSurface preview = mCameraPreviewViews.get(videoId);
                if (preview == null) {
                    preview = new CameraPreviewSurface(MakepadActivity.this, videoId);
                    mCameraPreviewViews.put(videoId, preview);
                    mCameraPreviewOverlay.addView(preview);
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(1, right - left),
                    Math.max(1, bottom - top)
                );
                lp.leftMargin = left;
                lp.topMargin = top;
                preview.setLayoutParams(lp);
                preview.setVisibility(View.VISIBLE);
            }
        });
    }

    public void updateCameraNativePreview(final long videoId, final int left, final int top, final int right, final int bottom, final boolean visible) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CameraPreviewSurface preview = mCameraPreviewViews.get(videoId);
                if (preview == null) {
                    if (visible) {
                        attachCameraNativePreview(videoId, left, top, right, bottom);
                    }
                    return;
                }
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    Math.max(1, right - left),
                    Math.max(1, bottom - top)
                );
                lp.leftMargin = left;
                lp.topMargin = top;
                preview.setLayoutParams(lp);
                preview.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }

    public void detachCameraNativePreview(final long videoId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CameraPreviewSurface preview = mCameraPreviewViews.remove(videoId);
                if (preview != null && mCameraPreviewOverlay != null) {
                    mCameraPreviewOverlay.removeView(preview);
                }
            }
        });
    }

    private static boolean codecLooksSoftware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) {
            if (info.isSoftwareOnly()) return true;
            if (info.isHardwareAccelerated()) return false;
        }
        String name = info.getName().toLowerCase();
        return name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains("sw");
    }

    private static boolean codecLooksHardware(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= 29) {
            if (info.isHardwareAccelerated()) return true;
            if (info.isSoftwareOnly()) return false;
        }
        return !codecLooksSoftware(info);
    }

    public int[] queryH264CodecSupport() {
        boolean encHw = false;
        boolean encSw = false;
        boolean decHw = false;
        boolean decSw = false;
        int maxWidth = 0;
        int maxHeight = 0;
        int maxFps = 0;
        int maxBitrate = 0;
        int widthAlign = 2;
        int heightAlign = 2;

        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                String[] types = info.getSupportedTypes();
                boolean supportsAvc = false;
                for (String t : types) {
                    if ("video/avc".equalsIgnoreCase(t)) {
                        supportsAvc = true;
                        break;
                    }
                }
                if (!supportsAvc) {
                    continue;
                }

                boolean hw = codecLooksHardware(info);
                boolean sw = codecLooksSoftware(info);

                boolean probeOk = false;
                MediaCodec codec = null;
                try {
                    codec = MediaCodec.createByCodecName(info.getName());
                    probeOk = codec != null;
                } catch (Throwable ignored) {
                    probeOk = false;
                } finally {
                    if (codec != null) {
                        try { codec.release(); } catch (Throwable ignored) {}
                    }
                }
                if (!probeOk) {
                    continue;
                }

                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType("video/avc");
                    if (caps != null && caps.getVideoCapabilities() != null) {
                        MediaCodecInfo.VideoCapabilities vc = caps.getVideoCapabilities();
                        maxWidth = Math.max(maxWidth, vc.getSupportedWidths().getUpper().intValue());
                        maxHeight = Math.max(maxHeight, vc.getSupportedHeights().getUpper().intValue());
                        maxBitrate = Math.max(maxBitrate, vc.getBitrateRange().getUpper().intValue());
                        maxFps = Math.max(maxFps, vc.getSupportedFrameRates().getUpper().intValue());
                        widthAlign = Math.max(widthAlign, vc.getWidthAlignment());
                        heightAlign = Math.max(heightAlign, vc.getHeightAlignment());
                    }
                } catch (Throwable ignored) {}

                if (info.isEncoder()) {
                    if (hw) encHw = true;
                    if (sw) encSw = true;
                } else {
                    if (hw) decHw = true;
                    if (sw) decSw = true;
                }
            }
        } catch (Throwable ignored) {}

        return new int[] {
            encHw ? 1 : 0,
            encSw ? 1 : 0,
            decHw ? 1 : 0,
            decSw ? 1 : 0,
            maxWidth,
            maxHeight,
            maxFps,
            maxBitrate,
            widthAlign,
            heightAlign,
        };
    }

    public void prepareVideoPlayback(long videoId, Object source, int externalTextureHandle, boolean autoplay, boolean shouldLoop) {
        VideoPlayer VideoPlayer = new VideoPlayer(this, videoId);
        VideoPlayer.setSource(source);
        VideoPlayer.setExternalTextureHandle(externalTextureHandle);
        VideoPlayer.setAutoplay(autoplay);
        VideoPlayer.setShouldLoop(shouldLoop);
        VideoPlayerRunnable runnable = new VideoPlayerRunnable(VideoPlayer);

        mVideoPlayerRunnables.put(videoId, runnable);
        mVideoPlaybackHandler.post(runnable);
    }

    public void beginVideoPlayback(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.beginPlayback();
        }
    }

    public void pauseVideoPlayback(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.pausePlayback();
        }
    }

    public void resumeVideoPlayback(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.resumePlayback();
        }
    }

    public void muteVideoPlayback(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.mute();
        }
    }

    public void unmuteVideoPlayback(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.unmute();
        }
    }

    public void setVideoPlaybackRate(long videoId, double rate) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.setPlaybackRate(rate);
        }
    }

    public void seekVideoPlayback(long videoId, long positionMs) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            runnable.seekToPosition(positionMs);
        }
    }

    public long getVideoPlaybackPosition(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.get(videoId);
        if(runnable != null) {
            return runnable.getCurrentPositionMs();
        }
        return 0;
    }

    public void cleanupVideoPlaybackResources(long videoId) {
        VideoPlayerRunnable runnable = mVideoPlayerRunnables.remove(videoId);
        if(runnable != null) {
            runnable.cleanupVideoPlaybackResources();
            runnable = null;
        }
        detachCameraNativePreview(videoId);
    }
    
                
    public boolean isEmulator() {
        // hints that the app is running on emulator
        return Build.MODEL.startsWith("sdk")
            || "google_sdk".equals(Build.MODEL)
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK")
            || Build.MODEL.toLowerCase().contains("droid4x")
            || Build.FINGERPRINT.startsWith("generic")
            || Build.PRODUCT == "sdk"
            || Build.PRODUCT == "google_sdk"
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"));
    }
    
    

    private void persistMpmuxPairingIntent(Intent intent, boolean notifyNative) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        String data = intent.getDataString();
        if (handleMpmuxUpdateUrl(data)) {
            return;
        }
        persistMpmuxPairingUrl(data, notifyNative);
    }

    private void persistMpmuxPairingUrl(String data, boolean notifyNative) {
        if (!isMpmuxPairingQrValue(data)) {
            return;
        }
        try {
            writeMpmuxPairingUrl(new File(getFilesDir(), "mpmux-android-sessiond-pairing-url.txt"), data);
            writeMpmuxPairingUrl(new File(getCacheDir(), "mpmux-android-sessiond-pairing-url.txt"), data);
            if (notifyNative) {
                notifyMpmuxPairingSubmitted();
                MakepadNative.onAndroidIntentUrl(data);
            }
        } catch (IOException err) {
            Log.w("Makepad", "failed to persist mpmux pairing intent", err);
        }
    }

    private void writeMpmuxPairingUrl(File file, String data) throws IOException {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(data);
            writer.write("\n");
        }
    }

    private boolean isMpmuxPairingQrValue(String data) {
        if (data == null) {
            return false;
        }
        String value = data.trim();
        return value.startsWith("mpmux://sessiond-pair?")
            || value.startsWith("mpmux://pair?")
            || value.startsWith("mpmux://short-code-pair?")
            || isMpmuxShortCodeValue(value);
    }

    private boolean isMpmuxShortCodeValue(String value) {
        return value != null && value.matches("^[0-9]+-[A-Za-z0-9][A-Za-z0-9._~-]{7,}$");
    }

    private void notifyMpmuxPairingSubmitted() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Pairing request sent. Connecting may take a few seconds…", Toast.LENGTH_LONG).show();
            mMpmuxQrScannerPairingNotificationPending = true;
            mHandler.postDelayed(() -> {
                if (mMpmuxQrScannerPairingNotificationPending) {
                    Toast.makeText(this, "Still connecting… waiting for the session host", Toast.LENGTH_LONG).show();
                    mMpmuxQrScannerPairingNotificationPending = false;
                }
            }, 4500);
        });
    }

    private void scheduleMpmuxStartupUpdateCheck() {
        if (mMpmuxStartupUpdateCheckScheduled) {
            return;
        }
        mMpmuxStartupUpdateCheckScheduled = true;
        mHandler.postDelayed(() -> {
            if (!mIsResumed || hasPendingMpmuxUpdateDownload()) {
                mMpmuxStartupUpdateCheckScheduled = false;
                return;
            }
            checkMpmuxSelfUpdate(MPMUX_DEFAULT_UPDATE_MANIFEST_URL, false);
        }, MPMUX_STARTUP_UPDATE_CHECK_DELAY_MS);
    }

    private boolean hasPendingMpmuxUpdateDownload() {
        return mMpmuxUpdateDownloadId > 0
            || getSharedPreferences(MPMUX_UPDATE_PREFS, MODE_PRIVATE).getLong(MPMUX_UPDATE_PREF_DOWNLOAD_ID, -1) > 0;
    }

    private boolean handleMpmuxUpdateUrl(String data) {
        if (data == null || !data.startsWith("mpmux://update?")) {
            return false;
        }
        try {
            Uri uri = Uri.parse(data);
            String manifestUrl = uri.getQueryParameter("manifest");
            if (manifestUrl != null && !manifestUrl.trim().isEmpty()) {
                checkMpmuxSelfUpdate(manifestUrl.trim());
                return true;
            }

            String apkUrl = uri.getQueryParameter("url");
            String sha256 = uri.getQueryParameter("sha256");
            String version = uri.getQueryParameter("version");
            promptMpmuxSelfUpdate(apkUrl, sha256, version);
            return true;
        } catch (RuntimeException err) {
            Log.w(LOG_TAG, "failed to handle mpmux update link", err);
            Toast.makeText(this, "Invalid mpmux update link", Toast.LENGTH_LONG).show();
            return true;
        }
    }

    public void checkMpmuxSelfUpdate(final String manifestUrl) {
        checkMpmuxSelfUpdate(manifestUrl, true);
    }

    private void checkMpmuxSelfUpdate(final String manifestUrl, final boolean userVisible) {
        if (!isHttpsUrl(manifestUrl)) {
            if (userVisible) {
                runOnUiThread(() -> Toast.makeText(this, "Update manifest must use HTTPS", Toast.LENGTH_LONG).show());
            }
            return;
        }

        if (userVisible) {
            Toast.makeText(this, "Checking for mpmux update…", Toast.LENGTH_SHORT).show();
        }
        new Thread(() -> {
            try {
                JSONObject manifest = fetchMpmuxUpdateManifest(manifestUrl);
                String manifestPackage = manifest.optString("package", getPackageName());
                if (!getPackageName().equals(manifestPackage)) {
                    if (userVisible) {
                        runOnUiThread(() -> Toast.makeText(this, "Update manifest targets a different app", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                long currentVersionCode = currentMpmuxVersionCode();
                long nextVersionCode = manifest.optLong("version_code", -1);
                String versionLabel = manifest.optString(
                    "version_label",
                    manifest.optString("version_name", nextVersionCode > 0 ? Long.toString(nextVersionCode) : "update")
                );

                if (nextVersionCode > 0 && currentVersionCode >= nextVersionCode) {
                    if (userVisible) {
                        runOnUiThread(() -> Toast.makeText(this, "mpmux is already up to date", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                String apkUrl = manifest.optString("apk_url", manifest.optString("url", ""));
                String sha256 = manifest.optString("sha256", manifest.optString("apk_sha256", ""));
                if (!isHttpsUrl(apkUrl) || !isValidSha256(sha256)) {
                    if (userVisible) {
                        runOnUiThread(() -> Toast.makeText(this, "Update manifest is missing a valid APK URL or SHA-256", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                runOnUiThread(() -> promptMpmuxSelfUpdate(apkUrl, sha256, versionLabel));
            } catch (Exception err) {
                Log.w(LOG_TAG, "mpmux update check failed", err);
                if (userVisible) {
                    runOnUiThread(() -> Toast.makeText(this, "Update check failed", Toast.LENGTH_LONG).show());
                }
            }
        }, "MpmuxUpdateCheck").start();
    }

    private void promptMpmuxSelfUpdate(final String apkUrl, final String expectedSha256, final String versionLabel) {
        runOnUiThread(() -> {
            String safeVersion = sanitizeUpdateLabel(versionLabel == null || versionLabel.trim().isEmpty() ? "update" : versionLabel.trim());
            new AlertDialog.Builder(this)
                .setTitle("mpmux update available")
                .setMessage("Version " + safeVersion + " is available. Download it now? Android will ask again before installing.")
                .setPositiveButton("Download", (dialog, which) -> startMpmuxSelfUpdate(apkUrl, expectedSha256, safeVersion))
                .setNegativeButton("Not now", null)
                .show();
        });
    }

    public void startMpmuxSelfUpdate(final String apkUrl, final String expectedSha256, final String versionLabel) {
        runOnUiThread(() -> {
            if (!isHttpsUrl(apkUrl)) {
                Toast.makeText(this, "Update APK must use HTTPS", Toast.LENGTH_LONG).show();
                return;
            }
            if (!isValidSha256(expectedSha256)) {
                Toast.makeText(this, "Update APK is missing a valid SHA-256", Toast.LENGTH_LONG).show();
                return;
            }

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (downloadManager == null) {
                Toast.makeText(this, "Android DownloadManager is unavailable", Toast.LENGTH_LONG).show();
                return;
            }
            if (hasPendingMpmuxUpdateDownload()) {
                resumePendingMpmuxUpdateDownload();
                Toast.makeText(this, "mpmux update download is already in progress", Toast.LENGTH_LONG).show();
                return;
            }

            clearMpmuxUpdateDownloadReceiver();
            String safeVersion = sanitizeUpdateLabel(versionLabel == null || versionLabel.trim().isEmpty() ? "update" : versionLabel.trim());
            String fileName = "mpmux-" + safeVersion + "-" + SystemClock.uptimeMillis() + ".apk";
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("mpmux update " + safeVersion);
            request.setDescription("Downloading update. Android will ask before installing.");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            if (getExternalFilesDir(null) == null) {
                Toast.makeText(this, "External storage is unavailable right now; restart the app and try again", Toast.LENGTH_LONG).show();
                return;
            }
            try {
                // Some Samsung FUSE storage builds return null for app-storage
                // subdirectory lookups (e.g. "Download"), which makes this call
                // throw IllegalStateException. Targeting the app files directory
                // root (null type) avoids that on affected devices.
                request.setDestinationInExternalFilesDir(this, null, fileName);
                mMpmuxUpdateExpectedSha256 = expectedSha256.toLowerCase(Locale.ROOT);
                mMpmuxUpdateVersionLabel = safeVersion;
                registerMpmuxUpdateDownloadReceiver();
                mMpmuxUpdateDownloadId = downloadManager.enqueue(request);
                persistMpmuxUpdateDownloadState();
                Toast.makeText(this, "Downloading mpmux update…", Toast.LENGTH_LONG).show();
            } catch (RuntimeException err) {
                clearMpmuxUpdateDownloadReceiver();
                clearMpmuxUpdateDownloadState();
                Log.w(LOG_TAG, "failed to enqueue mpmux update download", err);
                Toast.makeText(this, "Failed to start update download", Toast.LENGTH_LONG).show();
            }
        });
    }

    private JSONObject fetchMpmuxUpdateManifest(String manifestUrl) throws Exception {
        String manifestBody = fetchMpmuxUpdateText(manifestUrl, "application/json", 1024 * 1024);
        return MpmuxUpdateManifestVerifier.parseVerifiedManifest(
            manifestUrl,
            manifestBody,
            (signatureUrl, maxBytes) -> fetchMpmuxUpdateText(signatureUrl, "text/plain", maxBytes)
        );
    }

    private String fetchMpmuxUpdateText(String url, String accept, int maxBytes) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", accept);
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("update HTTP status " + statusCode);
            }
            try (InputStream input = connection.getInputStream()) {
                return readBoundedUtf8(input, maxBytes);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String readBoundedUtf8(InputStream input, int maxBytes) throws IOException {
        byte[] buffer = new byte[8192];
        int total = 0;
        StringBuilder body = new StringBuilder();
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("response too large");
            }
            body.append(new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8));
        }
        return body.toString();
    }

    private void resumePendingMpmuxUpdateDownload() {
        SharedPreferences prefs = getSharedPreferences(MPMUX_UPDATE_PREFS, MODE_PRIVATE);
        long downloadId = prefs.getLong(MPMUX_UPDATE_PREF_DOWNLOAD_ID, -1);
        String expectedSha256 = prefs.getString(MPMUX_UPDATE_PREF_SHA256, null);
        String versionLabel = prefs.getString(MPMUX_UPDATE_PREF_VERSION_LABEL, null);
        if (downloadId <= 0 || !isValidSha256(expectedSha256)) {
            return;
        }

        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return;
        }

        int status = mpmuxUpdateDownloadStatus(downloadManager, downloadId);
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            mMpmuxUpdateDownloadId = downloadId;
            mMpmuxUpdateExpectedSha256 = expectedSha256.toLowerCase(Locale.ROOT);
            mMpmuxUpdateVersionLabel = versionLabel == null ? "update" : versionLabel;
            handleMpmuxUpdateDownloadComplete(downloadId);
        } else if (status == DownloadManager.STATUS_PENDING
            || status == DownloadManager.STATUS_RUNNING
            || status == DownloadManager.STATUS_PAUSED) {
            mMpmuxUpdateDownloadId = downloadId;
            mMpmuxUpdateExpectedSha256 = expectedSha256.toLowerCase(Locale.ROOT);
            mMpmuxUpdateVersionLabel = versionLabel == null ? "update" : versionLabel;
            registerMpmuxUpdateDownloadReceiver();
        } else {
            clearMpmuxUpdateDownloadState();
        }
    }

    private void registerMpmuxUpdateDownloadReceiver() {
        if (mMpmuxUpdateDownloadReceiver != null) {
            return;
        }
        mMpmuxUpdateDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    return;
                }
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId != mMpmuxUpdateDownloadId) {
                    return;
                }
                handleMpmuxUpdateDownloadComplete(downloadId);
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // DownloadManager completion is sent by the platform downloads provider;
            // keep the receiver exported on API 33+ and gate the payload by the
            // exact enqueue id before doing any verification work.
            registerReceiver(mMpmuxUpdateDownloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(mMpmuxUpdateDownloadReceiver, filter);
        }
    }

    private void handleMpmuxUpdateDownloadComplete(long downloadId) {
        clearMpmuxUpdateDownloadReceiver();
        DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(this, "Android DownloadManager is unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        if (!mpmuxUpdateDownloadSucceeded(downloadManager, downloadId)) {
            Toast.makeText(this, "mpmux update download failed", Toast.LENGTH_LONG).show();
            clearMpmuxUpdateDownloadState();
            return;
        }

        try {
            String actualSha256 = sha256ForDownloadedFile(downloadManager, downloadId);
            if (!actualSha256.equalsIgnoreCase(mMpmuxUpdateExpectedSha256)) {
                Log.w(LOG_TAG, "mpmux update SHA-256 mismatch; refusing install");
                Toast.makeText(this, "Update verification failed", Toast.LENGTH_LONG).show();
                clearMpmuxUpdateDownloadState();
                return;
            }
            String validationError = validateMpmuxDownloadedApk(downloadManager, downloadId);
            if (validationError != null) {
                Log.w(LOG_TAG, "mpmux update APK validation failed: " + validationError);
                Toast.makeText(this, validationError, Toast.LENGTH_LONG).show();
                clearMpmuxUpdateDownloadState();
                return;
            }
        } catch (Exception err) {
            Log.w(LOG_TAG, "failed to verify mpmux update", err);
            Toast.makeText(this, "Update verification failed", Toast.LENGTH_LONG).show();
            clearMpmuxUpdateDownloadState();
            return;
        }

        Uri apkUri = downloadManager.getUriForDownloadedFile(downloadId);
        clearMpmuxUpdateDownloadState();
        if (apkUri == null) {
            Toast.makeText(this, "Downloaded update is unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        promptInstallMpmuxUpdate(apkUri);
    }

    private boolean mpmuxUpdateDownloadSucceeded(DownloadManager downloadManager, long downloadId) {
        return mpmuxUpdateDownloadStatus(downloadManager, downloadId) == DownloadManager.STATUS_SUCCESSFUL;
    }

    private int mpmuxUpdateDownloadStatus(DownloadManager downloadManager, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return -1;
            }
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            return statusIndex >= 0 ? cursor.getInt(statusIndex) : -1;
        }
    }

    private String sha256ForDownloadedFile(DownloadManager downloadManager, long downloadId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (ParcelFileDescriptor descriptor = downloadManager.openDownloadedFile(downloadId);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return hexDigest(digest.digest());
    }

    private String validateMpmuxDownloadedApk(DownloadManager downloadManager, long downloadId) throws Exception {
        File validationApk = copyDownloadedApkForPackageValidation(downloadManager, downloadId);
        try {
            PackageInfo updateInfo = getPackageManager().getPackageArchiveInfo(
                validationApk.getAbsolutePath(),
                packageInfoSignatureFlags()
            );
            if (updateInfo == null) {
                return "Downloaded update is not a valid APK";
            }
            if (!getPackageName().equals(updateInfo.packageName)) {
                return "Downloaded update targets a different app";
            }

            long updateVersionCode = packageInfoVersionCode(updateInfo);
            long currentVersionCode = currentMpmuxVersionCode();
            if (updateVersionCode > 0 && currentVersionCode >= updateVersionCode) {
                return "Downloaded update is not newer";
            }

            PackageInfo installedInfo = getPackageManager().getPackageInfo(getPackageName(), packageInfoSignatureFlags());
            Set<String> installedSigners = packageSigningSha256(installedInfo);
            Set<String> updateSigners = packageSigningSha256(updateInfo);
            if (installedSigners.isEmpty() || updateSigners.isEmpty()) {
                return "Downloaded update signing certificate is unavailable";
            }
            if (!installedSigners.containsAll(updateSigners)) {
                return "Downloaded update is signed by a different key";
            }
            return null;
        } finally {
            if (!validationApk.delete()) {
                validationApk.deleteOnExit();
            }
        }
    }

    private File copyDownloadedApkForPackageValidation(DownloadManager downloadManager, long downloadId) throws IOException {
        File validationApk = new File(getCacheDir(), "mpmux-update-validation.apk");
        try (ParcelFileDescriptor descriptor = downloadManager.openDownloadedFile(downloadId);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(validationApk, false)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return validationApk;
    }

    private long packageInfoVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    @SuppressWarnings("deprecation")
    private int packageInfoSignatureFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return PackageManager.GET_SIGNING_CERTIFICATES;
        }
        return PackageManager.GET_SIGNATURES;
    }

    @SuppressWarnings("deprecation")
    private Set<String> packageSigningSha256(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && info.signingInfo != null) {
            if (info.signingInfo.hasMultipleSigners()) {
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                signatures = info.signingInfo.getSigningCertificateHistory();
            }
        } else {
            signatures = info.signatures;
        }

        java.util.HashSet<String> digests = new java.util.HashSet<>();
        if (signatures == null) {
            return digests;
        }
        for (Signature signature : signatures) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digests.add(hexDigest(digest.digest(signature.toByteArray())));
        }
        return digests;
    }

    private String hexDigest(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return hex.toString();
    }

    private void promptInstallMpmuxUpdate(Uri apkUri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Allow mpmux to install unknown apps, then retry the update", Toast.LENGTH_LONG).show();
            Intent settingsIntent = new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())
            );
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(settingsIntent);
            } catch (RuntimeException err) {
                Log.w(LOG_TAG, "failed to open unknown-app install settings", err);
            }
            return;
        }

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(installIntent);
            Toast.makeText(this, "Android will ask before installing the verified update", Toast.LENGTH_LONG).show();
        } catch (RuntimeException err) {
            Log.w(LOG_TAG, "failed to open Android package installer", err);
            Toast.makeText(this, "Could not open Android installer", Toast.LENGTH_LONG).show();
        }
    }

    private void clearMpmuxUpdateDownloadReceiver() {
        if (mMpmuxUpdateDownloadReceiver == null) {
            return;
        }
        try {
            unregisterReceiver(mMpmuxUpdateDownloadReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        mMpmuxUpdateDownloadReceiver = null;
    }

    private void persistMpmuxUpdateDownloadState() {
        if (mMpmuxUpdateDownloadId <= 0 || !isValidSha256(mMpmuxUpdateExpectedSha256)) {
            return;
        }
        getSharedPreferences(MPMUX_UPDATE_PREFS, MODE_PRIVATE)
            .edit()
            .putLong(MPMUX_UPDATE_PREF_DOWNLOAD_ID, mMpmuxUpdateDownloadId)
            .putString(MPMUX_UPDATE_PREF_SHA256, mMpmuxUpdateExpectedSha256)
            .putString(MPMUX_UPDATE_PREF_VERSION_LABEL, mMpmuxUpdateVersionLabel == null ? "update" : mMpmuxUpdateVersionLabel)
            .apply();
    }

    private void clearMpmuxUpdateDownloadState() {
        mMpmuxUpdateDownloadId = -1;
        mMpmuxUpdateExpectedSha256 = null;
        mMpmuxUpdateVersionLabel = null;
        getSharedPreferences(MPMUX_UPDATE_PREFS, MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }

    private boolean isHttpsUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            Uri uri = Uri.parse(url.trim());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (RuntimeException err) {
            return false;
        }
    }

    private boolean isValidSha256(String sha256) {
        return sha256 != null && sha256.matches("(?i)^[0-9a-f]{64}$");
    }

    private String sanitizeUpdateLabel(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "-");
        if (sanitized.isEmpty()) {
            return "update";
        }
        return sanitized.length() > 48 ? sanitized.substring(0, 48) : sanitized;
    }

    private long currentMpmuxVersionCode() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException err) {
            return -1;
        }
    }

    public void startMpmuxQrScanner() {
        runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 7301);
                return;
            }
            showMpmuxQrScannerOverlay();
        });
    }

    private void showMpmuxQrScannerOverlay() {
        if (mMpmuxQrScannerActive) {
            return;
        }
        try {
            mMpmuxQrScannerCamera = android.hardware.Camera.open();
        } catch (RuntimeException err) {
            Log.w(LOG_TAG, "failed to open camera for mpmux QR scanner", err);
            return;
        }

        mMpmuxQrScannerActive = true;
        mMpmuxQrScannerOverlay = new FrameLayout(this);
        mMpmuxQrScannerOverlay.setBackgroundColor(Color.BLACK);
        mMpmuxQrScannerPreview = new SurfaceView(this);
        mMpmuxQrScannerOverlay.addView(mMpmuxQrScannerPreview, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mMpmuxQrScannerResultOverlay = new View(this) {
            private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            {
                checkPaint.setColor(0xFF5DFF7A);
                checkPaint.setStyle(Paint.Style.STROKE);
                checkPaint.setStrokeCap(Paint.Cap.ROUND);
                checkPaint.setStrokeJoin(Paint.Join.ROUND);
                checkPaint.setStrokeWidth(Math.max(10.0f, getResources().getDisplayMetrics().density * 8.0f));
                textPaint.setColor(Color.WHITE);
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(Math.max(24.0f, getResources().getDisplayMetrics().scaledDensity * 22.0f));
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                drawMpmuxQrScannerResult(canvas, checkPaint, textPaint, getWidth(), getHeight());
            }
        };
        mMpmuxQrScannerResultOverlay.setClickable(false);
        mMpmuxQrScannerResultOverlay.setFocusable(false);
        mMpmuxQrScannerOverlay.addView(mMpmuxQrScannerResultOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView label = new TextView(this);
        label.setText("Scan mpmux pairing or short-code QR\nTap to focus · Back to cancel");
        label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);
        label.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        );
        mMpmuxQrScannerOverlay.addView(label, labelParams);
        mMpmuxQrScannerOverlay.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                focusMpmuxQrScannerAt(event.getX(), event.getY(), view.getWidth(), view.getHeight());
            }
            return true;
        });
        mRootLayout.addView(mMpmuxQrScannerOverlay, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mMpmuxQrScannerPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startMpmuxQrScannerPreview(holder);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                startMpmuxQrScannerPreview(holder);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopMpmuxQrScannerCamera();
            }
        });
    }

    private void startMpmuxQrScannerPreview(SurfaceHolder holder) {
        if (mMpmuxQrScannerCamera == null) {
            return;
        }
        try {
            mMpmuxQrScannerCamera.stopPreview();
        } catch (RuntimeException ignored) {}
        try {
            mMpmuxQrScannerCamera.setPreviewDisplay(holder);
            android.hardware.Camera.Parameters params = mMpmuxQrScannerCamera.getParameters();
            configureMpmuxQrScannerCamera(params);
            mMpmuxQrScannerCamera.setParameters(params);
            mMpmuxQrScannerCamera.setDisplayOrientation(mpmuxQrScannerDisplayOrientation());
            android.hardware.Camera.Size size = params.getPreviewSize();
            mMpmuxQrScannerCamera.setPreviewCallback((data, camera) -> decodeMpmuxQrFrame(data, size.width, size.height));
            mMpmuxQrScannerCamera.startPreview();
            mHandler.removeCallbacks(mMpmuxQrScannerAutofocus);
            mHandler.postDelayed(mMpmuxQrScannerAutofocus, 400);
        } catch (IOException | RuntimeException err) {
            Log.w(LOG_TAG, "failed to start mpmux QR scanner preview", err);
            stopMpmuxQrScanner();
        }
    }

    private void configureMpmuxQrScannerCamera(android.hardware.Camera.Parameters params) {
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_MACRO)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_MACRO);
            } else if (focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);
            }
        }

        if (params.getMaxNumFocusAreas() > 0) {
            ArrayList<android.hardware.Camera.Area> focusAreas = new ArrayList<>();
            focusAreas.add(new android.hardware.Camera.Area(new Rect(-350, -350, 350, 350), 1000));
            params.setFocusAreas(focusAreas);
        }
        if (params.getMaxNumMeteringAreas() > 0) {
            ArrayList<android.hardware.Camera.Area> meteringAreas = new ArrayList<>();
            meteringAreas.add(new android.hardware.Camera.Area(new Rect(-500, -500, 500, 500), 1000));
            params.setMeteringAreas(meteringAreas);
        }

        if (params.isZoomSupported()) {
            int maxZoom = params.getMaxZoom();
            if (maxZoom > 0) {
                params.setZoom(Math.min(maxZoom, 3));
            }
        }

        android.hardware.Camera.Size preferredSize = null;
        List<android.hardware.Camera.Size> sizes = params.getSupportedPreviewSizes();
        if (sizes != null) {
            for (android.hardware.Camera.Size size : sizes) {
                if (preferredSize == null || Math.abs((size.width * size.height) - (1280 * 720)) < Math.abs((preferredSize.width * preferredSize.height) - (1280 * 720))) {
                    preferredSize = size;
                }
            }
        }
        if (preferredSize != null) {
            params.setPreviewSize(preferredSize.width, preferredSize.height);
        }
    }

    private void focusMpmuxQrScannerAt(float x, float y, int viewWidth, int viewHeight) {
        if (mMpmuxQrScannerCamera == null || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        try {
            mHandler.removeCallbacks(mMpmuxQrScannerAutofocus);
            mMpmuxQrScannerCamera.cancelAutoFocus();
            android.hardware.Camera.Parameters params = mMpmuxQrScannerCamera.getParameters();
            if (params.getMaxNumFocusAreas() > 0) {
                ArrayList<android.hardware.Camera.Area> focusAreas = new ArrayList<>();
                focusAreas.add(new android.hardware.Camera.Area(mpmuxQrScannerFocusRect(x, y, viewWidth, viewHeight, 260), 1000));
                params.setFocusAreas(focusAreas);
            }
            if (params.getMaxNumMeteringAreas() > 0) {
                ArrayList<android.hardware.Camera.Area> meteringAreas = new ArrayList<>();
                meteringAreas.add(new android.hardware.Camera.Area(mpmuxQrScannerFocusRect(x, y, viewWidth, viewHeight, 420), 1000));
                params.setMeteringAreas(meteringAreas);
            }
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO);
            }
            mMpmuxQrScannerCamera.setParameters(params);
            mMpmuxQrScannerCamera.autoFocus((success, camera) -> {
                if (mMpmuxQrScannerActive) {
                    mHandler.postDelayed(mMpmuxQrScannerAutofocus, 1800);
                }
            });
        } catch (RuntimeException err) {
            Log.w(LOG_TAG, "failed to focus mpmux QR scanner", err);
        }
    }

    private Rect mpmuxQrScannerFocusRect(float x, float y, int viewWidth, int viewHeight, int size) {
        int centerX = clamp((int) ((x / viewWidth) * 2000.0f - 1000.0f), -1000, 1000);
        int centerY = clamp((int) ((y / viewHeight) * 2000.0f - 1000.0f), -1000, 1000);
        int half = size / 2;
        return new Rect(
            clamp(centerX - half, -1000, 1000),
            clamp(centerY - half, -1000, 1000),
            clamp(centerX + half, -1000, 1000),
            clamp(centerY + half, -1000, 1000)
        );
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int mpmuxQrScannerDisplayOrientation() {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(0, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            case Surface.ROTATION_0:
            default:
                degrees = 0;
                break;
        }
        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private void decodeMpmuxQrFrame(byte[] data, int width, int height) {
        if (!mMpmuxQrScannerActive) {
            return;
        }
        try {
            Class<?> detectorBuilderClass = Class.forName("com.google.android.gms.vision.barcode.BarcodeDetector$Builder");
            Class<?> barcodeDetectorClass = Class.forName("com.google.android.gms.vision.barcode.BarcodeDetector");
            Class<?> barcodeClass = Class.forName("com.google.android.gms.vision.barcode.Barcode");
            Object builder = detectorBuilderClass.getConstructor(Context.class).newInstance(this);
            Method setBarcodeFormats = detectorBuilderClass.getMethod("setBarcodeFormats", int.class);
            setBarcodeFormats.invoke(builder, barcodeClass.getField("QR_CODE").getInt(null));
            Object detector = detectorBuilderClass.getMethod("build").invoke(builder);
            boolean operational = (Boolean) barcodeDetectorClass.getMethod("isOperational").invoke(detector);
            if (!operational) {
                barcodeDetectorClass.getMethod("release").invoke(detector);
                decodeMpmuxQrFrameWithZxing(data, width, height);
                return;
            }

            Class<?> frameBuilderClass = Class.forName("com.google.android.gms.vision.Frame$Builder");
            Object frameBuilder = frameBuilderClass.getConstructor().newInstance();
            Method setImageData = frameBuilderClass.getMethod("setImageData", ByteBuffer.class, int.class, int.class, int.class);
            setImageData.invoke(frameBuilder, ByteBuffer.wrap(data), width, height, ImageFormat.NV21);
            Object frame = frameBuilderClass.getMethod("build").invoke(frameBuilder);
            SparseArray<?> barcodes = (SparseArray<?>) barcodeDetectorClass.getMethod("detect", Class.forName("com.google.android.gms.vision.Frame")).invoke(detector, frame);
            barcodeDetectorClass.getMethod("release").invoke(detector);
            if (barcodes.size() == 0) {
                return;
            }
            Object barcode = barcodes.valueAt(0);
            String value = (String) barcodeClass.getField("displayValue").get(barcode);
            if (value == null || value.isEmpty()) {
                value = (String) barcodeClass.getField("rawValue").get(barcode);
            }
            if (isMpmuxPairingQrValue(value)) {
                handleMpmuxQrScannerResult(value.trim(), null, width, height);
            }
        } catch (ClassNotFoundException err) {
            decodeMpmuxQrFrameWithZxing(data, width, height);
        } catch (Exception err) {
            Log.w(LOG_TAG, "failed to decode mpmux QR frame", err);
        }
    }

    private void decodeMpmuxQrFrameWithZxing(byte[] data, int width, int height) {
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                data,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            );
            Result result = decodeMpmuxQrLuminanceSource(source);
            if (result == null && source.isRotateSupported()) {
                LuminanceSource rotated = source.rotateCounterClockwise();
                result = decodeMpmuxQrLuminanceSource(rotated);
                if (result == null && rotated.isRotateSupported()) {
                    LuminanceSource rotatedTwice = rotated.rotateCounterClockwise();
                    result = decodeMpmuxQrLuminanceSource(rotatedTwice);
                    if (result == null && rotatedTwice.isRotateSupported()) {
                        result = decodeMpmuxQrLuminanceSource(rotatedTwice.rotateCounterClockwise());
                    }
                }
            }
            if (result == null) {
                return;
            }
            String value = result.getText();
            if (isMpmuxPairingQrValue(value)) {
                handleMpmuxQrScannerResult(value.trim(), result.getResultPoints(), width, height);
            }
        } catch (Exception err) {
            Log.w(LOG_TAG, "failed to decode mpmux QR frame with bundled ZXing", err);
        }
    }

    private Result decodeMpmuxQrLuminanceSource(LuminanceSource source) throws Exception {
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        MultiFormatReader reader = new MultiFormatReader();
        EnumMap<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.of(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        try {
            return reader.decode(bitmap, hints);
        } catch (NotFoundException err) {
            return null;
        } finally {
            reader.reset();
        }
    }

    private void handleMpmuxQrScannerResult(String pairingUrl, ResultPoint[] points, int width, int height) {
        runOnUiThread(() -> {
            if (mMpmuxQrScannerFound) {
                return;
            }
            mMpmuxQrScannerFound = true;
            mMpmuxQrScannerResultPoints = points;
            mMpmuxQrScannerResultWidth = width;
            mMpmuxQrScannerResultHeight = height;
            if (mMpmuxQrScannerResultOverlay != null) {
                mMpmuxQrScannerResultOverlay.invalidate();
            }
            Toast.makeText(this, "QR found. Pairing and connecting…", Toast.LENGTH_LONG).show();
            mHandler.postDelayed(() -> {
                if (mMpmuxQrScannerActive) {
                    persistMpmuxPairingUrl(pairingUrl, true);
                    stopMpmuxQrScanner();
                }
            }, 700);
        });
    }

    private void drawMpmuxQrScannerResult(Canvas canvas, Paint checkPaint, Paint textPaint, int viewWidth, int viewHeight) {
        if (mMpmuxQrScannerFound) {
            canvas.drawColor(0x99000000);
            float size = Math.min(viewWidth, viewHeight) * 0.32f;
            float centerX = viewWidth * 0.5f;
            float centerY = viewHeight * 0.42f;
            canvas.drawLine(centerX - size * 0.45f, centerY, centerX - size * 0.12f, centerY + size * 0.32f, checkPaint);
            canvas.drawLine(centerX - size * 0.12f, centerY + size * 0.32f, centerX + size * 0.50f, centerY - size * 0.36f, checkPaint);
            canvas.drawText("QR found", centerX, centerY + size * 0.78f, textPaint);
            canvas.drawText("Pairing and connecting…", centerX, centerY + size * 1.08f, textPaint);
            return;
        }
    }

    private void stopMpmuxQrScanner() {
        stopMpmuxQrScannerCamera();
        if (mMpmuxQrScannerOverlay != null) {
            mRootLayout.removeView(mMpmuxQrScannerOverlay);
            mMpmuxQrScannerOverlay = null;
            mMpmuxQrScannerPreview = null;
            mMpmuxQrScannerResultOverlay = null;
        }
        mMpmuxQrScannerResultPoints = null;
        mMpmuxQrScannerResultWidth = 0;
        mMpmuxQrScannerResultHeight = 0;
        mMpmuxQrScannerFound = false;
        mMpmuxQrScannerActive = false;
    }

    private void stopMpmuxQrScannerCamera() {
        if (mMpmuxQrScannerCamera != null) {
            try {
                mHandler.removeCallbacks(mMpmuxQrScannerAutofocus);
                mMpmuxQrScannerCamera.setPreviewCallback(null);
                mMpmuxQrScannerCamera.cancelAutoFocus();
                mMpmuxQrScannerCamera.stopPreview();
            } catch (RuntimeException ignored) {}
            mMpmuxQrScannerCamera.release();
            mMpmuxQrScannerCamera = null;
        }
    }


    @SuppressWarnings("deprecation")
    public float getDeviceRefreshRate() {
        float refreshRate = 60.0f;  // Default to a common refresh rate

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use getDisplay() API on Android 11 and above
            Display display = getDisplay();
            if (display != null) {
                refreshRate = display.getRefreshRate();
            }
        } else {
            // Use the old method for Android 10 and below
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                Display display = windowManager.getDefaultDisplay();
                refreshRate = display.getRefreshRate();
            }
        }

        return refreshRate;
    }
}
