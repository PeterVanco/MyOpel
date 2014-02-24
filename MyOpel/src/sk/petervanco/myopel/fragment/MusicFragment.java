package sk.petervanco.myopel.fragment;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import sk.nanodesign.bluetooth.BluetoothServiceDevice;
import sk.petervanco.myopel.MyOpelActivity;
import sk.petervanco.myopel.R;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;


public class MusicFragment extends Fragment implements OnClickListener, OnSeekBarChangeListener {

	private MyOpelActivity mActivity = null;
	private BluetoothServiceDevice mDevice; 
	
	private Handler mHandler = new Handler();

	private TextView mInfoMusicFilename;

	private TextView mInfoControlFilename;
	
	private SeekBar songProgressBar;

	  public static final String TAG = MusicFragment.class.getSimpleName();
		MediaPlayer mp = new MediaPlayer();
	
	private static final String MUSIC_SCHEME = "settings";
	  private static final String MUSIC_AUTHORITY = "music";
	  public static final Uri MUSIC_URI = new Uri.Builder()
												  .scheme(MUSIC_SCHEME)
												  .authority(MUSIC_AUTHORITY)
												  .build();
	
	    private String mMCSBuffer = "";
	    private Boolean mMCSInProgress = false;
		private String[] mMCSRecords;
	    private int mMCSRecordsPtr = 0;
		
	  
	  
	  @Override
	  public View onCreateView(LayoutInflater inflater, ViewGroup container,
	      Bundle savedInstanceState) {

		  mActivity = (MyOpelActivity) getActivity();
		  mDevice = mActivity.getBluetoothDevice();    
		  
	    final View v = inflater.inflate(R.layout.music, container, false);


	    mInfoMusicFilename = (TextView) v.findViewById(R.id.text_music_file);
	    mInfoControlFilename = (TextView) v.findViewById(R.id.text_musiccontrol_file);
	    
	    songProgressBar = (SeekBar) v.findViewById(R.id.music_progress);
	    songProgressBar.setOnSeekBarChangeListener(this);
	    
	    v.findViewById(R.id.btn_choose_music).setOnClickListener(this);
	    v.findViewById(R.id.choose_control_file).setOnClickListener(this);
	    v.findViewById(R.id.play_pause).setOnClickListener(this);
	    
//
//	    TextView name = (TextView) v.findViewById(R.id.disconnection_bt_name);
//	    name.setText(mActivity.getConnectedDeviceName());
//	    TextView address = (TextView) v.findViewById(R.id.disconnection_bt_address);
//	    address.setText(mActivity.getConnectedDeviceAddress());
	    
  	    return v;
	  }

	  	  
	  private String mMusicFile;
	  private String mControlFile;
	  
	public void SetMusicFilename(File file) {
		
		mMusicFile = file.getAbsolutePath();
		
		String shortName = file.getName();
		if (shortName.length() > 20)
			shortName = shortName.substring(0, 20) + " ...";
		mInfoMusicFilename.setText(shortName);
	}		
  
	public void SetControlFilename(File file) {
		
		mControlFile = file.getAbsolutePath();
		
		String shortName = file.getName();
		if (shortName.length() > 20)
			shortName = shortName.substring(0, 20) + " ...";
		mInfoControlFilename.setText(shortName);
		
	}		
  
	
    public static String convertStreamToString(InputStream is) throws Exception {
	    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
	    StringBuilder sb = new StringBuilder();
	    String line = null;
	    while ((line = reader.readLine()) != null) {
	      sb.append(line).append("\n");
	    }
	    return sb.toString();
	}

	public static String getStringFromFile (String filePath) throws Exception {
	    File fl = new File(filePath);
	    FileInputStream fin = new FileInputStream(fl);
	    String ret = convertStreamToString(fin);
	    //Make sure you close all streams.
	    fin.close();        
	    return ret;
	}  	
	
    /**
     * Update timer on seekbar
     * */
    public void updateProgressBar() {
        mHandler.postDelayed(mUpdateTimeTask, 100);
    }   
 
    /**
     * Background Runnable thread
     * */
    private Runnable mUpdateTimeTask = new Runnable() {
           public void run() {
               long totalDuration = mp.getDuration();
               long currentDuration = mp.getCurrentPosition();
 
               // Displaying Total Duration time
               //songTotalDurationLabel.setText(""+utils.milliSecondsToTimer(totalDuration));
               // Displaying time completed playing
               //songCurrentDurationLabel.setText(""+utils.milliSecondsToTimer(currentDuration));
 
               // Updating progress bar
               //int progress = (int)(utils.getProgressPercentage(currentDuration, totalDuration));
               //Log.d("Progress", ""+progress);
               songProgressBar.setProgress((int) (100 * currentDuration / totalDuration));
 
               // Running this thread after 100 milliseconds
               if (mp.isPlaying())
            	   mHandler.postDelayed(this, 100);
           }
        };	
	
        
        /**
         * When user starts moving the progress handler
         * */
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // remove message Handler from updating progress bar
            mHandler.removeCallbacks(mUpdateTimeTask);
        }
     
        /**
         * When user stops moving the progress hanlder
         * */
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mHandler.removeCallbacks(mUpdateTimeTask);
            int totalDuration = mp.getDuration();
            int currentPosition = (int) ((seekBar.getProgress() / 100.0) * totalDuration);
     
            // forward or backward to certain seconds
            //mp.seekTo(currentPosition);

			try {
	            mp.stop();
				prepareMCS(mControlFile, currentPosition);
				mp.reset();
				mp.setDataSource(mMusicFile);
				startMCS();
				mp.prepare();
				mp.start();
				updateProgressBar();
			} catch (IOException e) {
				e.printStackTrace();
			}
        }        

	public synchronized void prepareMCS(String controlFile, int TimeOffset) {
		try {
			mActivity.sendMessage(MyOpelActivity.MESSAGE_MCS_STOP, null);

			Log.d("MCS", "Opening control file: " + controlFile);
			String control = getStringFromFile(controlFile);
			mMCSRecords = control.split("\n");
			mMCSRecordsPtr = 0;
			mMCSInProgress = true;		
			Log.d("MCS", "Got " + mMCSRecords.length + " mcs records");
			
			if (TimeOffset > 0) {
				for (; mMCSRecordsPtr < mMCSRecords.length; mMCSRecordsPtr++) {
					String[] record = mMCSRecords[mMCSRecordsPtr].split(",");
					int milis = Integer.parseInt(record[0]);
					if (milis > TimeOffset)
						break;
				}
			}
			
			SystemClock.sleep(500);
			continueMCS();
			SystemClock.sleep(500);
			continueMCS();
			SystemClock.sleep(500);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}  	
  	
    public void continueMCS() {
		Log.d("MCS", (mMCSRecordsPtr + 1) + " / " + mMCSRecords.length);
		
		if (mMCSRecordsPtr < mMCSRecords.length) {
			int i;
			ByteArrayOutputStream feed = new ByteArrayOutputStream();
			for (i = 0; i < 40; i++) {
				
				Log.d("MCS", "Feeding " + (mMCSRecordsPtr + i + 1));
				
				
				String[] record = mMCSRecords[mMCSRecordsPtr + i].split(",");
				int milis = Integer.parseInt(record[0]);
				feed.write(milis >> 16);
				feed.write((milis >> 8) & 0xff);
				feed.write(milis & 0xff);
				feed.write(Integer.parseInt(record[1]));
				feed.write(Integer.parseInt(record[2]));
				feed.write(Integer.parseInt(record[3]));
				if (mMCSRecordsPtr + i + 1 == mMCSRecords.length) {
					i++;
					break;
				}
			}
			mMCSRecordsPtr += i;

			mActivity.sendMessage(MyOpelActivity.MESSAGE_MCS_FEED, feed.toByteArray());
		}    	
    }		
	
	public void startMCS() {
		mActivity.sendMessage(MyOpelActivity.MESSAGE_MCS_START, null);
	}
    
	public void stopMCS() {
		mActivity.sendMessage(MyOpelActivity.MESSAGE_MCS_STOP, null);
	}
    
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()) {
		
		case R.id.btn_choose_music:
			mActivity.requestFileLocation(MyOpelActivity.REQUEST_GET_FILE_MUSIC);
			break;

		case R.id.choose_control_file:
			mActivity.requestFileLocation(MyOpelActivity.REQUEST_GET_FILE_MUSIC_CONTROL);
			break;
			
		case R.id.play_pause:
			try {
				
				if (mp.isPlaying()) {
					stopMCS();
					mp.stop();
				}
				else {
					prepareMCS(mControlFile, 0);
					mp.reset();
					mp.setDataSource(mMusicFile);
					startMCS();
					mp.prepare();
					mp.start();
					//updateProgressBar();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;

		default:
			break;
		}
		
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		// TODO Auto-generated method stub
		
	}

}
