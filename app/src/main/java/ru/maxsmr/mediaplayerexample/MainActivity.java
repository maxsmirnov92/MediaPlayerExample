package ru.maxsmr.mediaplayerexample;

import android.app.Dialog;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.MediaController;

import org.ngweb.android.api.filedialog.FileDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import ru.altarix.commonutils.data.FileHelper;
import ru.maxsmr.mediaplayercontroller.MediaPlayerController;

public class MainActivity extends AppCompatActivity implements MediaPlayerController.OnStateChangedListener {

    private static final Logger logger = LoggerFactory.getLogger(MainActivity.class);

    private MediaPlayerController mediaPlayerController;

    private FrameLayout loadingLayout;

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

    private void initMediaController() {
        loadingLayout = (FrameLayout) findViewById(R.id.flLoading);
        loadingLayout.setVisibility(View.GONE);
        FrameLayout contentLayout = (FrameLayout) findViewById(R.id.flContent);
        MediaController mediaController = new MediaController(this, true);
        ViewGroup parent = (ViewGroup) mediaController.getParent();
        parent.removeAllViews();
        contentLayout.addView(mediaController, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mediaPlayerController = new MediaPlayerController(this);
        mediaPlayerController.setMediaController(mediaController, contentLayout);
        mediaPlayerController.getStateChangedObservable().registerObserver(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initToolbar();
        initMediaController();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.actionSelectTrack) {
            showFolderSelectDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        hideFolderSelectDialog();

        mediaPlayerController.getStateChangedObservable().unregisterObserver(this);
        mediaPlayerController.release();
        mediaPlayerController = null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
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
        if (currentState == MediaPlayerController.State.PREPARING) {
            loadingLayout.setVisibility(View.VISIBLE);
        } else {
            loadingLayout.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTargetStateChanged(@NonNull MediaPlayerController.State targetState) {
        logger.info("onTargetStateChanged(), targetState=" + targetState);
    }


    private File lastSelectedDirectory;
    private Dialog fileDialog;

    private boolean isFileDialogShowing() {
        return fileDialog != null && fileDialog.isShowing();
    }

    private void showFolderSelectDialog() {
        if (!isFileDialogShowing()) {
            FileDialog fd = new FileDialog(this);
            fd.setShowDirectoryOnly(false);
            fd.setFileSortedBy(FileDialog.SORTED_BY_NAME);
            fd.initDirectory(lastSelectedDirectory != null && FileHelper.isDirExists(lastSelectedDirectory.getAbsolutePath()) ? lastSelectedDirectory.getAbsolutePath() : Environment.getExternalStorageDirectory().toString());
            fd.addDirectoryListener(new FileDialog.DirSelectedListener() {
                @Override
                public void directorySelected(File directory, String[] dirs, String[] files) {
                    if (directory != null && FileHelper.isDirExists(directory.getAbsolutePath())) {
                        lastSelectedDirectory = directory;
                    }
                }
            });
            fd.addFileListener(new FileDialog.FileSelectedListener() {
                @Override
                public void fileSelected(File file, String[] strings, String[] strings1) {
                    mediaPlayerController.setAudioFile(file);
                    mediaPlayerController.start();
                    mediaPlayerController.resume();
                    hideFolderSelectDialog();
                }
            });

            fileDialog = fd.createFileDialog();
            fileDialog.show();
        }
    }

    private void hideFolderSelectDialog() {
        if (isFileDialogShowing()) {
            fileDialog.hide();
            fileDialog = null;
        }
    }
}
