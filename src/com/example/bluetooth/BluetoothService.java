package com.example.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;


/**
 * 
 * @author alvarolamas
 *
 *	This class does all the work for setting up and managing Bluetooth 
 *	conections. It has three threads: one for listening, one for connecting
 *	and one for performing data transmisions
 */

public class BluetoothService {

	//Debbugging: enable D for debugging mode
	private static final String tag = "BlutoothService";
	private static final Boolean D = true;
	
	private BluetoothAdapter adapter;
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	private Handler handler;
	private int state;
	
	// Unique UUID for this app, generated by uuidenerator.net 
	public static final String NAME = "Bluetooth";
	public static final UUID MY_UUID = UUID.fromString("6ce23d3a-b35a-11e3-8669-425861b86ab6");
	
	
	//Connection state - ENUM
	public static final int STATE_NOCONNECTION = 0;
	public static final int STATE_LISTEN = 1;
	public static final int STATE_CONNECTING = 2;
	public static final int STATE_CONNECTED = 3;
	
	
	/**
	 * Constructor
	 */
	public BluetoothService(Context context,Handler handler){
	//public BluetoothService(Context context, Handler handler){
		this.handler = handler;
		this.adapter = BluetoothAdapter.getDefaultAdapter();
		setState(STATE_NOCONNECTION);
	}
	
	/**
	 * 
	 * Setter
	 * @param state
	 */
	private synchronized void setState (int state){
		
		if(D) Log.d(tag,"state: " + this.state + "->" + state);
		
		this.state = state;
	}
	
	/**
	 * Getter
	 */
	public synchronized int getState(){
		return this.state;
	}
	
	/**
	 * Start the BluetoothService. Start AcceptThread to begin LISTENING MODE.
	 * Called by the Activity onResume()
	 */
	public synchronized void start(){
		
		if(D) Log.d(tag,"Start");
		
		//Cancel any thread attempting to make a connection
		if(connectThread != null){
			connectThread.cancel();
			connectThread = null;
		}
		
		//Cancel any thread currently running a connection
		if(connectedThread != null){
			connectedThread.cancel();
			connectedThread = null;
		}
		
		//Start the thread to listen on a BluetoothServerSocket
		if(acceptThread==null){
			acceptThread = new AcceptThread();
			acceptThread.start();
		}
		setState(STATE_LISTEN);
	}
	
	
	/**
	 * Start the ConnectThread to initiate a connection to a device
	 */
	public synchronized void connect(BluetoothDevice device){
		
		if(D) Log.d(tag,"Connect to: " + device.getName());
		
		//Cancel any thread attempting to make a connection
		if(state == STATE_CONNECTING){	
			if(connectThread != null){
				connectThread.cancel();
				connectThread = null;
			}
		}
		
		//Cancel any thread currently running a connection
		if(connectedThread != null){
			connectedThread.cancel();
			connectedThread = null;
		}
		
		//Start the thread to connect with the give device
		connectThread = new ConnectThread(device);
		connectThread.start();
		setState(STATE_CONNECTING);
	}
	
	
	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 */
	public synchronized void connected(BluetoothSocket socket,
			BluetoothDevice device){
		
		if(D) Log.d(tag,"Connected to: " + device.getName());
		
		//Cancel the thread that completed the connection
		if(connectThread != null){
			connectThread.cancel();
			connectThread = null;
		}
		
		//Cancel any thread currently running a connection
		if(connectedThread != null){
			connectedThread.cancel();
			connectThread = null;
		}
		
			
		//Start the thread to manage the connection and perform data transmissions
		connectedThread = new ConnectedThread(socket);
		connectedThread.start();
		//Send the name of connected device back to the MainActivity
		/*Message msg = handler.obtainMessage(MainActivity.MESSAGE_DEVICE_NAME);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.BLUETOOTH_NAME, device.getName());
		msg.setData(bundle);
		handler.sendMessage(msg);
		*/
		setState(STATE_CONNECTED);
		
		/*
		//Cancel the accept thread: only connect to one device
		if(acceptThread != null){			
			//Cancelling before setState(STATE_CONNECTED) makes
			//AcceptThread going into accept() failed (in run())
			acceptThread.cancel();
			acceptThread = null;
		}*/
		
	}
	
	
	/**
	 * Stops all threads
	 */
	public synchronized void stop(){
		
		if(D) Log.d(tag,"stop");
		
		if(acceptThread != null){
			acceptThread.cancel();
			acceptThread = null;
		}
		if(connectThread != null){
			connectThread.cancel();
			connectThread = null;
		}
		if(connectedThread != null){
			connectedThread.cancel();
			connectedThread = null;
		}		
		
		setState(STATE_NOCONNECTION);
	}
	
	
	/**
	 * Write to ConnectedThread (unsynchronized)
	 */
	public void write(byte[]out){
		//Create temporary object
		ConnectedThread ct;
		
		//synchronize a copy of the connected thread
		synchronized(this){
			if(state!=STATE_CONNECTED) return;
			ct = connectedThread;
		}
		
		ct.write(out);		
	}

	
	/**
	 * Connection attemp failed
	 */
	public void connectionFailed(){
		
		if (D) Log.d(tag, "Connection Failed");
				
		setState(STATE_LISTEN);		
		
		//Send a failure message to MainActivity
		Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.TOAST, "Connection failed");
		msg.setData(bundle);
		handler.sendMessage(msg);
		
	}

	
	/**
	 * Connection lost
	 */
	public void connectionLost(){
		
		if(D) Log.d(tag, "Connection lost");
		
		setState(STATE_LISTEN);
		
		//Send a failure message to MainActivity
		/*Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(MainActivity.TOAST, "Unable to connect device");
		msg.setData(bundle);
		handler.sendMessage(msg);
		 */
	}

	
	
	
	
	/**
	 * 	This thread runs while listening for incoming connections. It behaves
	 *	like a server-side client. It runs until a connection is accepted 
	 *	or until cancelled.	
	 * @author alvarolamas
	 *
	 */
	private class AcceptThread extends Thread {

		private final BluetoothServerSocket serverSocket;
		
		
		/**
		 * Constuctor AcceptThread
		 */
		public AcceptThread(){
			
			BluetoothServerSocket tmp = null;
			
			//Create a new listening server socket
			try{				
				tmp = adapter.listenUsingRfcommWithServiceRecord(BluetoothService.NAME,
						BluetoothService.MY_UUID);
			}catch (IOException e){
				Log.e(tag,"listen() failed",e);			
			}//Null Pointer Exception
			this.serverSocket=tmp;
		}
		
		
		/**
		 * Run AcceptThread
		 */
		public void run(){
			
			setName("AcceptThread");
			
			if(D) Log.d(tag,"Begin acceptThread " + this);
						
			BluetoothSocket socket = null;
			
			//Listen to server socket if not connected
			while(state != STATE_CONNECTED){
				try{
					socket = serverSocket.accept();
				}catch(IOException e){
					Log.e(tag,"Socket closed: accept() failed",e);
					break;
				}		
				
				
				//If the connection is accepted
				if(socket != null){
					synchronized(BluetoothService.this){
						switch (state){
						case STATE_LISTEN:
						case STATE_CONNECTING:
							//Start the connected thread
							connected(socket, socket.getRemoteDevice());
							break;
							
						case STATE_CONNECTED:
							//Already connected
						case STATE_NOCONNECTION:
							//Not ready							
							//Terminate new socket
							try{
								socket.close();
								if (D) Log.d(tag,"socket.close()");
							}catch(IOException e){
								Log.e(tag,"Could not close socket",e);
							}
							break;
						}
					}
				}
			}
			if (D) Log.d(tag,"end acceptThread");
		}
		
		
		/**
		 * Cancel AcceptThread
		 */
		public void cancel(){
			
			if(D) Log.d(tag, "cancel " + this);
			
			try{
				serverSocket.close();
			}catch(IOException e){
				Log.e(tag,"close() of server socket failed",e);
			}
		}
	}

	
	
	
	
	/**
	 * 	This thread runs while attempting to make an outgoing connection
	 *	with a device. It runs either the connection succeds or fails.
	 *
	 */
	
	private class ConnectThread extends Thread {

		private final BluetoothSocket socket;
		private final BluetoothDevice device;
		
		
		/**
		 * Constructor ConnectThread
		 */
		public ConnectThread(BluetoothDevice device){
			
			this.device = device;			
			BluetoothSocket tmp = null;
			
			//Get a socket for a connection with the given device
			try{
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
			}catch(IOException e){
				Log.e(tag,"create() failed",e);
			}
			
			this.socket=tmp;
		}
		
		
		/**
		 * Run ConnectThread
		 */
		public void run(){
			setName("ConnectThread"); 
			
			if(D) Log.d(tag,"Begin ConnectThread " + this);
			 	 
			 //Cancelling discovery, slow down a connection
			 adapter.cancelDiscovery();
			 
			 //Make a connection to the BluetoothSocket
			 try{
				 socket.connect();
			 }catch(IOException e){
				 connectionFailed();
				 //Closing socket
				 try{
					 socket.close();
				 }catch(IOException ee){
					 Log.e(tag,"Unable to close socket during connection failure",ee);
				 }
				 
				 //When failure, restart listening mode
				 BluetoothService.this.start();
				 return;
			 }
			 
			 //Reset ConnectThread because we're made the connection 
			 synchronized(BluetoothService.this){
				 connectThread=null;
				 if (D) Log.d(tag, "Connect Thread = NULL");
			 }
			 
			 //Start the connected thread
			 
			 connected(socket, device);
			 
			 Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
			 Bundle bundle = new Bundle();
			 bundle.putString(MainActivity.TOAST, "Connected to : " + device.getName());
			 msg.setData(bundle);
			 handler.sendMessage(msg);
		}
		
		
		/**
		 * Cancel ConnectThread
		 */
		public void cancel(){
			
			if(D) Log.d(tag, "cancel" + this);
			
			try{
				socket.close();
			}catch(IOException e){
				Log.e(tag,"close() of connect socket failed",e);
			}
		}
	}

	
	
	
	
	/**
	 * 
	 * This thread runs during a connection with a remote device.
	 *	It handles all incoming and outcoming transmissions
	 *
	 */
	
	public class ConnectedThread extends Thread {
	
		private final BluetoothSocket socket;
		private final InputStream inStream;
		private final OutputStream outStream;
		
		
		/**
		 * Constructor ConnectedThread
		 */
		public ConnectedThread(BluetoothSocket socket){

			if(D) Log.d(tag, "create ConnectedThread");
			
			this.socket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;
			
			//Get the BluetoothSocket input and output Streams
			try{
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			}catch(IOException e){
				Log.e(tag,"temp sockets not created",e);
			}
			this.inStream = tmpIn;
			this.outStream = tmpOut;
			
			//Send a message
			String connect = "Devices connected";
			byte[] buffer = connect.getBytes();
			write(buffer);
			
		}
		
		
		/**
		 * Run ConnectedThread
		 */
		public void run(){
			
			if(D) Log.d(tag,"Begin ConnectedThread");
			
			//1Kb
			byte[] buffer = new byte[1024];
			int bytes;
			
			//Keeps listening to the inputStream while connected
			while(true){
				try{
					//Read from the InputStream
					bytes = inStream.read(buffer);
					
					//Send obtained data to Main Activity
					handler.obtainMessage(MainActivity.MESSAGE_READ, 
							bytes, -1, buffer).sendToTarget();
					
				}catch(IOException e){
					Log.e(tag,"Socket closed",e);
					connectionLost();
					break;
				}
				
			}
		}
		
		
		/**
		 * Cancel ConnectedThread
		 */
		public void cancel(){
			
			if(D) Log.d(tag, "cancel" + this);
			
			try{
				socket.close();
			}catch(IOException e){
				Log.e(tag,"close of connect socket failure",e);
			}
		}
		
		
		/**
		 * Write to the connected outstream of ConnectedThread
		 */
		public void write(byte[] buffer){
			try{
				outStream.write(buffer);
				
				//Share the sent message to the Main Activity
				handler.obtainMessage(MainActivity.MESSAGE_WRITE, 
						-1, -1, buffer).sendToTarget();
			}catch(IOException e){
				Log.e(tag,"Error during write",e);
			}
		}
	}	

	public ConnectedThread getConnectedThread(){
		return this.connectedThread;
	}
}

