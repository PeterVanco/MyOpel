package sk.petervanco.myopel;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import shared.ui.actionscontentview.ActionsContentView;
import sk.nanodesign.bluetooth.BluetoothService;
import sk.nanodesign.bluetooth.BluetoothServiceDFU;
import sk.nanodesign.bluetooth.BluetoothServiceDevice;
import sk.nanodesign.bluetooth.BluetoothServiceDevice.BluetoothState;
import sk.petervanco.myopel.adapter.ActionsAdapter;
import sk.petervanco.myopel.fragment.ConnectionFragment;
import sk.petervanco.myopel.fragment.DisconnectionFragment;
import sk.petervanco.myopel.fragment.FirmwareFragment;
import sk.petervanco.myopel.fragment.ModeFragment;
import sk.petervanco.myopel.fragment.MusicFragment;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.haarman.listviewanimations.swinginadapters.prepared.SwingRightInAnimationAdapter;
import com.ipaulpro.afilechooser.utils.FileUtils;

public class MyOpelActivity extends ActionBarActivity {

  private static final String TAG = MyOpelActivity.class.getSimpleName();
  private static final String STATE_URI = "state:uri";
  private static final String STATE_FRAGMENT_TAG = "state:fragment_tag";

  private ActionsContentView viewActionsContentView;
  private static ActionsAdapter actionsAdapter;
  
  private static ActionBar mActionBar;
  private static MenuItem mReadyMenuItem;
  private Boolean doubleBackToExitPressedOnce = false;
  
  private static Resources mResources;
  private static FragmentManager mFragmentManager;

  private static Uri currentUri = ConnectionFragment.CONNECTION_URI;
  private static String currentContentFragmentTag = null;

  private static final String DEVICE_ID = "MyOpelDevice";
  private static BluetoothService mBluetoothService;
  private static BluetoothServiceDevice mDevice;
  
  private static NotificationManager mNotifyManager = null;
  private static NotificationCompat.Builder mBuilder = null;  

  private static final int REQUEST_BLUETOOTH_ENABLE = 0;
  public static final int  REQUEST_GET_FILE_FIRMWARE = 1;  
  public static final int  REQUEST_GET_FILE_MUSIC = 2;  
  public static final int  REQUEST_GET_FILE_MUSIC_CONTROL = 3;  
  
  private static final int PROTOCOL_START_BYTE = 0xAA;
  private static final int PROTOCOL_STOP_BYTE = 0x0A;
  
  public static final int MESSAGE_DEVICE_FIRMWARE_UPDATE = 0x01;
  public static final int MESSAGE_SOLID = 0x02;
  public static final int MESSAGE_RAINBOW = 0x03;
  public static final int MESSAGE_SPEED = 0x04;
  public static final int MESSAGE_FADING = 0x05;
  public static final int MESSAGE_MCS_START = 0x06;
  public static final int MESSAGE_MCS_FEED = 0x07;
  public static final int MESSAGE_MCS_STOP = 0x08;
  public static final int MESSAGE_MCS_NEXT = 0x09;
  
  	private static final int RECSTATE_WAITING_FOR_START = 1;
  	private static final int RECSTATE_WAITING_FOR_LENGTH = 2;
  	private static final int RECSTATE_WAITING_FOR_MSG_TYPE = 3;
  	private static final int RECSTATE_RECEIVING_DATA = 4;
  	private static final int RECSTATE_WAITING_FOR_STOP = 5;
	
  	static int mBufferStage = RECSTATE_WAITING_FOR_START;
	static int mReceiveLength = -1;
	static int mReceiveMsgType = -1;
	static int mReceiveBufferPointer = 0;
	static byte mReceiveBuffer[] = new byte[50];

	
	private static void ProcessMessage(int MessageType, byte[] MessageData, int MessageDataSize) {
		
		switch (MessageType) {
		
		case MESSAGE_MCS_NEXT:
            final MusicFragment currentFragment = (MusicFragment) mFragmentManager.findFragmentByTag(MusicFragment.TAG);
            if (currentFragment != null) {
                currentFragment.continueMCS();
            }
			break;

		default:
			break;
		}
		
	}
  
  private static void parseBuffer(int len) {

	  
		byte[] buffer = mDevice.read(len);

		int bufferPointer;

		for (bufferPointer = 0; bufferPointer < len; bufferPointer++) {
			switch (mBufferStage) {
			
				case RECSTATE_WAITING_FOR_START:
					if (buffer[bufferPointer] == (byte)0xaa) {
						mBufferStage = RECSTATE_WAITING_FOR_LENGTH;
					}
					break;
					
				case RECSTATE_WAITING_FOR_LENGTH:
					mReceiveLength = buffer[bufferPointer];
					mBufferStage = RECSTATE_WAITING_FOR_MSG_TYPE;
					break;
					
				case RECSTATE_RECEIVING_DATA:
				case RECSTATE_WAITING_FOR_MSG_TYPE:
				{
					if (mBufferStage == RECSTATE_RECEIVING_DATA)
						mReceiveBuffer[mReceiveBufferPointer++] = buffer[bufferPointer];
					else {
						mReceiveMsgType = buffer[bufferPointer];
						mBufferStage = RECSTATE_RECEIVING_DATA;
					}
	
					if (mReceiveLength == mReceiveBufferPointer) {
						ProcessMessage(mReceiveMsgType, mReceiveBuffer, mReceiveLength);
						mBufferStage = RECSTATE_WAITING_FOR_STOP;
					}
				}
					break;
					
				case RECSTATE_WAITING_FOR_STOP:
					if (buffer[bufferPointer] == (byte)0x0a)
					{
						mReceiveLength = -1;
						mReceiveMsgType = -1;
						mReceiveBufferPointer = 0;
						mBufferStage = RECSTATE_WAITING_FOR_START;
					}
					break;			
			}
		}
	}  
  
  	public byte[] buildMessage(int MessageType, byte[] Stream) {
		ByteArrayOutputStream mMessage = new ByteArrayOutputStream();
		mMessage.write(PROTOCOL_START_BYTE);
		mMessage.write((Stream != null) ? Stream.length : 0x00);
		mMessage.write(MessageType);
		if (Stream != null){
			try {
				mMessage.write(Stream);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		mMessage.write(PROTOCOL_STOP_BYTE);
		return mMessage.toByteArray();
  	}

	public void sendMessage(int MessageType, byte[] Stream) {
		mDevice.write(buildMessage(MessageType, Stream));
	}
	
	public BluetoothServiceDevice getBluetoothDevice() {
		return mDevice;
	}
	
	public BluetoothService getBluetoothService() {
		return mBluetoothService;
	}
	
	public void requestFileLocation(int request) {
	  	Intent target = FileUtils.createGetContentIntent();
		Intent intent = Intent.createChooser(target, "Vyberte aktualizaèný súbor");
		try {
			startActivityForResult(intent, request);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();
		}        	  
	}	

	public void onActivityResult(int requestCode, int resultCode, Intent data) {

		 Log.d(TAG, "onActivityResult " + resultCode);
	      Log.d(TAG, "onActivityResult request code: " + requestCode);
	      switch (requestCode) {
			case REQUEST_GET_FILE_FIRMWARE:
			case REQUEST_GET_FILE_MUSIC:	
			case REQUEST_GET_FILE_MUSIC_CONTROL:	
				
				if (resultCode == RESULT_OK && data != null) {		
					try {
						final Uri uri = data.getData();
						final File file = FileUtils.getFile(uri);
						final String filePath = file.getAbsolutePath();
						
						switch (requestCode) {
							case REQUEST_GET_FILE_FIRMWARE:
								if (filePath.toLowerCase(Locale.US).endsWith(".hex")) {
									if (currentUri.equals(FirmwareFragment.FIRMWARE_URI)) {
				                        final FirmwareFragment currentFragment = (FirmwareFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
				                        currentFragment.SetFirmwareFilename(file);
									}
								}
								else
									Toast.makeText(this, "Nesprávny typ súboru (žiadaný HEX)", Toast.LENGTH_SHORT).show();
							break;

							case REQUEST_GET_FILE_MUSIC:
								if (filePath.toLowerCase(Locale.US).endsWith(".mp3")) {
									if (currentUri.equals(MusicFragment.MUSIC_URI)) {
				                        final MusicFragment currentFragment = (MusicFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
				                        currentFragment.SetMusicFilename(file);
									}
								}
								else
									Toast.makeText(this, "Nesprávny typ súboru (žiadaný MP3)", Toast.LENGTH_SHORT).show();
							break;

							case REQUEST_GET_FILE_MUSIC_CONTROL:
								if (filePath.toLowerCase(Locale.US).endsWith(".mcf") || filePath.toLowerCase(Locale.US).endsWith(".csv")) {
									if (currentUri.equals(MusicFragment.MUSIC_URI)) {
				                        final MusicFragment currentFragment = (MusicFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
				                        currentFragment.SetControlFilename(file);
									}
								}
								else
									Toast.makeText(this, "Nesprávny typ súboru (žiadaný MCF)", Toast.LENGTH_SHORT).show();
							break;
							
						default:
							break;
						}
					} catch (Exception e) {
						Log.e("FileSelectorTestActivity", "File select error", e);
					}
				} 
			break;
			
	        case REQUEST_BLUETOOTH_ENABLE:
	            if (resultCode == Activity.RESULT_OK) {
					if (currentUri.equals(ConnectionFragment.CONNECTION_URI)) {
                        final ConnectionFragment currentFragment = (ConnectionFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
                        currentFragment.refreshBonded();
					}
	                //Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show();
	            } else {
	                Toast.makeText(this, "Bluetooth musí by zapnutý", Toast.LENGTH_SHORT).show();
	                finish();
	            }
	        break;
	      }
	  }  	
	
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                	
                    if (currentUri.equals(ConnectionFragment.CONNECTION_URI)) {
                        final ConnectionFragment currentFragment = (ConnectionFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
                        currentFragment.addDiscoveredDevice(device);
                    }
                    else 
                    	Toast.makeText(getApplicationContext(), "Found " + device.getName(), Toast.LENGTH_SHORT).show();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (currentUri.equals(ConnectionFragment.CONNECTION_URI)) {
                	mActionBar.setSubtitle("Vyh¾adávanie dokonèené");
                }
            }
        }
    };	
	
  private static final Handler BluetoothHandler = new Handler() {
  	
      public void handleMessage(Message msg) {

      	Bundle b = msg.getData(); 
      	
      	switch (msg.what) {

      			case BluetoothService.MESSAGE_CONNECTED:
	            	mActionBar.setTitle(mResources.getString(R.string.app_name));
	            	mActionBar.setSubtitle("Pripojenie úspešné");
	        		mReadyMenuItem.setIcon(R.drawable.ic_link_facebook);
	            	actionsAdapter.SetVisibilityLevel(mResources.getInteger(R.integer.only_while_connected));
	            	updateContent(ModeFragment.MODE_URI);
	                break;
	            
      			case BluetoothService.MESSAGE_RECEIVED:
	                //String bluetoothMessage = new String(b.getByteArray(BluetoothServiceDevice.EXTRA_RECEIVED_BUFFER), 0, b.getInt(BluetoothServiceDevice.EXTRA_RECEIVED_LENGTH));
            		parseBuffer(b.getInt(BluetoothServiceDevice.EXTRA_RECEIVED_LENGTH));
	                break;
	            
      			case BluetoothService.MESSAGE_DISCONNECTED:
	            	actionsAdapter.SetVisibilityLevel(mResources.getInteger(R.integer.show_everytime));
	            	updateContent(ConnectionFragment.CONNECTION_URI);
	                break;
	            
      			case BluetoothService.MESSAGE_CONNECTION_FAILED:
	            	mActionBar.setSubtitle("Pripojenie zlyhalo");
	                break;
	            
	            case BluetoothServiceDFU.MESSAGE_DFU_BEGIN:
	        		mBuilder.setContentTitle("Aktualizácia")
	        	    	    .setContentText("Èaká sa ...")
	        	    	    .setSmallIcon(R.drawable.ic_launcher_opel)
	        				.setProgress(100, 0, false);

	        		mNotifyManager.notify(0, mBuilder.build()); 	
	        	  break;
	          case BluetoothServiceDFU.MESSAGE_DFU_PROGRESS:
	        	  
	        	  int PercentDone = 100 * b.getInt(BluetoothServiceDFU.MESSAGE_DFU_PROGRESS_ACTUAL) / b.getInt(BluetoothServiceDFU.MESSAGE_DFU_PROGRESS_TOP);
	        	  
		      		mBuilder.setContentTitle("Aktualizácia")
			    	    .setContentText("Prebieha ... " + PercentDone + "%")
			    	    .setSmallIcon(R.drawable.ic_launcher)
						.setProgress(b.getInt(BluetoothServiceDFU.MESSAGE_DFU_PROGRESS_TOP) , b.getInt(BluetoothServiceDFU.MESSAGE_DFU_PROGRESS_ACTUAL), false);
		      		mNotifyManager.notify(0, mBuilder.build());
		      		
		      		
                    if (currentUri.equals(FirmwareFragment.FIRMWARE_URI)) {
                        final FirmwareFragment currentFragment = (FirmwareFragment) mFragmentManager.findFragmentByTag(currentContentFragmentTag);
                        currentFragment.setUpgradeProgress(PercentDone);
                    }
	        	  break;
	          
	          case BluetoothServiceDFU.MESSAGE_DFU_END:
		      		mBuilder.setContentTitle("Aktualizácia dokonèená")
		      				.setContentText("Úspešne aktualizované na novú verziu")
		      				.setSmallIcon(R.drawable.ic_launcher)
		      				.setProgress(0, 0, false);
		      		mNotifyManager.notify(0, mBuilder.build()); 
	        	  break;
	          
	          case BluetoothServiceDFU.MESSAGE_DFU_FAILED:
		      		mBuilder.setContentTitle("Aktualizácia zlyhala")
		      				.setContentText("Zariadenie neodpovedalo, prosím skúste to znova")
		      				.setSmallIcon(R.drawable.ic_launcher)
		      				.setProgress(0, 0, false);
		      		mNotifyManager.notify(0, mBuilder.build());
	        	  break;

	            default:
	            	break;
          }
      }
  };	  
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    viewActionsContentView = (ActionsContentView) findViewById(R.id.actionsContentView);
    viewActionsContentView.setSwipingType(ActionsContentView.SWIPING_EDGE);

    mBluetoothService = new BluetoothService(BluetoothHandler);
	if (!mBluetoothService.isBluetoothEnabled()) {
    	startActivityForResult(mBluetoothService.createBluetoothEnableIntent(), REQUEST_BLUETOOTH_ENABLE);
	}

	mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
	mBuilder = new NotificationCompat.Builder(this);
	Intent notificationIntent = new Intent(getApplicationContext(), MyOpelActivity.class);
    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP );
    PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    mBuilder.setContentIntent(pendingIntent);
	
	
    final ListView viewActionsList = (ListView) findViewById(R.id.actions);
    actionsAdapter = new ActionsAdapter(this);
    actionsAdapter.SetVisibilityLevel(getResources().getInteger(R.integer.show_everytime));
    SwingRightInAnimationAdapter swingRightInAnimationAdapter = new SwingRightInAnimationAdapter(actionsAdapter);
    swingRightInAnimationAdapter.setAbsListView(viewActionsList);
    viewActionsList.setAdapter(swingRightInAnimationAdapter);
    viewActionsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> adapter, View v, int position, long flags) {
        final Uri uri = actionsAdapter.getItem(position);
        updateContent(uri);
        viewActionsContentView.showContent();
      }
    });

    if (savedInstanceState != null) {
      currentUri = Uri.parse(savedInstanceState.getString(STATE_URI));
      currentContentFragmentTag = savedInstanceState.getString(STATE_FRAGMENT_TAG);
    }

    mActionBar = getSupportActionBar();
    mActionBar.setSubtitle("Nepripojený");
    //mActionBar.setHomeButtonEnabled(true);
    mActionBar.setDisplayHomeAsUpEnabled(true);
    mFragmentManager = getSupportFragmentManager();
    mResources = getResources();
    
    IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    registerReceiver(mBluetoothReceiver, filter);
    
    filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    registerReceiver(mBluetoothReceiver, filter);		
	
    
    updateContent(currentUri);
  }

  public void onActionsButtonClick(View view) {
    if (viewActionsContentView.isActionsShown())
      viewActionsContentView.showContent();
    else
      viewActionsContentView.showActions();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putString(STATE_URI, currentUri.toString());
    outState.putString(STATE_FRAGMENT_TAG, currentContentFragmentTag);

    super.onSaveInstanceState(outState);
  }

  public void connectionRequest(String name, String address) {
		Toast.makeText(getApplicationContext(), "Connecting to " + name, Toast.LENGTH_SHORT).show();
        
		mDevice = mBluetoothService.createDevice(DEVICE_ID, mBluetoothService.getDeviceByAddress(address));		
		mDevice.connect();
  }  
  
  public static void updateContent(Uri uri) {
	    final Fragment fragment;
	    final String tag;

	    final FragmentTransaction tr = mFragmentManager.beginTransaction();

	    int positionDiff = actionsAdapter.getItemPosition(uri) - actionsAdapter.getItemPosition(currentUri);
	    Log.d(TAG, actionsAdapter.getItemPosition(uri) + " POS " +actionsAdapter.getItemPosition(currentUri));
	    if (positionDiff > 0)
	    	tr.setCustomAnimations(R.anim.slide_up_enter, R.anim.slide_up_exit);
	    else if (positionDiff < 0)
	    	tr.setCustomAnimations(R.anim.slide_down_enter, R.anim.slide_down_exit);

	    if (!currentUri.equals(uri)) {
	      final Fragment currentFragment = mFragmentManager.findFragmentByTag(currentContentFragmentTag);
	      if (currentFragment != null)
	        tr.hide(currentFragment);
	    }

	    if (mDevice != null && mDevice.getState() == BluetoothState.CONNECTED && ConnectionFragment.CONNECTION_URI.equals(uri)) {
	        uri = DisconnectionFragment.DISCONNECTION_URI;
	    }
	    
	    if (ConnectionFragment.CONNECTION_URI.equals(uri)) {
	        tag = ConnectionFragment.TAG;
	        final Fragment foundFragment = mFragmentManager.findFragmentByTag(tag);
	        if (foundFragment != null) {
	          fragment = foundFragment;
	        } else {
	          fragment = new ConnectionFragment();
	        }
	      } 
	    
	    else if (DisconnectionFragment.DISCONNECTION_URI.equals(uri)) {
	      tag = DisconnectionFragment.TAG;
	      final Fragment foundFragment = mFragmentManager.findFragmentByTag(tag);
	      if (foundFragment != null) {
	        fragment = foundFragment;
	      } else {
	        fragment = new DisconnectionFragment();
	      }
	    } 

	    else if (FirmwareFragment.FIRMWARE_URI.equals(uri)) {
	        tag = FirmwareFragment.TAG;
	        final FirmwareFragment foundFragment = (FirmwareFragment) mFragmentManager.findFragmentByTag(tag);
	        if (foundFragment != null) {
	          fragment = foundFragment;
	        } else {
	          fragment = new FirmwareFragment();
	        }
	      } 
	    else if (ModeFragment.MODE_URI.equals(uri)) {
			tag = ModeFragment.TAG;
			final ModeFragment foundFragment = (ModeFragment) mFragmentManager.findFragmentByTag(tag);
			if (foundFragment != null) {
			  fragment = foundFragment;
			} else {
			  fragment = new ModeFragment();
			}
	    } 
	    	else if (MusicFragment.MUSIC_URI.equals(uri)) {
			tag = MusicFragment.TAG;
			final MusicFragment foundFragment = (MusicFragment) mFragmentManager.findFragmentByTag(tag);
			if (foundFragment != null) {
			  fragment = foundFragment;
			} else {
			  fragment = new MusicFragment();
			}
	    } 
	    
	    else {
	      return;
	    }

	    if (fragment.isAdded()) {
	      tr.show(fragment);
	    } else {
	      tr.replace(R.id.content, fragment, tag);
	    }
	    tr.commit();

	    currentUri = uri;
	    currentContentFragmentTag = tag;
	  }

	  @Override
	  public void onBackPressed() {
	      
		if (doubleBackToExitPressedOnce) {
	          super.onBackPressed();
	          return;
	      }
	      doubleBackToExitPressedOnce = true;
	      Toast.makeText(this, "Stlaète spä znovu pre ukonèenie aplikácie", Toast.LENGTH_SHORT).show();
	      new Handler().postDelayed(new Runnable() {
	
	          @Override
	          public void run() {
	           doubleBackToExitPressedOnce = false;   
	          }
	      }, 2000);
	  }   
  
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main_menu, menu);

		mReadyMenuItem = (MenuItem) menu.findItem(R.id.actionbar_ready);
		return true;
	}  
	
	@Override
	  public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    
	    
	    case android.R.id.home:
	    	if (viewActionsContentView.isActionsShown()) {
		    	viewActionsContentView.showContent();
	    	}
	    	else {
		    	viewActionsContentView.showActions();
	    	}
	    	break;

	    
	    case R.id.actionbar_ready:
	    	
            if (!currentUri.equals(DisconnectionFragment.DISCONNECTION_URI)) {
            	updateContent(DisconnectionFragment.DISCONNECTION_URI);
            }
            else {
            	mDevice.disconnect();
            }
	      break;
	      
	    default:

	      break;
	    }

	    return true;
	  } 	
	
	@Override
	protected void onStop()
	{
		try {
		    unregisterReceiver(mBluetoothReceiver);
		} catch (Exception e) {
			e.printStackTrace();
		}
	    super.onStop();
	}	
}
