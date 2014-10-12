package com.jay.rtspclient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import com.FFmpeg.ffmpeg;


import android.util.Log;


public class H264Decoder implements Runnable {

	private volatile int leftSize = 0;
	private final int width;
	private final int height;
	private ffmpeg codec;
	private final Object mutex = new Object();
	private volatile Thread runner;
	private byte[] rawdata = new byte[40980];
	private byte[] mPixel ;
	private List<byte[]> mDecodeList;
	private FileOutputStream fout;

	public H264Decoder(int width,int height) {
		super();
		this.height=height;
		this.width = width;
		codec = new ffmpeg();
		//File file =new File("/sdcard/test1.h264");
//		try {
//			fout=new FileOutputStream(file);
//		} catch (FileNotFoundException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		int ret=codec.Init(this.width,this.height);
		if(ret==-1)
			Log.e("H264Decoder","FFMPEG LIB INIT ERROR");
		mPixel =new byte[width*height*2];
		mDecodeList = new ArrayList<byte[]>();
	}

	 public void startThread(){
			if(runner == null){
			    runner = new Thread(this);
			    runner.start();
			  }
		}
		
		public void stopThread(){
			 if(runner != null){
				    Thread moribund = runner;
				    runner = null;
				    moribund.interrupt();
			  }
		}

	public void run() {

		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);

		int getSize = 0;
		while (Thread.currentThread() == runner) {

			synchronized (mutex) {
				while (isIdle()) {
					try {
						mutex.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			synchronized (mutex) {		
				byte[] temp = new byte[leftSize];
				System.arraycopy(rawdata, 0, temp, 0, leftSize);
//				try {
//					fout.write(temp);
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				getSize = codec.DecoderNal( temp, leftSize,mPixel);
				if(getSize>0)
				{
					mDecodeList.add(mPixel);
					Log.d("FFmpeg","Decoder:"+ getSize);
		//			mDecodeList.add(mPixel);
				}
				setIdle();
			}
		}
	}

	public void putData(byte[] data,int size) {
		synchronized (mutex) {
			System.arraycopy(data, 0, rawdata, 0, size);
			this.leftSize = size;
			mutex.notify();
		}
	}
	
	public boolean isGetData()
	{
		return mDecodeList.size() == 0 ?false : true; 
	}
	
	public byte[] getData(){
		return mDecodeList.remove(0);
	}

	public boolean isIdle() {
		return leftSize == 0 ? true : false;
	}

	public void setIdle() {
		leftSize = 0;
	}
	
	public void free(){
		codec.Destroy();
		try {
			fout.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
