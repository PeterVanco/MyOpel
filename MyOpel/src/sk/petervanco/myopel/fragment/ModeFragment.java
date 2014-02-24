
package sk.petervanco.myopel.fragment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import sk.nanodesign.bluetooth.BluetoothServiceDevice;
import sk.petervanco.myopel.MyOpelActivity;
import sk.petervanco.myopel.R;
import sk.petervanco.myopel.color.ColorPicker;
import sk.petervanco.myopel.color.ColorPicker.OnColorChangedListener;
import sk.petervanco.myopel.color.SVBar;
import sk.petervanco.myopel.color.ValueBar;
import sk.petervanco.myopel.color.ValueBar.OnValueChangedListener;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

public class ModeFragment extends Fragment implements OnClickListener {
	
  public static final String 	TAG = ModeFragment.class.getSimpleName();

  private static final String 	MODE_SCHEME = "settings";
  private static final String 	MODE_AUTHORITY = "mode";
  public static final  Uri 		MODE_URI = new Uri.Builder()
													  .scheme(MODE_SCHEME)
													  .authority(MODE_AUTHORITY)
													  .build();
  private MyOpelActivity mActivity;
  private BluetoothServiceDevice mDevice; 
  private ValueBar mAnimationSpeed = null;
  ColorPicker mSolidColor;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    final View v = inflater.inflate(R.layout.mode, container, false);
    
    mActivity = (MyOpelActivity)getActivity();
    mDevice = mActivity.getBluetoothDevice();    

    v.findViewById(R.id.btn_mode_solid).setOnClickListener(this);
    v.findViewById(R.id.btn_mode_fade).setOnClickListener(this);
    v.findViewById(R.id.btn_mode_rainbow).setOnClickListener(this);

    RadioGroup door_logic = (RadioGroup)(v.findViewById(R.id.radiogroup_door_logic));
    door_logic.setOnCheckedChangeListener(new OnCheckedChangeListener() {
		
		@Override
		public void onCheckedChanged(RadioGroup group, int radio) {

			switch (radio) {
			case R.id.radio_disable_door_logic:
				Toast.makeText(mActivity, "Disabling door logic", Toast.LENGTH_SHORT).show();
				mDevice.write("dsisable\n");
				break;

			case R.id.radio_enable_door_logic:
				Toast.makeText(mActivity, "Enabling door logic", Toast.LENGTH_SHORT).show();
				mDevice.write("enable\n");
				break;
			}
			
		}
	});

    RadioGroup door_trigger = (RadioGroup)(v.findViewById(R.id.radiogroup_door_trigger));
    door_trigger.setOnCheckedChangeListener(new OnCheckedChangeListener() {
		
		@Override
		public void onCheckedChanged(RadioGroup group, int radio) {

			switch (radio) {
			case R.id.radio_2_blinks:
				Toast.makeText(mActivity, "2 Blinks", Toast.LENGTH_SHORT).show();
				mDevice.write("blink2\n");
				break;

			case R.id.radio_3_blinks:
				Toast.makeText(mActivity, "3 Blinks", Toast.LENGTH_SHORT).show();
				mDevice.write("blink3\n");
				break;
			}
			
		}
	});
    
    mAnimationSpeed = (ValueBar) v.findViewById(R.id.animSpeed);
    mAnimationSpeed.setColor(Color.WHITE);
    mAnimationSpeed.setOnValueChangedListener(new OnValueChangedListener() {
		
		@Override
		public void onValueChanged(int color) {
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(Color.red(color));
			mActivity.sendMessage(MyOpelActivity.MESSAGE_SPEED, bos.toByteArray());
	
			
		}
	});

    
    mSolidColor = (ColorPicker) v.findViewById(R.id.solid_colorpicker);
    mSolidColor.addSVBar((SVBar) v.findViewById(R.id.solid_svbar));
    mSolidColor.setOnColorChangedListener(new OnColorChangedListener() {
		
		@Override
		public synchronized void onColorChanged(int color) {
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			bos.write(Color.red(color));
			bos.write(Color.green(color));
			bos.write(Color.blue(color));
			mActivity.sendMessage(MyOpelActivity.MESSAGE_SOLID, bos.toByteArray());
		}
	});

    return v;
  } 
  
  @Override
  public void onClick(View v) {

	  ByteArrayOutputStream bos = new ByteArrayOutputStream();
	  int actualColor = mSolidColor.getColor();
	  
	  switch(v.getId()) {

	  	  case R.id.btn_mode_rainbow:
	  			mActivity.sendMessage(MyOpelActivity.MESSAGE_RAINBOW, null);
	  	  break;

          case R.id.btn_mode_solid:
	  			bos.write(Color.red(actualColor));
	  			bos.write(Color.green(actualColor));
	  			bos.write(Color.blue(actualColor));
	  			mActivity.sendMessage(MyOpelActivity.MESSAGE_SOLID, bos.toByteArray());
	  		break;

          case R.id.btn_mode_fade:

	  			bos.write(Color.red(actualColor));
	  			bos.write(Color.green(actualColor));
	  			bos.write(Color.blue(actualColor));
  				mActivity.sendMessage(MyOpelActivity.MESSAGE_FADING, bos.toByteArray());
	  		break;

	  }

  }  


  public void onRadioButtonClicked(View view) {
	    // Is the button now checked?
	    boolean checked = ((RadioButton) view).isChecked();
	    
	    Toast.makeText(getActivity(), "radio", Toast.LENGTH_SHORT).show();
	    
	    // Check which radio button was clicked
	    switch(view.getId()) {
	        case R.id.radio_disable_door_logic:
	            if (checked)
	            	
	            break;
	        case R.id.radio_enable_door_logic:
	            if (checked)

	            break;
	    }
	}  
  
}
