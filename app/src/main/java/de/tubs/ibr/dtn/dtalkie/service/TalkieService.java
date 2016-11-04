/*
 * DTalkieService.java
 * 
 * Copyright (C) 2011 IBR, TU Braunschweig
 *
 * Written-by: Johannes Morgenroth <morgenroth@ibr.cs.tu-bs.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package de.tubs.ibr.dtn.dtalkie.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.WearableExtender;
import android.util.Log;
import de.tubs.ibr.dtn.api.Block;
import de.tubs.ibr.dtn.api.Bundle;
import de.tubs.ibr.dtn.api.Bundle.ProcFlags;
import de.tubs.ibr.dtn.api.BundleID;
import de.tubs.ibr.dtn.api.DTNClient;
import de.tubs.ibr.dtn.api.DTNClient.Session;
import de.tubs.ibr.dtn.api.DTNIntentService;
import de.tubs.ibr.dtn.api.DataHandler;
import de.tubs.ibr.dtn.api.EID;
import de.tubs.ibr.dtn.api.Registration;
import de.tubs.ibr.dtn.api.ServiceNotAvailableException;
import de.tubs.ibr.dtn.api.SessionDestroyedException;
import de.tubs.ibr.dtn.api.TransferMode;
import de.tubs.ibr.dtn.dtalkie.R;
import de.tubs.ibr.dtn.dtalkie.TalkieActivity;
import de.tubs.ibr.dtn.dtalkie.db.Message;
import de.tubs.ibr.dtn.dtalkie.db.MessageDatabase;
import de.tubs.ibr.dtn.dtalkie.db.MessageDatabase.Folder;

public class TalkieService extends DTNIntentService {
	
	private static final String TAG = "TalkieService";

	public static final String ACTION_RECORDED = "de.tubs.ibr.dtn.dtalkie.RECORDED";
	public static final String ACTION_PLAY = "de.tubs.ibr.dtn.dtalkie.PLAY";
	public static final String ACTION_STOP = "de.tubs.ibr.dtn.dtalkie.STOP";
	public static final String ACTION_PLAY_ALL = "de.tubs.ibr.dtn.dtalkie.PLAY_ALL";
	public static final String ACTION_PLAY_NEXT = "de.tubs.ibr.dtn.dtalkie.PLAY_NEXT";
	public static final String ACTION_PLAYING_STATE = "de.tubs.ibr.dtn.dtalkie.PLAYING_STATE";
	
	public static final String EXTRA_PLAYING = "de.tubs.ibr.dtn.dtalkie.PLAYING";
	
	private static final String ACTION_RECEIVED = "de.tubs.ibr.dtn.dtalkie.RECEIVED";
	private static final String ACTION_MARK_DELIVERED = "de.tubs.ibr.dtn.dtalkie.MARK_DELIVERED";
	
	private static final int MESSAGE_NOTIFICATION = 1;
	
	private ServiceError mServiceError = ServiceError.NO_ERROR;
	
	private MessageDatabase mDatabase = null;
	
	private MediaPlayer mPlayer = null;
	private boolean mPaused = false;
	private LinkedList<Message> mPlayQueue = new LinkedList<Message>();
	
	private SoundFXManager mSoundManager = null;
	private NotificationManager mNotificationManager = null;
	
	private AudioManager mAudioManager = null;
	
    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
    
    // basic dtn client
    private DTNClient.Session mSession = null;
    
    public TalkieService() {
        super(TAG);
    }
	
	@Override
	public void onSessionConnected(Session session) {
		Log.d(TAG, "DTN session connected");
		
		// register own data handler for incoming bundles
		session.setDataHandler(_data_handler);
		
		mSession = session;
	}

	@Override
	public void onSessionDisconnected() {
		mSession = null;
	}

    private DataHandler _data_handler = new DataHandler()
    {
    	private de.tubs.ibr.dtn.api.Bundle bundle = null;
		private File file = null;
		private ParcelFileDescriptor fd = null;

		public void startBundle(de.tubs.ibr.dtn.api.Bundle bundle) {
			this.bundle = bundle;
		}

		public void endBundle() {
			if (file != null)
			{
				Log.i(TAG, "New message received.");

				// process the file using the service
				Intent recv_intent = new Intent(TalkieService.this, TalkieService.class);
				recv_intent.setAction(ACTION_RECEIVED);
				recv_intent.putExtra("source", bundle.getSource().toString());
				recv_intent.putExtra("destination", bundle.getDestination().toString());

				if (bundle.getTimestamp().isValid()) {
					recv_intent.putExtra("created", (Serializable)bundle.getTimestamp().getDate());
				} else {
					recv_intent.putExtra("created", (Serializable)new Date());
				}
				
				recv_intent.putExtra("received", (Serializable)new Date());
				recv_intent.putExtra("file", (Serializable)file);
				startService(recv_intent);

				// mark the bundle as delivered
				Intent delivered_intent = new Intent(TalkieService.this, TalkieService.class);
				delivered_intent.setAction(ACTION_MARK_DELIVERED);
				delivered_intent.putExtra("bundleid", new BundleID(bundle));
				startService(delivered_intent);

				file = null;
			}
			
			bundle = null;
		}

		public TransferMode startBlock(Block block) {
			if ((block.type == 1) && (file == null))
			{
				//File folder = Utils.getStoragePath(TalkieService.this);
				File folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
				// create a new temporary file
				try {
					file = File.createTempFile("msg", ".jpg", folder);
					return TransferMode.FILEDESCRIPTOR;
				} catch (IOException e) {
					Log.e(TAG, "Can not create temporary file.", e);
					file = null;
				}
			}
			return TransferMode.NULL;
		}

		public void endBlock() {
			if (fd != null)
			{
				// close filedescriptor
				try {
					fd.close();
					fd = null;
				} catch (IOException e) {
					Log.e(TAG, "Can not close filedescriptor.", e);
				}
			}
			
			if (file != null)
			{
				// unset the payload file
				Log.i(TAG, "File received: " + file.getAbsolutePath());

			}
		}

		public void payload(byte[] data) {
			// nothing to do here, since we using FILEDESCRIPTOR mode
		}

		public ParcelFileDescriptor fd() {
			// create new filedescriptor
			try {
				fd = ParcelFileDescriptor.open(file, 
						ParcelFileDescriptor.MODE_CREATE + 
						ParcelFileDescriptor.MODE_READ_WRITE);
				
				return fd;
			} catch (FileNotFoundException e) {
				Log.e(TAG, "Can not create a filedescriptor.", e);
			}
			
			return null;
		}

		public void progress(long current, long length) {
			Log.i(TAG, "Payload: " + current + " of " + length + " bytes.");
		}
    };
    
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
    	public TalkieService getService() {
            return TalkieService.this;
        }
    }

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate()
	{
		// call onCreate of the super-class
		super.onCreate();
		
		// register custom ring-tone
		try {
			Utils.registerNotificationSound(this);
		} catch (IOException e1) {
			Log.e(TAG, "failed to install notification sound", e1);
		}
		
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		
        // get the audio-manager
        mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mPrefListener);
		
		// create message database
		mDatabase = new MessageDatabase(this);
		
        // init sound pool
        mSoundManager = new SoundFXManager(AudioManager.STREAM_MUSIC, 2);
        
        mSoundManager.load(this, Sound.BEEP);
        mSoundManager.load(this, Sound.CONFIRM);
        mSoundManager.load(this, Sound.QUIT);
        mSoundManager.load(this, Sound.RING);
        mSoundManager.load(this, Sound.SQUELSH_LONG);
        mSoundManager.load(this, Sound.SQUELSH_SHORT);
        
        // create a player
        mPlayer = new MediaPlayer();
        mPlayer.setOnCompletionListener(mCompletionListener);
        mPlayer.setOnPreparedListener(mPrepareListener);
		
		// create registration
    	Registration reg = new Registration("dtalkie");
    	reg.add(RecorderService.TALKIE_GROUP_EID);
		
		try {
		    initialize(reg);
			mServiceError = ServiceError.NO_ERROR;
		} catch (ServiceNotAvailableException e) {
		    mServiceError = ServiceError.SERVICE_NOT_FOUND;
		} catch (SecurityException ex) {
		    mServiceError = ServiceError.PERMISSION_NOT_GRANTED;
		}
		
		Log.d(TAG, "Service created.");
		
		// enqueue the next audio file to play
		Intent play_i = new Intent(TalkieService.this, TalkieService.class);
		play_i.setAction(TalkieService.ACTION_PLAY_NEXT);
		startService(play_i);
	}
	
	public ServiceError getServiceError() {
		return mServiceError;
	}
	
	@Override
	public void onDestroy()
	{
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(mPrefListener);
        
	    mPlayer.release();
	    
	    // close the database
	    mDatabase.close();
	    
	    // free sound manager resources
	    mSoundManager.release();
		
		super.onDestroy();
		Log.d(TAG, "Service destroyed.");
	}
	
	public MessageDatabase getDatabase() {
		return mDatabase;
	}
	
	private void playSound(Sound s) {
		mSoundManager.play(this, s);
	}
	
	OnAudioFocusChangeListener mAudioFocusChangeListener = new OnAudioFocusChangeListener() {
		@Override
		public void onAudioFocusChange(int focusChange) {
			// AudioFocus changed
			Log.d(TAG, "Audio Focus changed to " + focusChange);

			try {
				if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
					// focus lost
					if (mPlayer.isPlaying()) {
						mPlayer.pause();
						mPaused = true;
					}
				} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
					// focus lost
					if (mPlayer.isPlaying()) {
						mPlayer.pause();
						mPaused = true;
					}
				} else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
					// focus returned
					if (mPaused) {
						mPlayer.start();
						mPaused = false;
					}
				} else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
					if (mPlayer.isPlaying()) {
						mPlayer.stop();
					}
				}
			} catch (IllegalStateException e) {
				Log.e(TAG, "Error while handling audio focus.", e);
			}
		}
	};

	private MediaPlayer.OnPreparedListener mPrepareListener = new MediaPlayer.OnPreparedListener() {
		public void onPrepared(MediaPlayer mp) {
			// request audio focus
			int result = mAudioManager.requestAudioFocus(mAudioFocusChangeListener,
					AudioManager.STREAM_MUSIC,
					// Request transient focus.
					AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);

			if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
				// focus not granted
				return;
			}
			
			Intent state_i = new Intent(ACTION_PLAYING_STATE);
			state_i.putExtra(EXTRA_PLAYING, true);
			sendBroadcast(state_i);

			playSound(Sound.BEEP);
			mp.start();
		}
	};

    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            playSound(Sound.CONFIRM);
            
            // return audio focus
            mAudioManager.abandonAudioFocus(mAudioFocusChangeListener);
            
            // reset the player
            mp.reset();
            
            // remove element from queue
            Message msg = mPlayQueue.pop();
            
            // mark the message as played
            mDatabase.mark(Folder.INBOX, msg.getId(), true);
            
            // remove notification if there are no more pending
            // messages
            removeNotification();
            
            if (mPlayQueue.isEmpty()) {
                // enqueue the next audio file to play
                Intent play_i = new Intent(TalkieService.this, TalkieService.class);
                play_i.setAction(ACTION_PLAY_NEXT);
                startService(play_i);
            } else {
                Message nextMessage = mPlayQueue.peek();
                play(nextMessage);
            }
        }
    };

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        
        if (de.tubs.ibr.dtn.Intent.RECEIVE.equals(action)) {

            try {
                while (mSession.queryNext());
            } catch (Exception e) { };
        }
        else if (ACTION_MARK_DELIVERED.equals(action)) {
            final BundleID received = intent.getParcelableExtra("bundleid");
            
            try {
                mSession.delivered(received);
            } catch (Exception e) {
                Log.e(TAG, "Can not mark bundle as delivered.", e);
            }
        }
        else if (ACTION_PLAY_ALL.equals(action)) {
        	List<Long> msgs = mDatabase.getMarkedMessages(Folder.INBOX, false);
        	
        	for (Long id : msgs) {
                Intent play_i = new Intent(this, TalkieService.class);
                play_i.setAction(TalkieService.ACTION_PLAY);
                play_i.putExtra("folder", Folder.INBOX.toString());
                play_i.putExtra("message", id);
                startService(play_i);
        	}
        }
        else if (ACTION_PLAY.equals(action)) {
            Folder f = Folder.valueOf( intent.getStringExtra("folder") );
            Long msgid = intent.getLongExtra("message", 0L);
            
            // unmark the message
            mDatabase.mark(f, msgid, false);

            // prepare player
            Message msg = mDatabase.get(f, msgid);
            
            if (mPlayQueue.isEmpty()) {
                // enqueue the message
                mPlayQueue.offer(msg);
                
                // play-back the message
                play(msg);
                
                // prevent service from terminating
                setOngoing(true);
            } else {
                // enqueue the message
                mPlayQueue.offer(msg);
            }
        }
        else if (ACTION_PLAY_NEXT.equals(action)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TalkieService.this);
            if (prefs.getBoolean("autoplay", false) || HeadsetService.ENABLED) {
                Message next = mDatabase.nextMarked(Folder.INBOX, false);
                
                if (next != null) {
                    Intent play_i = new Intent(TalkieService.this, TalkieService.class);
                    play_i.setAction(TalkieService.ACTION_PLAY);
                    play_i.putExtra("folder", Folder.INBOX.toString());
                    play_i.putExtra("message", next.getId());
                    startService(play_i);
                    return;
                }
            }
            
            Intent state_i = new Intent(ACTION_PLAYING_STATE);
            state_i.putExtra(EXTRA_PLAYING, false);
            sendBroadcast(state_i);
            
            // allow service to get terminated
            setOngoing(false);
        }
        else if (ACTION_STOP.equals(action)) {
            if (mPlayer.isPlaying()) {
                // stop the player
                mPlayer.stop();
                mPlayer.reset();
                
                // clear the queue
                mPlayQueue.clear();
            }
            
            Intent state_i = new Intent(ACTION_PLAYING_STATE);
            state_i.putExtra(EXTRA_PLAYING, false);
            sendBroadcast(state_i);
            
            // allow service to get terminated
            setOngoing(false);
        }
        else if (ACTION_RECORDED.equals(action)) {
            File recfile = (File)intent.getSerializableExtra("recfile");


			//File recfile = new File("/sdcard/Music/m_BlueforLabrusca.mp3");
            // create a new bundle
            Bundle b = new Bundle();
            
            // set destination
            b.setDestination((EID) intent.getSerializableExtra("destination"));
            
            // assign lifetime
            b.setLifetime(1800L);
            
            // request signing of the message
            b.set(ProcFlags.DTNSEC_REQUEST_SIGN, true);
            
            // request encryption of the message
            b.set(ProcFlags.DTNSEC_REQUEST_ENCRYPT, true);
            
            try {
                ParcelFileDescriptor fd = ParcelFileDescriptor.open(recfile, ParcelFileDescriptor.MODE_READ_ONLY);
                BundleID ret = mSession.send(b, fd);
                
                if (ret == null)
                {
                    Log.e(TAG, "Recording sent failed");
                }
                else
                {
                    Log.i(TAG, "Recording sent, BundleID: " + ret.toString());
                }
                
                //recfile.delete();
            } catch (FileNotFoundException ex) {
                Log.e(TAG, "Can not open message file for transmission", ex);
            } catch (SessionDestroyedException ex) {
                Log.e(TAG, "DTN session has been destroyed.", ex);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected exception while transmit message.", e);
            }
        }
        else if (ACTION_RECEIVED.equals(action)) {
            String source = intent.getStringExtra("source");
            String destination = intent.getStringExtra("destination");
            Date created = (Date)intent.getSerializableExtra("created");
            Date received = (Date)intent.getSerializableExtra("received");
            File file = (File)intent.getSerializableExtra("file");
            
            // add message to database
            Message msg = new Message(source, destination, file);
            msg.setCreated(created);
            msg.setReceived(received);
            mDatabase.put(Folder.INBOX, msg);
            
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            if (prefs.getBoolean("notification", true)) {
	            // create a notification for this message
	            createNotification();
            }

            Intent play_i = new Intent(TalkieService.this, TalkieService.class);
            play_i.setAction(ACTION_PLAY_NEXT);
            startService(play_i);
        }
    }
    
    public OnSharedPreferenceChangeListener mPrefListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if ("autoplay".equals(key)) {
                // enqueue the next audio file to play
                Intent play_i = new Intent(TalkieService.this, TalkieService.class);
                play_i.setAction(TalkieService.ACTION_PLAY_NEXT);
                startService(play_i);
            }
        }
    };
    
    private void play(Message msg) {
        try {
            // prepare player
            if (msg != null) {
                mPlayer.setDataSource(msg.getFile().getAbsolutePath());
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mPlayer.prepareAsync();
            }
        } catch (IOException e) {
            Log.e(TAG, null, e);
        }
    }
    
    private void removeNotification() {
        // get the number of marked messages
        int mcount = mDatabase.getMarkedMessageCount(Folder.INBOX, false);
        
        if (mcount == 0) {
        	mNotificationManager.cancel(MESSAGE_NOTIFICATION);
        }
    }
    
    private void createNotification()
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // do not add notifications if autoplay is active
        if (prefs.getBoolean("autoplay", false) || HeadsetService.ENABLED) return;
        
        // get the number of marked messages
        int mcount = mDatabase.getMarkedMessageCount(Folder.INBOX, false);
        
        // do not add a notification if there is no unread message
        if (mcount <= 0) return;
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        // enable auto-cancel
        builder.setAutoCancel(true);
        
        int defaults = 0;
        
        if (prefs.getBoolean("vibrateOnMessage", true)) {
            defaults |= Notification.DEFAULT_VIBRATE;
        }
        
        // create a play all unread messages intent
        Intent play_i = new Intent(this, TalkieService.class);
        play_i.setAction(TalkieService.ACTION_PLAY_ALL);
        play_i.putExtra("folder", Folder.INBOX.toString());
        
        PendingIntent playPendingIntent = PendingIntent.getService(this, 0, play_i, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Action action = new NotificationCompat.Action.Builder(R.drawable.ic_wear_play, getString(R.string.play_label), playPendingIntent).build();
        
        /** CREATE AN INTENT TO OPEN THE MESSAGES ACTIVITY **/
        Intent resultIntent = new Intent(this, TalkieActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        
        builder.setNumber(mcount);
        builder.setContentTitle(getResources().getQuantityString(R.plurals.notification_title, mcount));
        builder.setContentText(getResources().getQuantityString(R.plurals.notification_text, mcount));
        builder.setSmallIcon(R.drawable.ic_stat_mic);
        builder.setDefaults(defaults);
        builder.setWhen( System.currentTimeMillis() );
        builder.setContentIntent(contentIntent);
        builder.setLights(0xff0080ff, 300, 1000);
        builder.setSound( Uri.parse( prefs.getString("ringtoneOnMessage", "content://settings/system/notification_sound") ) );
        builder.extend(new WearableExtender().addAction(action));
        builder.addAction(R.drawable.ic_action_play, getString(R.string.play_label), playPendingIntent);
        
        Notification notification = builder.getNotification();
        mNotificationManager.notify(MESSAGE_NOTIFICATION, notification);
    }
}
