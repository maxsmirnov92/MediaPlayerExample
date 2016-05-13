package ru.maxsmr.mediaplayerexample;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.ngweb.android.api.filedialog.FileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import ru.maxsmr.commonutils.android.GuiUtils;
import ru.maxsmr.commonutils.data.FileHelper;
import ru.maxsmr.mediaplayercontroller.MediaPlayerController;
import ru.maxsmr.mediaplayercontroller.PlaylistManager;
import ru.maxsmr.mediaplayercontroller.utils.MetadataRetriever;

public class TestActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnErrorListener, MediaPlayerController.OnVideoSizeChangedListener, PlaylistManager.OnTracksSetListener, AdapterView.OnItemSelectedListener {

    private static final Logger logger = LoggerFactory.getLogger(TestActivity.class);

    private MediaPlayerController mediaPlayerController;
    private PlaylistManager playlistManager;

    private Spinner navigationSpinner;

    private LinearLayout loadingLayout;
    private TextView errorView;

    private SurfaceView surfaceView;
    private MediaController mediaViews;

    private void initToolbar() {
        String title = getTitle().toString();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationIcon(null);
            toolbar.setTitle(title);
            setSupportActionBar(toolbar);
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(title);
                actionBar.setLogo(android.R.drawable.ic_media_play);
                actionBar.setDisplayUseLogoEnabled(false);
                actionBar.setDisplayShowTitleEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(false);
                actionBar.show();
            }
        }
    }

    private void initSpinner() {
        navigationSpinner = (Spinner) findViewById(R.id.action_bar_spinner);
        SpinnerAdapter spinnerAdapter = ArrayAdapter.createFromResource(getApplicationContext(), R.array.actionbar_navigation_items, android.R.layout.simple_list_item_1);
        navigationSpinner.setAdapter(spinnerAdapter);
        navigationSpinner.setOnItemSelectedListener(this);
    }

    private void initMediaController() {
        mediaPlayerController = new MediaPlayerController(this);
        mediaPlayerController.getStateChangedObservable().registerObserver(this);
        mediaPlayerController.getErrorObservable().registerObserver(this);
        mediaPlayerController.getVideoSizeChangedObservable().registerObserver(this);
    }

    private void initPlaylistManager() {
        playlistManager = new PlaylistManager(mediaPlayerController);
        playlistManager.enableLoopPlaylist(true);
        playlistManager.getTracksSetObservable().registerObserver(this);
    }

    @SuppressWarnings("ConstantConditions")
    private void initMediaViews() {

        if (mediaViews != null) {
            throw new IllegalStateException("mediaViews is already initialized");
        }

        FrameLayout contentLayout = (FrameLayout) findViewById(R.id.flContent);
        mediaViews = new MediaController(this);
        mediaViews.setPrevNextListeners(new NextClickListener(), new PreviousClickListener());
        ViewGroup parent = (ViewGroup) mediaViews.getParent();
        parent.removeAllViews();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.BOTTOM;
        contentLayout.addView(mediaViews, lp);
        mediaPlayerController.setMediaController(mediaViews, contentLayout);
    }

    @SuppressWarnings("ConstantConditions")
    private void initSurfaceView() {

        if (surfaceView != null) {
            throw new IllegalStateException("surfaceView is already initialized");
        }

        FrameLayout contentLayout = (FrameLayout) findViewById(R.id.flContent);
        surfaceView = new SurfaceView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        contentLayout.addView(surfaceView, lp);
        surfaceView.getHolder().addCallback(this);
        surfaceView.getHolder().setFixedSize(1, 1);
//        surfaceView.setVisibility(View.GONE);
        mediaPlayerController.setVideoView(surfaceView);
    }

    private Menu menu;

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_test, this.menu = menu);

//        // Restore the check state e.g. if the device has been rotated.
//        final MenuItem fileSelectModeItem = menu.findItem(R.id.actionFileSelectMode);
//        CheckBox cb = (CheckBox) fileSelectModeItem.getActionView().findViewById(R.id.actionItemCheckbox);
//        if (cb != null) {
//            // Set the text to match the item.
//            cb.setText(fileSelectModeItem.getTitle());
//            // Add the onClickListener because the CheckBox doesn't automatically trigger onOptionsItemSelected.
//            cb.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    setActionFileSelectModeChecked(menu, !fileSelectModeItem.isChecked());
//                    onOptionsItemSelected(fileSelectModeItem);
//                }
//            });
//        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.actionAddTrack) {
            if (navigationSpinner.getSelectedItemPosition() == 0) {
                showFileSelectDialog();
            } else {
                showUrlInputDialog();
            }
            return true;
        } else if (item.getItemId() == R.id.actionIsPlaylist) {
            item.setChecked(!isActionPlaylistChecked(menu));
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isActionPlaylistChecked(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.actionIsPlaylist);
//        // Since it is shown as an action, and not in the sub-menu we have to manually set the icon too.
//        CheckBox cb = (CheckBox) menuItem.getActionView().findViewById(R.id.actionItemCheckbox);
//        return cb != null && cb.isChecked();
        return menuItem.isChecked();
    }

    // Set the check state of an actionbar item that has its actionLayout set to a layout
    // containing a checkbox with the ID action_item_checkbox.
//    private void setActionFileSelectModeChecked(Menu menu, boolean checked) {
//        MenuItem menuItem = menu.findItem(R.id.actionFileSelectMode);
//        menuItem.setChecked(checked);
//        // Since it is shown as an action, and not in the sub-menu we have to manually set the icon too.
//        CheckBox cb = (CheckBox) menuItem.getActionView().findViewById(R.id.actionItemCheckbox);
//        if (cb != null)
//            cb.setChecked(checked);
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.debug("onCreate()");
        setContentView(R.layout.activity_test);
        initToolbar();
        initSpinner();
        initMediaController();
        initMediaViews();
        initSurfaceView();
        initPlaylistManager();

        loadingLayout = (LinearLayout) findViewById(R.id.llLoading);
        errorView = (TextView) findViewById(R.id.emptyText);
        GuiUtils.setProgressBarColor(ContextCompat.getColor(this, R.color.progressBarColor), (ProgressBar) loadingLayout.findViewById(R.id.pbLoading));
    }

    @Override
    protected void onResume() {
        super.onResume();
        logger.debug("onResume()");
        if (mediaPlayerController.isVideoSpecified() && mediaPlayerController.getTargetState() == MediaPlayerController.State.PLAYING) {
            mediaPlayerController.start();
        }
        invalidateByCurrentState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        logger.debug("onStop()");
        if (mediaPlayerController.isVideoSpecified()) {
            mediaPlayerController.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.debug("onDestroy()");

        hideFileSelectDialog();
        hideUrlInputDialog();

        if (surfaceView != null) {
            surfaceView.getHolder().removeCallback(this);
        }

        mediaPlayerController.getStateChangedObservable().unregisterObserver(this);
        mediaPlayerController.getErrorObservable().unregisterObserver(this);
        mediaPlayerController.release();
        mediaPlayerController = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        logger.debug("onKeyDown(), keyCode=" + keyCode + ", event=" + event);

        boolean isKeyCodeSupported =
                keyCode != KeyEvent.KEYCODE_BACK &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                        keyCode != KeyEvent.KEYCODE_MENU &&
                        keyCode != KeyEvent.KEYCODE_CALL &&
                        keyCode != KeyEvent.KEYCODE_ENDCALL;


        if (isKeyCodeSupported && mediaPlayerController.isInPlaybackState() && mediaPlayerController.isMediaControllerAttached()) {

            MediaController mediaController = mediaPlayerController.getMediaController();

            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mediaPlayerController.isPlaying()) {
                    mediaPlayerController.pause();
                    mediaController.show();
                } else {
                    mediaPlayerController.start();
                    mediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mediaPlayerController.isPlaying()) {
                    mediaPlayerController.start();
                    mediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mediaPlayerController.isPlaying()) {
                    mediaPlayerController.pause();
                    mediaController.show();
                }
                return true;
            } else {
                mediaPlayerController.toggleMediaControlsVisibility();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onCurrentStateChanged(@NonNull MediaPlayerController.State currentState) {
        logger.info("onCurrentStateChanged(), currentState=" + currentState);
        invalidateByCurrentState();
        if (currentState == MediaPlayerController.State.PLAYING) {
            logger.debug("metadata: " + MetadataRetriever.extractMetaData(this, mediaPlayerController.isAudioSpecified() ? mediaPlayerController.getAudioUri() : mediaPlayerController.getVideoUri(), null));
        }
    }

    @Override
    public void onTargetStateChanged(@NonNull MediaPlayerController.State targetState) {
        logger.info("onTargetStateChanged(), targetState=" + targetState);
    }

    @Override
    public void onError(int what, int extra) {
        logger.error("onError(), what=" + what + ", extra=" + extra);
        processError();
    }

    private void invalidateByCurrentState() {
        errorView.setVisibility(View.GONE);
        MediaPlayerController.State currentState = mediaPlayerController.getCurrentState();
        if (currentState == MediaPlayerController.State.PREPARING) {
            loadingLayout.setVisibility(View.VISIBLE);
            if (mediaViews != null) {
                mediaViews.setVisibility(View.GONE);
            }
        } else {
            loadingLayout.setVisibility(View.GONE);
            if (mediaViews != null) {
                mediaViews.setVisibility(View.VISIBLE);
            }
        }
//        surfaceView.setVisibility(mediaPlayerController.isAudioSpecified() ? View.GONE : View.VISIBLE);
    }

    private void processError() {
        loadingLayout.setVisibility(View.GONE);
        mediaViews.setVisibility(View.GONE);
//        surfaceView.setVisibility(View.GONE);
        errorView.setText(R.string.error_url_load_failed);
        errorView.setVisibility(View.VISIBLE);
    }

    private File lastSelectedDirectory;
    private Dialog fileDialog;

    private boolean isFileDialogShowing() {
        return fileDialog != null && fileDialog.isShowing();
    }

    private void showFileSelectDialog() {
        if (!isFileDialogShowing()) {

            final boolean isPlaylist = isActionPlaylistChecked(menu);

            final FileDialog fd = new FileDialog(this);
            fd.setShowDirectoryOnly(isPlaylist);
            fd.setFileSortedBy(FileDialog.SORTED_BY_NAME);
            fd.initDirectory(lastSelectedDirectory != null && FileHelper.isDirExists(lastSelectedDirectory.getAbsolutePath()) ? lastSelectedDirectory.getAbsolutePath() : Environment.getExternalStorageDirectory().toString());
            fd.addDirectoryListener(new FileDialog.DirSelectedListener() {
                @Override
                public void directorySelected(File directory, String[] dirs, String[] files) {
                    if (directory != null && FileHelper.isDirExists(directory.getAbsolutePath())) {
                        lastSelectedDirectory = directory;

                        if (isPlaylist) {
                            List<File> folderFiles = FileHelper.getFiles(directory, false, null);
                            List<String> filesList = new ArrayList<>();
                            for (File f : folderFiles) {
                                filesList.add(f.getAbsolutePath());
                            }
                            playlistManager.setTracks(filesList);
                            playlistManager.playTrack(0);
                            hideFileSelectDialog();
                        }
                    }
                }
            });
            fd.addFileListener(new FileDialog.FileSelectedListener() {
                @Override
                public void fileSelected(File file, String[] strings, String[] strings1) {

                    if (!isPlaylist) {

                        playlistManager.clearTracks();

                        String mimeType = URLConnection.guessContentTypeFromName(Uri.parse(file.getAbsolutePath()).toString());
                        logger.debug("mimeType=" + mimeType);

                        if (mimeType.contains("audio")) {
                            mediaPlayerController.setAudioFile(file);
                        } else if (mimeType.contains("video")) {
                            mediaPlayerController.setVideoFile(file);
                        }

                        mediaPlayerController.start();
                        mediaPlayerController.resume();
                        hideFileSelectDialog();
                    }
                }
            });

            (fileDialog = fd.createFileDialog()).show();
        }
    }

    private void hideFileSelectDialog() {
        if (isFileDialogShowing()) {
            fileDialog.hide();
            fileDialog = null;
        }
    }

    private Dialog urlInputDialog;

    private boolean isUrlInputDialogShowing() {
        return urlInputDialog != null && urlInputDialog.isShowing();
    }

    private void showUrlInputDialog() {
        if (!isUrlInputDialogShowing()) {

            final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_url, null);
            final EditText urlView = (EditText) dialogView.findViewById(R.id.etUrl);
            final CheckedTextView choiceView = (CheckedTextView) dialogView.findViewById(R.id.tvUrlChoice);
            choiceView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choiceView.toggle();
                }
            });

            if (BuildConfig.URL_TYPE_TO_USE.equalsIgnoreCase("video")) {
                urlView.setText(BuildConfig.VIDEO_URL);
                choiceView.setChecked(true);
            } else {
                urlView.setText(BuildConfig.AUDIO_URL);
                choiceView.setChecked(false);
            }

            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setCancelable(true).setTitle(R.string.dialog_url_title).setView(dialogView).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    String url = urlView.getText().toString().trim();

                    if (!TextUtils.isEmpty(url)) {

                        try {
                            new URL(url);
                        } catch (MalformedURLException e) {
                            Toast.makeText(TestActivity.this, R.string.error_url_incorrect, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        final boolean isPlaylist = isActionPlaylistChecked(menu);
                        final boolean isVideo = choiceView.isChecked();

                        if (!isPlaylist) {
                            if (!isVideo) {
                                mediaPlayerController.setAudioPath(url);
//                            surfaceView.setVisibility(View.GONE);
                            } else {
                                mediaPlayerController.setVideoPath(url);
//                            surfaceView.setVisibility(View.VISIBLE);
                            }
                            mediaPlayerController.start();
                            mediaPlayerController.resume();

                        } else {
//                            if (isVideo != (playlistManager.getPlayMode() == PlaylistManager.PlayMode.VIDEO)) {
//                                playlistManager.clearTracks();
//                            }
                            playlistManager.setPlayMode(isVideo? PlaylistManager.PlayMode.VIDEO : PlaylistManager.PlayMode.AUDIO);
                            playlistManager.addTrack(url);
                            playlistManager.playLastTrack();
                        }
                    }

                }
            }).show();
        }
    }

    private void hideUrlInputDialog() {
        if (isUrlInputDialogShowing()) {
            urlInputDialog.hide();
            urlInputDialog = null;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        mediaPlayerController.stop();
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        logger.debug("onVideoSizeChanged(), width=" + width + ", height=" + height);
        if (width > 0 && height > 0) {
            Display display = ((WindowManager) (getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            GuiUtils.setViewSize(surfaceView, GuiUtils.getSurfaceViewSizeByPreviewSize(new Point(displayMetrics.widthPixels, displayMetrics.heightPixels), new Point(width, height), displayMetrics.widthPixels, GuiUtils.FitSize.FIT_WIDTH)); // getSurfaceViewSizeByDisplaySize(this, (float) width / (float) height)
        } else {
            GuiUtils.setViewSize(surfaceView, new Point(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        logger.debug("surfaceCreated()");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        logger.debug("surfaceChanged(), format=" + format + ", width=" + width + ", height=" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        logger.debug("surfaceDestroyed()");
    }

    @Override
    public void onTracksSet(@NonNull List<String> newTracks) {
        logger.debug("onTracksSet(), newTracks=" + newTracks);
    }

    @Override
    public void onTracksNotSet(@NonNull List<String> incorrectTracks) {
        logger.debug("onTracksNotSet(), incorrectTracks=" + incorrectTracks);
    }

    private class PreviousClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            playlistManager.playPreviousTrack();
        }
    }

    private class NextClickListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            playlistManager.playNextTrack();
        }
    }
}
