package de.tubs.ibr.dtn.dtalkie;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.renderscript.ScriptGroup;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.security.Timestamp;
import java.sql.Time;
import java.util.Date;

import de.tubs.ibr.dtn.dtalkie.service.RecorderService;
import de.tubs.ibr.dtn.dtalkie.service.Sound;
import de.tubs.ibr.dtn.dtalkie.service.SoundFXManager;
import de.tubs.ibr.dtn.dtalkie.service.TalkieService;
import de.tubs.ibr.dtn.dtalkie.service.Utils;

public class RecordingFragment extends Fragment {

    @SuppressWarnings("unused")
    private static final String TAG = "RecordingFragment";
    
    private ImageButton mRecordButton = null;
    private FrameLayout mRecIndicator = null;
    
    private Boolean mRecording = false;
    private SoundFXManager mSoundManager = null;
    
    private float mAnimScaleHeight = 1.0f;

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);
            switch (requestCode)
            {
                case 100:
                    if(resultCode == getActivity().RESULT_OK){
                        Uri selectedImage = imageReturnedIntent.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};

                        Cursor cursor = getActivity().getContentResolver().query(
                                selectedImage, filePathColumn, null, null, null);
                        cursor.moveToFirst();

                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        String filePath = cursor.getString(columnIndex);
                        cursor.close();
                        try {
                            ExifInterface exif = new ExifInterface(filePath);
                            exif.setAttribute(ExifInterface.TAG_MAKE, "The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.The quick brown fox jumped over the lazy dog near the riverbank.");
                            exif.saveAttributes();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        File f = new File(filePath);
                        Log.d("log", filePath);


                        Intent recorded_i = new Intent(getContext(), TalkieService.class);
                        recorded_i.setAction(TalkieService.ACTION_RECORDED);
                        recorded_i.putExtra("recfile", f);
                        recorded_i.putExtra("destination", (Serializable) RecorderService.TALKIE_GROUP_EID);
                        getContext().startService(recorded_i);

                    }
                break;

                case 1888:
                    if(resultCode == getActivity().RESULT_OK)
                    {
                        //Uri selectedImage = imageReturnedIntent.getData();

                        try {
                            Bitmap bitmap;
                            if(imageReturnedIntent.getData()==null){
                                bitmap = (Bitmap)imageReturnedIntent.getExtras().get("data");
                            }else{
                                bitmap = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(), imageReturnedIntent.getData());
                            }

                            //ExifInterface exif = new ExifInterface(b);
                            //Log.d("LOG", Double.toString(exif.getAttributeDouble(ExifInterface.TAG_GPS_LATITUDE, 0.0)));

                            //InputStream imageStream = getActivity().getContentResolver().openInputStream(selectedImage);
                            //Bitmap yourSelectedImage = BitmapFactory.decodeStream(imageStream);
                            Intent recorded_i = new Intent(getContext(), TalkieService.class);
                            recorded_i.setAction(TalkieService.ACTION_RECORDED);
                            Date d = new Date();

                            File f = new File(getContext().getCacheDir(), d.toString());
                            f.createNewFile();

                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100 /*ignored for PNG*/, bos);
                            byte[] bitmapdata = bos.toByteArray();

//write the bytes in file
                            FileOutputStream fos = new FileOutputStream(f);
                            fos.write(bitmapdata);
                            fos.flush();
                            fos.close();

                            recorded_i.putExtra("recfile", f);
                            recorded_i.putExtra("destination", (Serializable) RecorderService.TALKIE_GROUP_EID);
                            getContext().startService(recorded_i);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                    break;
            }



    }

    private OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch ( event.getAction() ) {
                case MotionEvent.ACTION_DOWN:
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    if (prefs.getBoolean("ptt", false)) {
                        //startRecording();

                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                    Intent i = new Intent(Intent.ACTION_PICK,
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    startActivityForResult(i, 100);
                    //Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                    //photoPickerIntent.setType("image/*");
                    //startActivityForResult(photoPickerIntent, 100);
                    /*
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, 1888);
                    */
                    //toggleRecording();
                    break;
            }
            
            return true;
        }
    };
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recording_fragment, container, false);
        
        mRecordButton = (ImageButton) v.findViewById(R.id.button_record);
        mRecordButton.setOnTouchListener(mTouchListener);
        
        mRecIndicator = (FrameLayout) v.findViewById(R.id.ptt_background);
        
        return v;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        // set volume control to VOICE_CALL
        getActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
        // init sound pool
        mSoundManager = new SoundFXManager(AudioManager.STREAM_VOICE_CALL, 2);
        
        mSoundManager.load(getActivity(), Sound.BEEP);
        mSoundManager.load(getActivity(), Sound.QUIT);
        mSoundManager.load(getActivity(), Sound.SQUELSH_LONG);
        mSoundManager.load(getActivity(), Sound.SQUELSH_SHORT);
	}

	@Override
	public void onDestroy() {
	    // free sound manager resources
	    mSoundManager.release();
	    
		super.onDestroy();
	}

	@Override
    public void onPause() {
        // we are going out of scope - stop recording
        stopRecording();
        
        // unregister from recorder events
        getActivity().unregisterReceiver(mRecorderEventReceiver);
        
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

    	IntentFilter filter = new IntentFilter();
    	filter.addAction(RecorderService.EVENT_RECORDING_EVENT);
    	filter.addAction(RecorderService.EVENT_RECORDING_INDICATOR);
    	getActivity().registerReceiver(mRecorderEventReceiver, filter);
    }
    
    private void startRecording() {
        if (mRecording) return;
        mRecording = true;
        
        // lock screen orientation
        Utils.lockScreenOrientation(getActivity());
        
        // get recording preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean indicator = !prefs.getBoolean("ptt", false);
        boolean auto_stop = !prefs.getBoolean("ptt", false);

        // start recording

        RecorderService.startRecording(getActivity(), RecorderService.TALKIE_GROUP_EID, indicator, auto_stop);
    }
    
    private void stopRecording() {
        if (!mRecording) return;
        mRecording = false;
        
        // unlock screen orientation
        Utils.unlockScreenOrientation(getActivity());
        
        // stop recording
        RecorderService.stopRecording(getActivity());
    }
    
    private void toggleRecording() {
        if (mRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private BroadcastReceiver mRecorderEventReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (RecorderService.EVENT_RECORDING_EVENT.equals(intent.getAction())) {
				String action = intent.getStringExtra(RecorderService.EXTRA_RECORDING_ACTION);
				
				if (RecorderService.ACTION_START_RECORDING.equals(action)) {
			        // make a noise
					mSoundManager.play(getActivity(), Sound.BEEP);
					
			        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			        
			        // mark as recording
			        mRecording = true;
			        
			        // show full indicator when dynamic indicator is disabled
			        if (prefs.getBoolean("ptt", false)) {
			        	// set indicator level to full
			        	setIndicator(1.0f, 0);
			        } else {
			        	// set indicator level to zero
			        	setIndicator(0.0f, 0);
			        }
				}
				else if (RecorderService.ACTION_STOP_RECORDING.equals(action)) {
			        // set indicator level to zero
			        setIndicator(0.0f, 0);
			        
			        // mark as not recording
			        mRecording = false;
			        
			        // unlock screen orientation
			        Utils.unlockScreenOrientation(getActivity());
			        
					// make a noise
					mSoundManager.play(getActivity(), Sound.QUIT);
				}
				else if (RecorderService.ACTION_ABORT_RECORDING.equals(action)) {
			        // set indicator level to zero
			        setIndicator(0.0f, 0);

			        // mark as not recording
			        mRecording = false;
			        
			        // unlock screen orientation
			        Utils.unlockScreenOrientation(getActivity());
			        
					// make a noise
					mSoundManager.play(getActivity(), Sound.SQUELSH_SHORT);
				}
			}
			else if (RecorderService.EVENT_RECORDING_INDICATOR.equals(intent.getAction())) {
				// read indicator value
				float level = intent.getFloatExtra(RecorderService.EXTRA_LEVEL_NORMALIZED, 0.0f);
				
		        // set indicator level
		        setIndicator(level);
			}
		}

    };
    
    private void setIndicator(Float level) {
    	setIndicator(level, 100);
    }
    
    private void setIndicator(Float level, int duration) {
    	if (duration == 0 && level == 1.0f) {
    		// make indicator invisible
    		mRecIndicator.setVisibility(View.INVISIBLE);
    		return;
    	}
    	
    	// set indicator to visible
    	mRecIndicator.setVisibility(View.VISIBLE);
    	
        Animation a = new ScaleAnimation(1.0f, 1.0f, 1 - mAnimScaleHeight, 1 - level);
        
        mAnimScaleHeight = level;

        a.setDuration(duration);
        a.setInterpolator(new LinearInterpolator());
        a.startNow();
        mRecIndicator.startAnimation(a);
    }
}
