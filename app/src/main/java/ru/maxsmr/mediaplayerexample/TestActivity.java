package ru.maxsmr.mediaplayerexample;

import android.app.Dialog;
import android.content.DialogInterface;
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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
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
        mediaPlayerController.setVideoView(surfaceView);
        mediaPlayerController.getStateChangedObservable().registerObserver(this);
        mediaPlayerController.getErrorObservable().registerObserver(this);
        mediaPlayerController.getVideoSizeChangedObservable().registerObserver(this);
    }

    private void initPlaylistManager() {
        playlistManager = new PlaylistManager(mediaPlayerController);
        playlistManager.getTracksSetObservable().registerObserver(this);
    }

    private void initMediaViews() {
        loadingLayout = (LinearLayout) findViewById(R.id.llLoading);
        loadingLayout.setVisibility(View.GONE);
        errorView = (TextView) findViewById(R.id.emptyText);
        errorView.setVisibility(View.GONE);

        GuiUtils.setProgressBarColor(ContextCompat.getColor(this, R.color.progressBarColor), (ProgressBar) loadingLayout.findViewById(R.id.pbLoading));
        FrameLayout contentLayout = (FrameLayout) findViewById(R.id.flContent);

        mediaViews = new MediaController(this);
        mediaViews.setPrevNextListeners(new PreviousClickListener(), new NextClickListener());
        ViewGroup parent = (ViewGroup) mediaViews.getParent();
        parent.removeAllViews();
        contentLayout.addView(mediaViews, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mediaPlayerController.setMediaController(mediaViews, contentLayout);
    }

    private void initSurfaceView() {
        surfaceView = (SurfaceView) findViewById(R.id.svVideo);
        surfaceView.getHolder().addCallback(this);
//        surfaceView.setVisibility(View.GONE);
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
                showEditDialog();
            }
            return true;
        } else if (item.getItemId() == R.id.actionFileSelectMode) {
            item.setChecked(!item.isChecked());
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean isActionFileSelectModeChecked(Menu menu) {
        MenuItem menuItem = menu.findItem(R.id.actionFileSelectMode);
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
        setContentView(R.layout.activity_test);
        initToolbar();
        initSpinner();
        initMediaController();
        initPlaylistManager();
        initSurfaceView();
        initMediaViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayerController.isVideoSpecified() && mediaPlayerController.getTargetState() == MediaPlayerController.State.PLAYING) {
            mediaPlayerController.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mediaPlayerController.isVideoSpecified()) {
            mediaPlayerController.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        hideFileSelectDialog();
        hideEditDialog();

        surfaceView.getHolder().removeCallback(this);

        mediaPlayerController.getStateChangedObservable().unregisterObserver(this);
        mediaPlayerController.getErrorObservable().unregisterObserver(this);
        mediaPlayerController.release();
        mediaPlayerController = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        logger.debug("onKeyDown(), keyCode=" + keyCode + ", event=" + event);

        boolean isKeyCodeSupported =
                keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                        keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                        keyCode != KeyEvent.KEYCODE_MENU &&
                        keyCode != KeyEvent.KEYCODE_CALL &&
                        keyCode != KeyEvent.KEYCODE_ENDCALL;

        if (mediaPlayerController.isInPlaybackState() && isKeyCodeSupported && mediaPlayerController.isMediaControllerAttached()) {

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
            logger.debug("metadata: " + MetadataRetriever.extractMetaData(this, mediaPlayerController.getAudioUri(), null));
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
            mediaViews.setVisibility(View.GONE);
        } else {
            loadingLayout.setVisibility(View.GONE);
            mediaViews.setVisibility(View.VISIBLE);
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

            final boolean fileOrFolders = isActionFileSelectModeChecked(menu);

            final FileDialog fd = new FileDialog(this);
            fd.setShowDirectoryOnly(!fileOrFolders);
            fd.setFileSortedBy(FileDialog.SORTED_BY_NAME);
            fd.initDirectory(lastSelectedDirectory != null && FileHelper.isDirExists(lastSelectedDirectory.getAbsolutePath()) ? lastSelectedDirectory.getAbsolutePath() : Environment.getExternalStorageDirectory().toString());
            fd.addDirectoryListener(new FileDialog.DirSelectedListener() {
                @Override
                public void directorySelected(File directory, String[] dirs, String[] files) {
                    if (directory != null && FileHelper.isDirExists(directory.getAbsolutePath())) {
                        lastSelectedDirectory = directory;

                            if (!fileOrFolders) {
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

                    if (fileOrFolders) {

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

            fileDialog = fd.createFileDialog();
            fileDialog.show();
        }
    }

    private void hideFileSelectDialog() {
        if (isFileDialogShowing()) {
            fileDialog.hide();
            fileDialog = null;
        }
    }

    private Dialog editDialog;

    private boolean isEditDialogShowing() {
        return editDialog != null && editDialog.isShowing();
    }

    private void showEditDialog() {
        if (!isEditDialogShowing()) {

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

                        if (!choiceView.isChecked()) {
                            mediaPlayerController.setAudioPath(url);
//                            surfaceView.setVisibility(View.GONE);
                        } else {
                            mediaPlayerController.setVideoPath(url);
//                            surfaceView.setVisibility(View.VISIBLE);
                        }
                        mediaPlayerController.start();
                        mediaPlayerController.resume();
                    }

                }
            }).show();
        }
    }

    private void hideEditDialog() {
        if (isEditDialogShowing()) {
            editDialog.hide();
            editDialog = null;
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
        GuiUtils.setViewSize(surfaceView, GuiUtils.getSurfaceViewSizeByDisplaySize(this, (float) width / (float) height));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

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