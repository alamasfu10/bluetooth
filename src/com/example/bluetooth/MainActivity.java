package com.example.bluetooth;

import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.example.bluetooth.BluetoothService.ConnectedThread;

public class MainActivity extends Activity {

	//Debugging
	private static final boolean D = true;
	private static final String TAG = "Main Activity";

	//Views
   private ToggleButton toggleButton;
   private ListView lv;
   private EditText message;
   private Button send_button;
   
   //Local BT Adapter
   private BluetoothAdapter BA;
   
   //Paired devices
   private Set<BluetoothDevice>pairedDevices;
   
   //Context
   private Context ctx;
   
   //Object for the service
   private BluetoothService bluetoothService;
   
   private String connectedDeviceName;
      
   
   //Key names received from BluetoothService Handler
   public static final String BLUETOOTH_NAME = "Device Name";
   public static final String TOAST = "Toast";
   
   //Intent request codes
   private static final int REQUEST_ENABLE_BT=1;
   
   //Message types sent from BluetoothService Handler
   public static final int MESSAGE_DEVICE_NAME = 1;
   public static final int MESSAGE_TOAST = 2;
   public static final int MESSAGE_READ=3;
   public static final int MESSAGE_WRITE=4;
   public static final int MESSAGE_STATE_CHANGE=5;
   
   
   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      
      if(D) Log.d(TAG,"** ON CREATE **");
      
      setContentView(R.layout.activity_main);
      
      this.ctx=this;      
      
      BA = BluetoothAdapter.getDefaultAdapter();
      
      toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
      toggleButton.setChecked(BA.isEnabled());
      
      message = (EditText)findViewById(R.id.message);
      
      send_button = (Button) findViewById(R.id.send_button);
      send_button.setOnClickListener(new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			//Send message
			byte [] out = message.getText().toString().getBytes();
			bluetoothService.write(out);
		}
	});
      lv = (ListView)findViewById(R.id.list);
            
      lv.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> adapter, View v, int pos,long id) {
				// TODO Auto-generated method stub
				
				Object[] devices = pairedDevices.toArray();
				BluetoothDevice device = (BluetoothDevice) devices[pos];
							
				// Attempt to connect to the device
                if(bluetoothService.getState()!=bluetoothService.STATE_CONNECTED){
                	Log.d(TAG,"Trying to connect: Actual state: " + bluetoothService.getState());
                	bluetoothService.connect(device);
                }else{
                	String connected = "Already Connected";
                	//Toast.makeText(ctx, connected, Toast.LENGTH_SHORT).show();
                	ConnectedThread connectedThread = bluetoothService.getConnectedThread();	
                	
                	//Displays a message in both devices when the listitem is clicked
                	byte[] buffer = connected.getBytes();
                	connectedThread.write(buffer);
                }
				
				
			}
      });
      
   }

   //Enabling/Disabling BT
   public void onToggledClicked(View view){
	   
	   if(view==toggleButton){
		   boolean on = ((ToggleButton)view).isChecked();
		   if(!on){	
			  BA.disable();
		      
		      ArrayList<String> list = new ArrayList<String>();
		      list.clear();
		      final ArrayAdapter adapter = new ArrayAdapter
		    	      (this,android.R.layout.simple_list_item_1, list);
		      lv.setAdapter(adapter);
		      
		      lv.setClickable(false);
		      send_button.setClickable(false);
		   }
		   else{
			   if (!BA.isEnabled()) {
			         Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			         startActivityForResult(turnOn, REQUEST_ENABLE_BT);
			      }
			   
		   }
		   list(lv);
	   }
   }
   
   
   //Filling list with device's names
   public void list(View view){
      pairedDevices = BA.getBondedDevices();

      ArrayList<String> list = new ArrayList<String>();
      
      if((!BA.isEnabled())){
    	  list.add("Bluetooth disabled");
      }
      else if(pairedDevices.size()==0 ){
    	  list.add("No devices");
      }else{
    	  for(BluetoothDevice bt : pairedDevices)
	         list.add(bt.getName());
      }
      
      final ArrayAdapter adapter = new ArrayAdapter
      (this,android.R.layout.simple_list_item_1, list);
      lv.setAdapter(adapter);

   }
   
   
   
   public void onActivityResult(int requestCode, int resultCode, Intent data){
	   
	   	if (D) Log.d(TAG,"OnActivityResult" + resultCode);
	   
	   	switch (requestCode){
	   	case REQUEST_ENABLE_BT:
	   		if(resultCode == Activity.RESULT_OK){
	   			list(lv);
	   			if(bluetoothService == null && BA.isEnabled()){
	   			   
	   				lv.setClickable(true);
	   				send_button.setClickable(true);
	   				
	   			   //Initialize BluetoothService to perform BT Connections	   			   
	   			   bluetoothService = new BluetoothService(this, handler);
	   			}
	   		}else{
	   			Toast.makeText(this,"BT disabled",Toast.LENGTH_SHORT).show();
	   		}
	   		break;
	   	default:
	   		if(D) Log.d(TAG,"onActivityResult default");
	   		break;
	   	}
   }
   
   
   @Override
   public void onStart(){
	   super.onStart();
	   
	   if(D) Log.d(TAG, "** ONSTART **");
	   
	   list(lv);
	   
	   //Starts Bluetooth Service
	   if(bluetoothService == null && BA.isEnabled()){
		   bluetoothService = new BluetoothService(this, handler);
		   if(D) Log.d(TAG, "Initiating BluetoothService...");
	   }else if(!BA.isEnabled()){
		   Toast.makeText(this, "Please, enable BT", Toast.LENGTH_LONG).show();
	   }
   }
   
   @Override
   public synchronized void onResume(){
	   super.onResume();
	   
	   if(D) Log.d(TAG,"** ONRESUME **");
	   
	   if(!BA.isEnabled()){
		   lv.setClickable(false);
		   send_button.setClickable(false);
	   }
	   
	   if(bluetoothService != null){
		   //If no connection, start Bluetooth Service
		   if(D) Log.d(TAG,"Starting Bluetooth Service...");
		   if(bluetoothService.getState() == BluetoothService.STATE_NOCONNECTION){
			   bluetoothService.start();
		   }
	   }
   }
   
   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      // Inflate the menu; this adds items to the action bar if it is present.
      getMenuInflater().inflate(R.menu.main, menu);
      return true;
   }
   
   
   
   
   // The Handler that gets information back from the BluetoothChatService
   private final Handler handler = new Handler() {
       @Override
       public void handleMessage(Message msg) {
              	   
    	   switch (msg.what) {
    	   /*
            case MESSAGE_STATE_CHANGE:
               if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
               switch (msg.arg1) {
               case BluetoothService.STATE_CONNECTED:
                  mTitle.setText(R.string.title_connected_to);
                   mTitle.append(mConnectedDeviceName);
                   mConversationArrayAdapter.clear();
                   break;
               case BluetoothService.STATE_CONNECTING:
                   //mTitle.setText(R.string.title_connecting);
                   break;
               case BluetoothService.STATE_LISTEN:
               case BluetoothService.STATE_NOCONNECTION:
                   //mTitle.setText(R.string.title_not_connected);
                   break;
               }
               break;*/
           case MESSAGE_WRITE:
               byte[] writeBuf = (byte[]) msg.obj;
               // construct a string from the buffer
               String writeMessage = new String(writeBuf);
               Toast.makeText(getApplicationContext(), writeMessage,
                       Toast.LENGTH_SHORT).show();
               break;
           case MESSAGE_READ:
               byte[] readBuf = (byte[]) msg.obj;
               // construct a string from the valid bytes in the buffer
               String readMessage = new String(readBuf, 0, msg.arg1);
               Toast.makeText(getApplicationContext(), readMessage,
                       Toast.LENGTH_SHORT).show();
               break;/*
           case MESSAGE_DEVICE_NAME:
               // save the connected device's name
               connectedDeviceName = msg.getData().getString(BLUETOOTH_NAME);
               Toast.makeText(getApplicationContext(), "Connected to "
                              + connectedDeviceName, Toast.LENGTH_SHORT).show();
               break;*/
           case MESSAGE_TOAST:
        	   if (D) Log.d("HandleMessage","TOAST");
               Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                              Toast.LENGTH_SHORT).show();
               break;
           }
       }
   };

}
