package ru.maxsmr.mediaplayerexample;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Point;
import android.media.AudioManager;
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
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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
import ru.maxsmr.mediaplayercontroller.PlaylistManager;
import ru.maxsmr.mediaplayercontroller.ScrobblerHelper;
import ru.maxsmr.mediaplayercontroller.mpc.MediaPlayerController;
import ru.maxsmr.mediaplayerexample.player.MediaPlayerFactory;

public class TestActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnErrorListener, MediaPlayerController.OnVideoSizeChangedListener, MediaPlayerController.OnPlaybackTimeUpdateTimeListener, PlaylistManager.OnTracksSetListener, SeekBar.OnSeekBarChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(TestActivity.class);

    private MediaPlayerController mediaPlayerController;
    private PlaylistManager playlistManager;
    private ScrobblerHelper scrobblerHelper;

    private Spinner navigationSpinner;

    private SeekBar trackBar;

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
//        navigationSpinner.setOnItemSelectedListener(this);
    }

    private void initTrackBar() {
        trackBar = (SeekBar) findViewById(R.id.sbTrackbar);
        trackBar.setOnSeekBarChangeListener(this);
        trackBar.setVisibility(View.GONE);
    }

    private void initMediaController() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mediaPlayerController = MediaPlayerFactory.getInstance().create("mpc_test");
        mediaPlayerController.setNotifyPlaybackTimeInterval(200);
        mediaPlayerController.getStateChangedObservable().registerObserver(this);
        mediaPlayerController.getErrorObservable().registerObserver(this);
        mediaPlayerController.getVideoSizeChangedObservable().registerObserver(this);
        mediaPlayerController.getPlaybackTimeUpdateTimeObservable().registerObserver(this);
    }

    private void initPlaylistManager() {
        playlistManager = new PlaylistManager(mediaPlayerController);
        playlistManager.enableLoopPlaylist(true);
        playlistManager.getTracksSetObservable().registerObserver(this);
    }

    private void initScrobblerHelper() {
        scrobblerHelper = ScrobblerHelper.attach(this, mediaPlayerController);
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

        boolean consumed = false;

        switch (item.getItemId()) {
            case R.id.actionAddTrack:
                if (navigationSpinner.getSelectedItemPosition() == 0) {
                    showFileSelectDialog();
                } else {
                    showUrlInputDialog();
                }
                consumed = true;
                break;
            case R.id.actionIsPlaylist:
                item.setChecked(!isActionPlaylistChecked(menu));
                consumed = true;
                break;
            case R.id.actionLooping:
                item.setChecked(!isActionLoopingChecked(menu));
                mediaPlayerController.setLooping(isActionLoopingChecked(menu));
                consumed = true;
                break;
            case R.id.actionEnableScrobbling:
                item.setChecked(!isActionEnableScrobblingChecked(menu));
                if (isActionLoopingChecked(menu)) {
                    scrobblerHelper.enableScrobbling();
                } else {
                    scrobblerHelper.disableScrobbling();
                }
                consumed = true;
                break;
            case R.id.actionQuit:
                finish();
                consumed = true;
                break;
        }

        return consumed || super.onOptionsItemSelected(item);
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

    private boolean isActionLoopingChecked(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.actionLooping);
        return menuItem.isChecked();
    }

    private boolean isActionEnableScrobblingChecked(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.actionEnableScrobbling);
        return menuItem.isChecked();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logger.debug("onCreate()");
        setContentView(R.layout.activity_test);
        initToolbar();
        initSpinner();
        initTrackBar();
        initMediaController();
        initMediaViews();
        initSurfaceView();
        initPlaylistManager();
        initScrobblerHelper();

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

        scrobblerHelper.detach();

        mediaPlayerController.getStateChangedObservable().unregisterObserver(this);
        mediaPlayerController.getErrorObservable().unregisterObserver(this);
        mediaPlayerController.getVideoSizeChangedObservable().unregisterObserver(this);
        mediaPlayerController.getPlaybackTimeUpdateTimeObservable().unregisterObserver(this);
        mediaPlayerController.release();
        mediaPlayerController = null;

        playlistManager.release();
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
    public void onCurrentStateChanged(@NonNull final MediaPlayerController.State currentState, @NonNull MediaPlayerController.State previousState) {
        logger.info("onCurrentStateChanged(), currentState=" + currentState + ", previousState=" + previousState);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidateByCurrentState();
                if (currentState == MediaPlayerController.State.PLAYING || currentState == MediaPlayerController.State.PREPARED || currentState == MediaPlayerController.State.PAUSED) {
                    trackBar.setMax(mediaPlayerController.getDuration());
                    trackBar.setVisibility(View.VISIBLE);
                } else {
                    trackBar.setMax(0);
                    trackBar.setVisibility(View.GONE);
                }
            }
        });

//        if (currentState == MediaPlayerController.State.PLAYING) {
//            Uri uri = mediaPlayerController.isAudioSpecified() ? mediaPlayerController.getAudioUri() : mediaPlayerController.getVideoUri();
//            if (uri != null) {
//                logger.debug("metadata: " + MetadataRetriever.extractMetaData(this, uri, null));
//                Bitmap albumArt = MetadataRetriever.extractAlbumArt(this, new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).appendEncodedPath(uri.getPath()).build());
//                if (albumArt != null) {
//                    albumArt.recycle();
//                }
//            }
//        }
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
                    if (isPlaylist) {
                        if (directory != null && FileHelper.isDirExists(directory.getAbsolutePath())) {
                            lastSelectedDirectory = directory;

                            List<File> folderFiles = FileHelper.getFiles(directory, false, null);
                            List<String> filesList = new ArrayList<>();
                            for (File f : folderFiles) {
                                filesList.add(f.getAbsolutePath());
                            }
                            playlistManager.setTracks(filesList);
                            playlistManager.playFirstTrack();
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

                        if (mediaPlayerController.isAudioSpecified() || mediaPlayerController.isVideoSpecified()) {
                            mediaPlayerController.start();
                            mediaPlayerController.resume();
                        } else {
                            Toast.makeText(TestActivity.this, String.format(getString(R.string.error_incorrect_media_file), file.toString()), Toast.LENGTH_SHORT).show();
                        }

                        hideFileSelectDialog();
                    }
                }
            });

            fileDialog = fd.createFileDialog();
//            ((android.app.AlertDialog) fileDialog).setView(LayoutInflater.from(this).inflate(R.layout.layout_checked_text, null));
            fileDialog.show();
        }
    }

    private void hideFileSelectDialog() {
        if (isFileDialogShowing()) {
            fileDialog.hide();
        }
        fileDialog = null;
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

                            playlistManager.clearTracks();

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
                            playlistManager.setPlayMode(isVideo ? PlaylistManager.PlayMode.VIDEO : PlaylistManager.PlayMode.AUDIO);
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
        }
        urlInputDialog = null;
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        logger.debug("onVideoSizeChanged(), width=" + width + ", height=" + height);
        if (width > 0 && height > 0) {
            Display display = ((WindowManager) (getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            GuiUtils.setViewSize(surfaceView, GuiUtils.getCorrectedSurfaceViewSizeByPreviewSize(this, new Point(width, height), new Point(surfaceView.getMeasuredWidth(), surfaceView.getMeasuredHeight()))); // getSurfaceViewSizeByDisplaySize(this, (float) width / (float) height)
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

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            mediaPlayerController.seekTo(progress);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onPlaybackTimeUpdateTime(int position, int duration) {
        trackBar.setProgress(position);
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
