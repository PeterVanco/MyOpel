package sk.petervanco.myopel.fragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import sk.nanodesign.bluetooth.BluetoothService;
import sk.petervanco.myopel.MyOpelActivity;
import sk.petervanco.myopel.R;
import sk.petervanco.myopel.adapter.BtListAdapter;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.haarman.listviewanimations.swinginadapters.prepared.SwingBottomInAnimationAdapter;

public class ConnectionFragment extends Fragment implements OnClickListener{
  
  public static final String TAG = ConnectionFragment.class.getSimpleName();

  private static final String CONNECTION_SCHEME = "settings";
  private static final String CONNECTION_AUTHORITY = "connection";
  public static final Uri CONNECTION_URI = new Uri.Builder()
											  .scheme(CONNECTION_SCHEME)
											  .authority(CONNECTION_AUTHORITY)
											  .build();
  
  
  public static final String KEY_DEVICE  = "name"; // parent node
  public static final String KEY_ADDRESS = "address";
  public static final String KEY_IMAGE   = "image";  

  private SwingBottomInAnimationAdapter swingBottomInAnimationAdapter;
  private ArrayList<HashMap<String, String>> mPairedDevices;
  private BtListAdapter mBtListAdapter;
  
  private MyOpelActivity mActivity;
  private BluetoothService mService;   
  
  private void copyPairedDevices() {
	  
	mPairedDevices.clear();
	Set<BluetoothDevice> pairedDevices = mService.getBonded();
	
	if (pairedDevices.size() > 0) {
	    for (BluetoothDevice device : pairedDevices) {
	    	HashMap<String, String> mPair = new HashMap<String, String>();
	    	mPair.put(KEY_DEVICE, device.getName());
	    	mPair.put(KEY_ADDRESS, device.getAddress());
	    	mPair.put(KEY_IMAGE, device.getBluetoothClass().toString());
	    	mPairedDevices.add(mPair);
	    }
	} else {
	    String noDevices = getResources().getText(R.string.no_device).toString();
		HashMap<String, String> mPair = new HashMap<String, String>();
		mPair.put(KEY_DEVICE, noDevices);
		mPair.put(KEY_ADDRESS, "");
		mPair.put(KEY_IMAGE, "");
		mPairedDevices.add(mPair);
	}    
	  
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {

    final View v = inflater.inflate(R.layout.connection, container, false);
    mActivity = (MyOpelActivity)getActivity();
    mService = mActivity.getBluetoothService();
    
    v.findViewById(R.id.btn_bt_discover).setOnClickListener(this);
    
    mPairedDevices = new ArrayList<HashMap<String,String>>();
    copyPairedDevices();

    mBtListAdapter = new BtListAdapter(getActivity(), mPairedDevices);
    ListView mBtDevicesList = (ListView) v.findViewById(R.id.bt_devices);
    
    swingBottomInAnimationAdapter = new SwingBottomInAnimationAdapter(mBtListAdapter);
    swingBottomInAnimationAdapter.setAbsListView(mBtDevicesList);
    mBtDevicesList.setAdapter(swingBottomInAnimationAdapter);
    //mBtDevicesList.setAdapter(mBtListAdapter);
    mBtDevicesList.setOnItemClickListener(new OnItemClickListener() {
    	 
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			// TODO Auto-generated method stub
		}
	});    
    
    return v;
  }
  
  public void refreshBonded() {
	copyPairedDevices();
	swingBottomInAnimationAdapter.notifyDataSetChanged();		
  }
  
  public void onClick(View v) {

	  switch (v.getId()) {
	  
	  	case R.id.btn_bt_discover:
	  		if (!mService.isBluetoothDiscovering()) {
	  			mActivity.getSupportActionBar().setSubtitle("Vyh¾adávam ...");
	  			copyPairedDevices();
	  			swingBottomInAnimationAdapter.notifyDataSetChanged();		
		  		mService.startDiscovery();
	  		}
	  		break;

	  	default:
		break;
	}
  }

	public void addDiscoveredDevice(BluetoothDevice device) {
		
		
		HashMap<String, String> mPair = new HashMap<String, String>();
		mPair.put(KEY_DEVICE, device.getName());
		mPair.put(KEY_ADDRESS, device.getAddress());
		mPair.put(KEY_IMAGE, device.getBluetoothClass().toString());
		
		if (!mPairedDevices.contains(mPair))
			mPairedDevices.add(mPair);
		swingBottomInAnimationAdapter.notifyDataSetChanged();		
	}

}
