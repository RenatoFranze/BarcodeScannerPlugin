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
	private static final float FAILBEEP_VOLUME = 10*BEEP_VOLUME;
	private static final long VIBRATE_DURATION = 200L;

	private final Activity mActivity;
	private MediaPlayer mMediaPlayerBeep;
	private MediaPlayer mMediaPlayerFailBeep;
	private boolean mPlayBeep;
	private boolean mVibrate;

	BeepManager(Activity activity) {
		this.mActivity = activity;
		this.mMediaPlayerBeep = null;
		this.mMediaPlayerFailBeep = null;
		updatePrefs();
	}

	synchronized void updatePrefs() {
		this.mPlayBeep = shouldBeep(true, this.mActivity);
		this.mVibrate = true;
		if (this.mPlayBeep) {
			this.mActivity.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			if(this.mMediaPlayerBeep == null){
				this.mMediaPlayerBeep = buildMediaPlayer(mActivity, R.raw.beep);				
			}
			if(this.mMediaPlayerFailBeep == null){
				this.mMediaPlayerFailBeep = buildMediaPlayer(mActivity, R.raw.failbeep);					
			}	
		}
	}

	synchronized void playBeepSoundAndVibrate(int soundID) {
		if (this.mPlayBeep) {
			switch (soundID) {
			case R.raw.beep:
				if(this.mMediaPlayerBeep != null){
					this.mMediaPlayerBeep.start();					
				}
				break;
			case R.raw.failbeep:
				if(this.mMediaPlayerFailBeep != null){
					this.mMediaPlayerFailBeep.start();					
				}
				break;
			}
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

	private MediaPlayer buildMediaPlayer(Context activity, int soundID) {
		MediaPlayer mediaPlayer = new MediaPlayer();
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setOnErrorListener(this);
		try {
			AssetFileDescriptor file = activity.getResources().openRawResourceFd(soundID);
			try {
				mediaPlayer.setDataSource(file.getFileDescriptor(), file.getStartOffset(), file.getLength());
			} finally {
				file.close();
			}
			
			switch (soundID) {
			case R.raw.beep:
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);				
				break;
			case R.raw.failbeep:
				mediaPlayer.setVolume(FAILBEEP_VOLUME, FAILBEEP_VOLUME);	
				break;
			}
			
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
			this.mMediaPlayerBeep = null;
			this.mMediaPlayerFailBeep = null;
			updatePrefs();
		}
		return true;
	}

	@Override
	public synchronized void close() {
		if (this.mMediaPlayerBeep != null) {
			this.mMediaPlayerBeep.release();
			this.mMediaPlayerBeep = null;
		}
		if (this.mMediaPlayerFailBeep != null) {
			this.mMediaPlayerFailBeep.release();
			this.mMediaPlayerFailBeep = null;
		}
	}
}
