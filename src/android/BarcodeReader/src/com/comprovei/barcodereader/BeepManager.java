package com.comprovei.barcodereader;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;
import android.util.Log;

import java.io.Closeable;
import java.io.IOException;

/**
 * Manages beeps and vibrations for {@link CaptureActivity}.
 */
final class BeepManager implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, Closeable {
	private static final String TAG = BeepManager.class.getSimpleName();

	private static final float BEEP_VOLUME = 0.10f;
	private static final long VIBRATE_DURATION = 200L;

	private final Activity mActivity;
	private MediaPlayer mMediaPlayer;
	private boolean mPlayBeep;
	private boolean mVibrate;

	BeepManager(Activity activity) {
		this.mActivity = activity;
		this.mMediaPlayer = null;
		updatePrefs();
	}

	synchronized void updatePrefs() {
		this.mPlayBeep = shouldBeep(true, this.mActivity);
		this.mVibrate = true;
		if (this.mPlayBeep && this.mMediaPlayer == null) {
			this.mActivity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			this.mMediaPlayer = buildMediaPlayer(mActivity);
		}
	}

	synchronized void playBeepSoundAndVibrate() {
		if (this.mPlayBeep && this.mMediaPlayer != null) {
			this.mMediaPlayer.start();
		}
		if (this.mVibrate) {
			Vibrator vibrator = (Vibrator) this.mActivity.getSystemService(Context.VIBRATOR_SERVICE);
			vibrator.vibrate(VIBRATE_DURATION);
		}
	}

	private static boolean shouldBeep(boolean shouldPlayBeep, Context activity) {
		if (shouldPlayBeep) {
			AudioManager audioService = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
			if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
				shouldPlayBeep = false;
			}
		}
		return shouldPlayBeep;
	}

	private MediaPlayer buildMediaPlayer(Context activity) {
		MediaPlayer mediaPlayer = new MediaPlayer();
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setOnErrorListener(this);
		try {
			AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
			} finally {
				file.close();
			}
			mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
			mediaPlayer.prepare();
			return mediaPlayer;
		} catch (IOException ioe) {
			Log.w(TAG, ioe);
			mediaPlayer.release();
			return null;
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {     
		mp.seekTo(0);
	}

	@Override
	public synchronized boolean onError(MediaPlayer mp, int what, int extra) {
		if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
			this.mActivity.finish();
		} else {
			mp.release();
			this.mMediaPlayer = null;
			updatePrefs();
		}
		return true;
	}

	@Override
	public synchronized void close() {
		if (this.mMediaPlayer != null) {
			this.mMediaPlayer.release();
			this.mMediaPlayer = null;
		}
	}
}
