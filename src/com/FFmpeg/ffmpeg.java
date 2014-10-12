package com.FFmpeg;


public class ffmpeg  {
	
	public ffmpeg() {
		load();
	}

	public int init(int width, int height) {
		return Init(width,height);
	}
	
	public void load() {
		try {
			System.loadLibrary("ffmpeg");
			System.loadLibrary("myffmpeg");
		} catch (Throwable e) {
			e.printStackTrace();
		}

	}
	public void close(){
		Destroy();
	}

	 public native int Init(int width, int height);
	 public native int Destroy(); 
	 public native int DecoderNal(byte[] in, int insize, byte[] out);
	
}