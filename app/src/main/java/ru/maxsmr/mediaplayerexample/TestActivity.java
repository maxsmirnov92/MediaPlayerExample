package ru.maxsmr.mediaplayerexample;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
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
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
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

import net.maxsmr.commonutils.android.gui.GuiUtils;
import net.maxsmr.commonutils.data.FileHelper;
import net.maxsmr.commonutils.logger.BaseLogger;
import net.maxsmr.commonutils.logger.holder.BaseLoggerHolder;
import net.maxsmr.mediaplayercontroller.ScrobblerHelper;
import net.maxsmr.mediaplayercontroller.facades.MediaPlayerFacade;
import net.maxsmr.mediaplayercontroller.facades.PlaylistManagerFacade;
import net.maxsmr.mediaplayercontroller.mpc.BaseMediaPlayerController;
import net.maxsmr.mediaplayercontroller.mpc.nativeplayer.MediaPlayerController;
import net.maxsmr.mediaplayercontroller.playlist.PlaylistManager;
import net.maxsmr.mediaplayercontroller.playlist.item.AbsPlaylistItem;
import net.maxsmr.mediaplayercontroller.playlist.item.UriPlaylistItem;

import org.ngweb.android.api.filedialog.FileDialog;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ru.maxsmr.mediaplayerexample.app.MediaPlayerExampleApp;

public class TestActivity extends AppCompatActivity implements SurfaceHolder.Callback, MediaPlayerController.OnStateChangedListener, MediaPlayerController.OnErrorListener<MediaPlayerController.MediaError>, MediaPlayerController.OnVideoSizeChangedListener, MediaPlayerController.OnPlaybackTimeUpdateTimeListener, PlaylistManager.OnTracksSetListener<UriPlaylistItem>, SeekBar.OnSeekBarChangeListener {

    private static final BaseLogger logger = BaseLoggerHolder.getInstance().getLogger(TestActivity.class);

    private MediaPlayerController mediaPlayerController;
    private PlaylistManager<MediaPlayerController, UriPlaylistItem> playlistManager;
    private ScrobblerHelper scrobblerHelper;

    private Spinner navigationSpinner;

    private SeekBar trackBar;

    private LinearLayout loadingLayout;
    private TextView errorView;

    private SurfaceView surfaceView;
    private MediaController mediaViews;

    private String lastUrl;
    private BaseMediaPlayerController.PlayMode lastPlayModeChoice;

    private Menu menu;

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
        navigationSpinner = findViewById(R.id.action_bar_spinner);
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
        mediaPlayerController = MediaPlayerFacade.getInstance().create("mpc_test");
        mediaPlayerController.setPrepareResetTimeoutMs(20000);
        mediaPlayerController.setNotifyPlaybackTimeInterval(200);
        mediaPlayerController.getStateChangedObservable().registerObserver(this);
        mediaPlayerController.getErrorObservable().registerObserver(this);
        mediaPlayerController.getVideoSizeChangedObservable().registerObserver(this);
        mediaPlayerController.getPlaybackTimeUpdateTimeObservable().registerObserver(this);
    }

    private void initPlaylistManager() {
        playlistManager = PlaylistManagerFacade.getInstance().get(MediaPlayerExampleApp.PLAYER_ALIAS);
        if (playlistManager == null) {
            throw new RuntimeException("playlistManager is not created");
        }
        playlistManager.setLoopPlaylist(true);
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
        logger.d("onCreate()");
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
        logger.d("onResume()");
        if (mediaPlayerController.isVideoSpecified() && mediaPlayerController.getTargetState() == MediaPlayerController.State.PLAYING) {
            mediaPlayerController.start();
        }
        invalidateByCurrentState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        logger.d("onStop()");
        if (mediaPlayerController.isVideoSpecified()) {
            mediaPlayerController.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        logger.d("onDestroy()");

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

        if (isFinishing()) {
            playlistManager.release();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        logger.d("onKeyDown(), keyCode=" + keyCode + ", event=" + event);

        boolean isKeyCodeSupported =
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
                    if (mediaController != null) {
                        mediaController.show();
                    }
                } else {
                    mediaPlayerController.start();
                    if (mediaController != null) {
                        mediaController.hide();
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mediaPlayerController.isPlaying()) {
                    mediaPlayerController.start();
                    if (mediaController != null) {
                        mediaController.hide();
                    }
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mediaPlayerController.isPlaying()) {
                    mediaPlayerController.pause();
                    if (mediaController != null) {
                        mediaController.show();
                    }
                }
                return true;
            } else {
                mediaPlayerController.toggleMediaControlsVisibility();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBeforeOpenDataSource() {

    }

    @Override
    public void onCurrentStateChanged(@NonNull final MediaPlayerController.State currentState, @NonNull MediaPlayerController.State previousState) {
        logger.i("onCurrentStateChanged(), currentState=" + currentState + ", previousState=" + previousState);
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
//                logger.d("metadata: " + MetadataRetriever.extractMetaData(this, uri, null));
//                Bitmap albumArt = MetadataRetriever.extractAlbumArt(this, new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).appendEncodedPath(uri.getPath()).build());
//                if (albumArt != null) {
//                    albumArt.recycle();
//                }
//            }
//        }
    }

    @Override
    public void onTargetStateChanged(@NonNull MediaPlayerController.State targetState) {
        logger.i("onTargetStateChanged(), targetState=" + targetState);
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

                            Set<File> folderFiles = FileHelper.getFiles(directory, FileHelper.GetMode.FILES, new FileHelper.FileComparator(Collections.singletonMap(FileHelper.FileComparator.SortOption.NAME, true)), null, FileHelper.DEPTH_UNLIMITED);
                            List<UriPlaylistItem> playlistItems = new ArrayList<>();
                            for (File f : folderFiles) {
                                playlistItems.add(new UriPlaylistItem(BaseMediaPlayerController.PlayMode.VIDEO, UriPlaylistItem.DURATION_NOT_SPECIFIED, isActionLoopingChecked(menu), f.getAbsolutePath()));
                            }
                            playlistManager.setTracks(playlistItems);
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
                        logger.d("mimeType=" + mimeType);

                        if (mimeType.contains("audio")) {
                            mediaPlayerController.setContentUri(BaseMediaPlayerController.PlayMode.AUDIO, Uri.fromFile(file));
                        } else if (mimeType.contains("video")) {
                            mediaPlayerController.setContentUri(BaseMediaPlayerController.PlayMode.VIDEO, Uri.fromFile(file));
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

    private AlertDialog urlInputDialog;

    private boolean isUrlInputDialogShowing() {
        return urlInputDialog != null && urlInputDialog.isShowing();
    }

    private void showUrlInputDialog() {
        if (!isUrlInputDialogShowing()) {

            final View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_url, null);

            final EditText urlView = (EditText) dialogView.findViewById(R.id.etUrl);
            urlView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isUrlInputDialogShowing()) {
                        urlInputDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s));
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            });

            final CheckedTextView choiceView = (CheckedTextView) dialogView.findViewById(R.id.tvUrlChoice);
            choiceView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    choiceView.toggle();
                    lastPlayModeChoice = choiceView.isChecked() ? BaseMediaPlayerController.PlayMode.VIDEO : BaseMediaPlayerController.PlayMode.AUDIO;
                }
            });
            // TODO checked changed listener

            if (lastPlayModeChoice == null || BuildConfig.URL_TYPE_TO_USE.equalsIgnoreCase("video")) {
                urlView.setText(BuildConfig.VIDEO_URL);
                choiceView.setChecked(true);
            } else {
                urlView.setText(BuildConfig.AUDIO_URL);
                choiceView.setChecked(false);
            }

            AlertDialog.Builder b = new AlertDialog.Builder(this);
            urlInputDialog = b.setCancelable(true).setTitle(R.string.dialog_url_title).setView(dialogView).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();

                    lastUrl = urlView.getText().toString().trim();

                    if (!TextUtils.isEmpty(lastUrl)) {

                        if (!lastUrl.contains("://")) {
                            lastUrl = "http://" + lastUrl;
                        }

                        try {
                            new URL(lastUrl);
                        } catch (MalformedURLException e) {
                            Toast.makeText(TestActivity.this, R.string.error_url_incorrect, Toast.LENGTH_SHORT).show();
                            return;
                        }

                        final boolean isPlaylist = isActionPlaylistChecked(menu);
                        final boolean isVideo = choiceView.isChecked();

                        if (!isPlaylist) {

                            playlistManager.clearTracks();

                            if (!isVideo) {
                                mediaPlayerController.setContentUri(BaseMediaPlayerController.PlayMode.AUDIO, Uri.parse(lastUrl));
//                            surfaceView.setVisibility(View.GONE);
                            } else {
                                mediaPlayerController.setContentUri(BaseMediaPlayerController.PlayMode.VIDEO, Uri.parse(lastUrl));
//                            surfaceView.setVisibility(View.VISIBLE);
                            }
                            mediaPlayerController.start();
                            mediaPlayerController.resume();

                        } else {
//                            if (isVideo != (playlistManager.getPlayMode() == PlaylistManager.PlayMode.VIDEO)) {
//                                playlistManager.clearTracks();
//                            }
                            playlistManager.addTrack(new UriPlaylistItem(isVideo ? BaseMediaPlayerController.PlayMode.VIDEO : BaseMediaPlayerController.PlayMode.AUDIO,
                                    AbsPlaylistItem.DURATION_NOT_SPECIFIED,
                                    isActionLoopingChecked(menu),
                                    lastUrl
                            ));
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
        logger.d("onVideoSizeChanged(), width=" + width + ", height=" + height);
        if (width > 0 && height > 0) {
            Display display = ((WindowManager) (getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            display.getMetrics(displayMetrics);
            GuiUtils.setViewSize(surfaceView,
                    getCorrectedSurfaceViewSizeByPreviewSize(
                            new Point(surfaceView.getMeasuredWidth(), surfaceView.getMeasuredHeight()),
                            new Point(width, height),
                            getResources().getConfiguration().orientation)); // getSurfaceViewSizeByDisplaySize(this, (float) width / (float) height)
        } else {
            GuiUtils.setViewSize(surfaceView, new Point(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)); // TODO
        }
    }

    public static Point getCorrectedSurfaceViewSizeByPreviewSize(@NonNull Point viewSize, @NonNull Point previewSize, int orientation) {
        float videoScale = (float) previewSize.x / (float) previewSize.y;
        final int targetWidth;
        final int targetHeight;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            targetWidth = viewSize.x;
            targetHeight = (int) (targetWidth / videoScale);
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            targetHeight = viewSize.y;
            targetWidth = (int) (videoScale * targetHeight);
        } else {
            throw new IllegalArgumentException("incorrect orientation: " + orientation);
        }
        return new Point(targetWidth, targetHeight);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        logger.d("surfaceCreated()");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        logger.d("surfaceChanged(), format=" + format + ", width=" + width + ", height=" + height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        logger.d("surfaceDestroyed()");
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

    @Override
    public void onError(@NonNull MediaPlayerController.MediaError error) {
        logger.e("onError(), error=" + error);
        processError();
    }

    @Override
    public void onTracksSet(@NonNull List<UriPlaylistItem> newTracks) {

    }

    @Override
    public void onTracksNotSet(@NonNull List<UriPlaylistItem> incorrectTracks) {

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
