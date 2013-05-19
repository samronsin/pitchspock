
package com.example.pitchspock;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import javazoom.jl.decoder.Decoder;

import com.google.common.io.LittleEndianDataInputStream;
import com.google.common.io.LittleEndianDataOutputStream;


import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.util.FloatMath;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MainActivity extends Activity {

	Button bspeed;
	Button bpitch;
	Button bwave;
	Button bplay;
	SeekBar seekpitch;
	TextView tv2;
	TextView tv3;
	ImageView wf;
	boolean ismodified = false;
	boolean endreached;
	float rate = 1;
	float snr = 0;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
    	bspeed = (Button) findViewById(R.id.go_speed);
    	bspeed.setOnClickListener(gospeed);
    	
    	bpitch = (Button) findViewById(R.id.go_pitch);
    	bpitch.setOnClickListener(gopitch);
    	
    	bplay = (Button) findViewById(R.id.play);
    	bplay.setOnClickListener(play);
    	
    	bwave = (Button) findViewById(R.id.plot_wave);
    	bwave.setOnClickListener(wave);
    	
    	seekpitch = (SeekBar) findViewById(R.id.seekBarPitch);
    	seekpitch.setOnSeekBarChangeListener(set_rate);
    	
		tv2 = (TextView) findViewById(R.id.textView2);
		tv2.setText(getString(R.string.rate, rate));
		
		wf = (ImageView) findViewById(R.id.waveForm);

		
//		tv3 = (TextView) findViewById(R.id.textView3);yo
//		tv3.setText(getString(R.string.snr, snr));
        
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    
    }
 
	File rootsd = Environment.getExternalStorageDirectory();

	private OnSeekBarChangeListener set_rate = new OnSeekBarChangeListener(){
	 	@Override
	 	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)	{
					
	 	}
	    @Override
	    public void onStartTrackingTouch(SeekBar seekBar) {}
	    @Override
	    public void onStopTrackingTouch(SeekBar seekBar) {

	    		int prog = seekpitch.getProgress();	    		
	    		int max = seekpitch.getMax();
	    		float x = (float)prog/(float)max;
	 			rate = 1/4 + x*(9*x/2 - 3/4);// = 1/4 (resp. 4) when x = 0 (resp. 1)
	 			//NB: very weird TODO: fix this (cast float)
	 			tv2.setText(getString(R.string.rate, rate));
	 			
	    }
	
	};
    
	
	private OnClickListener wave = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
		
			String path = rootsd.getAbsolutePath() + "/download/clar.wav";
		    float[] array = floatsOfMonoWav(path);
//		    float[] waveform = waveform(array,512,50);
			float[] window=sinebell(512,64);
		    float[][] S=stft(array,window,64);
//		    Bitmap bitmap = plotwf(waveform,50);
		    Bitmap bitmap = plotspec(S,50);
		    bitmap = Bitmap.createScaledBitmap(bitmap,512,100,false);
			wf.setImageBitmap(bitmap);

    	return;
		}	
	};
	
	
	
	private OnClickListener play = new OnClickListener() {
	
		@Override
		public void onClick(View v) {
		
			String playpath = ismodified?(rootsd.getAbsolutePath() + "/download/test1.wav"):(rootsd.getAbsolutePath() + "/download/clar.wav");
			File file = new File(playpath);
			Uri uri = Uri.fromFile(file);
			Decoder decoder = new Decoder();

			MediaPlayer mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			try{
				mediaPlayer.setDataSource(getApplicationContext(), uri);
				mediaPlayer.prepare();
				mediaPlayer.start();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
    	return;
		}	
	};

	
	private OnClickListener gopitch = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
		
			String path1 = rootsd.getAbsolutePath() + "/download/clar.wav";
			File file1 = new File(path1);

			FileInputStream fis = null;
			BufferedInputStream bis = null;

			try {
				fis = new FileInputStream(file1);
				bis = new BufferedInputStream(fis);
			} catch (FileNotFoundException e) {
					e.printStackTrace();
			}
			
			LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(bis);
			
				try {
					ledis.skip(44);//skip the header
				} catch (IOException e) {
					e.printStackTrace();
				}

			int buffersize = 1024*8;
			
			AudioTrack audioTrack = new AudioTrack(
				     AudioManager.STREAM_MUSIC,
				     16000,
				     AudioFormat.CHANNEL_CONFIGURATION_MONO,
				     AudioFormat.ENCODING_PCM_16BIT,
				     buffersize,
				     AudioTrack.MODE_STREAM);
				   
			audioTrack.play();
			
			endreached = false;
			int buffersizemod = (int)((float)buffersize/rate)+1;

			while(!endreached){
				
				float[] audioDataFloat = floatArrayFromLittleEndianData(ledis,buffersize,!endreached);
			
				float[] audioDataFloatPitched = naivePitch(audioDataFloat, 1/rate);
				short[] audioData = floatArraytoShort(audioDataFloatPitched);
				audioTrack.write(audioData, 0, buffersizemod);
				}
			audioTrack.stop();

	    	return;
		}

	};
	

	private OnClickListener gospeed = new OnClickListener() {
		
		@Override
		public void onClick(View v) {
			
			String path1 = rootsd.getAbsolutePath() + "/download/clar.wav";

		    int sr = srOfWav(path1);
		    short bps = bpsOfWav(path1);
		    		    
		    int winlen = 1024;//64 ms for 16000 Hz
		    int ola = 400;//40% overlap
		    int hop = winlen - ola;
		    
			File file1 = new File(path1);
			FileInputStream fis = null;
			BufferedInputStream bis = null;
			try {
				fis = new FileInputStream(file1);
				bis = new BufferedInputStream(fis);
				//NB: speeds up the reading process (factor ~10)
				//TODO: try buffers with java.nio since it allows LITTLE_ENDIAN setting
				
				
				
				
				//ok
				
			} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
			}
			//LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
			LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(bis);
			
			AudioTrack audioTrack = new AudioTrack(
				     AudioManager.STREAM_MUSIC,
				     sr,
				     AudioFormat.CHANNEL_CONFIGURATION_MONO,
				     AudioFormat.ENCODING_PCM_16BIT,
				     AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT),
				     AudioTrack.MODE_STREAM);
				   
			audioTrack.play();
			
			endreached = false;
			int initframecount = -2;
			int nextframeindex = 0;
			float floatframeindex;
			int initframeindex_;
			
			float alpha;
			int mov;

			short[] audioData;
			float[] window = sinebell(winlen,ola);
			float[] olaData = new float[ola];
			float[] windowedFloats0 = new float[winlen];
			float[] windowedFloats1 = new float[winlen];
			float[] freqFrame0 = new float[winlen];
			float[] freqFrame1 = new float[winlen];
			float[] finFreqFrame = new float[winlen];
			
			float[] finalFloats0 = new float[winlen];
			float[] finalFloats1 = new float[winlen];
			float[] rawAudioData = new float[hop];
			float[] rawAudioData0 = new float[winlen];
			float[] rawAudioData1 = new float[hop];
			
			FloatFFT_1D fft = new FloatFFT_1D(winlen);

			mov = 2;
			alpha = (float)1;
			initframeindex_ = 0;
			
			while(!endreached){
				
				switch(mov){
				
				case 0:{
					//same two frames used for final frame computation
					finFreqFrame = pvoc2(freqFrame0, freqFrame1, alpha,hop);
					finalFloats0 = finalFloats1;//save the last final windowed frame for ola final samples computation 
					finalFloats1 = finFreqFrame;
					fft.realInverse(finalFloats1, true);
					
					for(int i = 0; i < ola; i++) finalFloats1[i] += finalFloats0[hop+i];
					audioData = floatArraytoShort(finalFloats1);
					audioTrack.write(audioData, 0, hop);
					nextframeindex++;
					floatframeindex = ((float)nextframeindex)*rate;
					initframeindex_ = (int) floatframeindex;
					alpha = floatframeindex - (float)initframeindex_;
					mov = (initframeindex_ == initframecount - 1)?0:(initframeindex_ == initframecount)?1:2;					
					
				}

					break;
					
				case 1:{
					
					//move one frame forward
					windowedFloats0 = windowedFloats1;//save the last windowed raw frame
					finalFloats0 = finalFloats1;//save the last final frame
					rawAudioData = floatArrayFromLittleEndianData(ledis,hop,!endreached);//load a new raw frame
					initframecount++;
					
					for(int i = 0; i < ola; i++) windowedFloats1[i] = olaData[i];//complete the new frame with saved raw data 
	 				for(int i = 0; i < ola; i++) olaData[i] = rawAudioData[hop-ola+i];//update the raw data saved
					
					for(int i = 0; i < hop; i++) windowedFloats1[ola + i] = rawAudioData[i];
					windowedFloats1 = multFloatArray(windowedFloats1,window);
					
					freqFrame0 = freqFrame1;//load one old frequency frame
					freqFrame1 = windowedFloats1;
					fft.realForward(freqFrame1);//compute one new frequency frame

					finFreqFrame = pvoc2(freqFrame0, freqFrame1, alpha,hop);
					
					finalFloats1 = finFreqFrame;
					fft.realInverse(finalFloats1, true);

					for(int i = 0; i < ola; i++) finalFloats1[i] += finalFloats0[hop+i];
					audioData = floatArraytoShort(finalFloats1);
					
					audioTrack.write(audioData, 0, hop);
					
					nextframeindex++;
					floatframeindex = ((float)nextframeindex)*rate;
					initframeindex_ = (int) floatframeindex;
					
					alpha = floatframeindex - (float)initframeindex_;
					mov = (initframeindex_ == initframecount - 1)?0:(initframeindex_ == initframecount)?1:2;
				}
					break;
				
				case 2:{
					
					if(initframeindex_ > initframecount+1){
						try {
							ledis.skip(hop*2*(initframeindex_-initframecount-1));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							endreached = true;
						}
					}
					
					finalFloats0 = finalFloats1;//save the last final frame
					rawAudioData0 = floatArrayFromLittleEndianData(ledis,winlen,!endreached);
					rawAudioData1 = floatArrayFromLittleEndianData(ledis,hop,!endreached);
					//NB: second frame built with ola samples from rawAudioData0 and hop samples from rawAudioData1
					initframecount+=2;

					for(int i = 0; i < ola; i++) olaData[i] = rawAudioData1[hop-ola+i];//keep useful samples for next frame in olaData
					
					windowedFloats0 = multFloatArray(rawAudioData0,window);
					for(int i = 0; i < ola; i++) windowedFloats1[i] = rawAudioData0[winlen-ola+i];
					for(int i = 0; i < hop; i++) windowedFloats1[ola+i] = rawAudioData1[i];
					windowedFloats1 = multFloatArray(windowedFloats1,window);
					
					
					freqFrame0 = windowedFloats0;
					freqFrame1 = windowedFloats1;
					
					fft.realForward(freqFrame1);//compute two new frequency frames
					fft.realForward(freqFrame0);
					
					finFreqFrame = pvoc2(freqFrame0, freqFrame1, alpha,hop);
					finalFloats1 = finFreqFrame;
					fft.realInverse(finalFloats1, true);

					for(int i = 0; i < ola; i++) finalFloats1[i] += finalFloats0[hop+i];
					
					audioData = floatArraytoShort(finalFloats1);
					finalFloats0 = finalFloats1;
					audioTrack.write(audioData, 0, hop);
					
					nextframeindex++;
					floatframeindex = ((float)nextframeindex)*rate;
					initframeindex_ = (int) floatframeindex;
					alpha = floatframeindex - (float)initframeindex_;
					mov = (initframeindex_ == initframecount - 1)?0:(initframeindex_ == initframecount)?1:2;
					
				}
					break;
					
				}
				
			}
			
			audioTrack.stop();

        	return;
        	
		    //float[][] stft0 = stft(array1, window, ola);
		    //float[][] stft1 = pvoc_naive(stft0, rate);
		    //int nn = (int) (1+(float)n*rate);
		    //float[] array3 = istft(stft1, window, nn, ola);
		    //float snr = snr(array1,array3,n);
 			
        	//tv3.setText(getString(R.string.snr, snr));
			//String path3 = rootsd.getAbsolutePath() + "/download/test1.wav";
		    //writeMonoWav(array3,sr,bps,path3);

        	//String path2 = rootsd.getAbsolutePath() + "/download/test1.wav";
        	//writeMonoWav(array2,sr,bps,path2);

		    //ismodified = true;
		}
	};
    
    
    public float[] sinebell(int winlen, int ola) {
		//assumes ola <= winlen/2
    	
    	float window[] = new float[winlen];
		float Pi=(float) Math.PI;
		
		for (int i=0; i<ola; i++){
			window[i]= FloatMath.sin(Pi*(float)i/(2*(float)ola));
		}
		
		for (int i = ola; i < winlen - ola; i++){
			window[i]=1;
		}
		
		for (int i = winlen-ola; i < winlen;i++){
			window[i]= FloatMath.sin(Pi*(float)(winlen-i)/(2*(float)ola));
		}
		return window;
	}
    
    
    public float[] multFloatArray(float[] array1, float[] array2){
    	
    	for(int i = 0; i < array1.length; i++){
    		array1[i] = array1[i]*array2[i];
    	}
    	return array1;
    }

	public int stft_len(int n, int winlen, int ola)
	{
		int N = (int) FloatMath.floor(1+((float)(n-winlen))/(float)(winlen-ola));
		return N;
	}
	
	
	public float[][] stft(float[] x, float[] window, int ola)//, int nfft)
	{	
		int n = x.length;
		int winlen = window.length;
		int N = stft_len(n,winlen,ola);
		float[][] S = new float[N+2][winlen];//*nfft];//TODO: wtf nfft? (must be 1 for FFT computation)
		int hop = winlen-ola;
		FloatFFT_1D fft = new FloatFFT_1D(winlen);
		
		for (int m = 1; m < N+1; m++){
			for (int f = 0; f < winlen; f++){
				S[m][f] = window[f]*x[f+(m-1)*hop];
			}
		}
		
		for (int f=winlen-ola;f<winlen;f++)	// first frame
		{
			S[0][f]=x[f-hop]*window[f];
		}
		
		for (int f = 0; f < ola; f++) // last frame

		{
			S[N+1][f]=x[f+N*hop]*window[f];
		}
		for (int m = 0; m < N+2; m++)
		{
			fft.realForward(S[m]);
		}
		return S;
	}
	
	
	public float[] istft(float[][] S,float[] window, int n, int ola)
	{	
		int F = window.length;//F = winlen
		int N = S.length - 2;
		FloatFFT_1D fft = new FloatFFT_1D(F);
		float[] x = new float[n];//n is the length of the returned float[]; 
		int hop = F-ola;
		int winlen = F;
		int N1 = (int) FloatMath.floor((float)(n-F)/(float)hop);
		
		for (int m = 0; m < N+2; m++)
		{
			fft.realInverse(S[m],true);
		}
		
		for (int m = 1; m < N+1; m++)
		{
			for (int f = 0; f < F; f++)
			{
				x[f + (m-1)*hop] += S[m][f]*window[f];
			}
		}

		for (int f = winlen-ola; f < winlen; f++)// first frame
		{
			x[f-hop] += S[0][f]*window[f];
		}

		for (int f = 0; f < Math.min(ola,n-1-N1*hop); f++)// last frame
		{
			x[f+N1*hop] += S[N+1][f]*window[f];
		}
		return x;
	}

	
		public float[][] pvoc_naive(float[][] S, float r){
			
			int winlen = S[0].length;
			int initlen = S.length-2;
			int modilen = (int)((float)initlen*r);
			//int modilen2=(int) ((float) init.length*r);
			float[][] modS = new float[modilen+2][winlen];
			float mm1,mm2,m1,m2,phi1,phi2,m,phi;
			
			for(int i = 0; i < modilen-1 ; i++){
			
			 float initindf = (float)i/r;
			 int initind = (int) initindf;
			 float alpha = initindf - (float)initind;
			 for (int f=0;f < winlen;f+=2)
			 {
				 // attention le module se calcule comme
				 mm1 = S[initind][f]*S[initind][f]+ S[initind][f+1]*S[initind][f+1];
				 // idem au temps intind +1
			 	 mm2 = S[initind+1][f]*S[initind+1][f]+ S[initind+1][f+1]*S[initind+1][f+1];
			 	 m1 = FloatMath.sqrt(mm1);
			 	 m2 = FloatMath.sqrt(mm2);

			 	 m = FloatMath.sqrt(alpha*mm1+(1-alpha)*mm2);

			 	 //atan2 renvoie le module dans [-pi,pi] comme son nom ne l'indique pas
			 	 //phi1 = (float) Math.atan2((double) S[initind][f],(double) S[initind][f+1]);
			 	 //phi2=(float) Math.atan2((double) S[initind+1][f],(double) S[initind+1][f+1]);
			 	 //inversment on calcule real = module *cos(phi) et imag=module * sin(phi)
			 modS[i][f] = m*S[initind+1][f]/m2;
			 modS[i][f+1] = m*S[initind+1][f+1]/m2;
			 //NB: only phase of the first frame used!!!
			 //no phase interpolation/reconstruction!!!
			 //TODO: fix this
			 }
			 }
			 return modS;	
	}

		
		float[] pvoc2(float[] freqFrame0, float[] freqFrame1, float alpha,int hop){
			
			int winlen = freqFrame0.length;
			float mm0, mm1,m,phi0,phi1,phi,dphi,naivefreq,bestfreq;
			float[] finFreqFrame = new float[winlen];
			
			for (int f=0;f < winlen/2;f+=2)
			 {
				 // attention le module se calcule comme
				 mm0 = freqFrame0[f]*freqFrame0[f]+ freqFrame0[f+1]*freqFrame0[f+1];
				 // idem au temps intind +1
				 mm1 = freqFrame1[f]*freqFrame1[f]+ freqFrame1[f+1]*freqFrame1[f+1];

			 	 m = FloatMath.sqrt(alpha*mm0+(1-alpha)*mm1);

			 	//atan2 renvoie le module dans [-pi,pi] comme son nom ne l'indique pas
			 	phi0 = (float) Math.atan2((double) freqFrame0[f],(double) freqFrame0[f+1]);
			 	phi1 = (float) Math.atan2((double) freqFrame1[f],(double) freqFrame1[f+1]);
			 	dphi = phi1 - phi0;
			 	naivefreq = dphi/(2*(float)Math.PI *(float)hop);
			 	float k = (float)f*(float)hop/(float)winlen - dphi/(2*(float)Math.PI);
			 	if(k-FloatMath.floor(k) > 1/2) k++;//TODO: double check
			 	bestfreq = naivefreq + (float)k/(float)hop;
			 	phi = phi0 + alpha*bestfreq*2*(float)Math.PI;//TODO: figure out "phase advance" (phi += phi_advance?)
			 	
			 	finFreqFrame[f] = m*FloatMath.cos(phi);
			 	finFreqFrame[f+1] = m*FloatMath.sin(phi);
			 	
			 //TODO: fix this
			 }
		 	return finFreqFrame;

		}
	
	
	public float snr(float[] x, float[]y, int n)
	{
		float res=0;
		float energy=0;
		float z;
		float xx;
		for (int i=0;i<n;i++)
		{	xx = x[i];
			z=Math.abs(xx-y[i]);
			res+=z*z;
			energy += xx*xx; 
		}
		return res/energy;
	}
	
	
	public float[] naivePitch(float[] init, float r){
		
		int ini_len = init.length;
		int fin_len = (int)((float)ini_len*r)+1;
		float fin[] = new float[fin_len];
		
		for(int i = 0; i < fin_len-1 ; i++){
			
			float ini_ind_f = ((float)i)/r;
			
			int ini_ind = (int) Math.floor(ini_ind_f);
			
			float alpha = ini_ind_f - (float)ini_ind;
			//Log.d("alpha = ", String.valueOf(alpha));
			//System.out.print(alpha);
			fin[i]= (ini_ind < ini_len-1)?((1-alpha)*init[ini_ind]+(alpha)*init[ini_ind+1]):0;//alpha*init[initind];
			//1st order interpolation sounds bad for rate > 1 (time stretching)
			//TODO: fix this
		}
		fin[fin_len-1] = init[ini_len-1];
		
		return fin;
	}
	
	
	public short bpsOfWav(String path){
	    File file = new File(path);
	    short bps = 0;
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
				//0 bytes read
				ledis.skip(34);//go through the header of the wav file
				bps = ledis.readShort(); //bits per sample
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return bps;
	}
	
	public int srOfWav(String path){
	    File file = new File(path);
	    int sr = 0;
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
				//0 bytes read

				ledis.skip(24); //go through the header of the wav file
				sr = ledis.readInt(); //sample rate
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return sr;
	}
	
	public short nchanOfWav(String path){
	    File file = new File(path);
	    short nchan = 0;
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
				//0 bytes read

				ledis.skip(22); //go through the header of the wav file
				//22 bytes read
				nchan = ledis.readShort(); //sample rate
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return nchan;
	}
	
	
    public float[] floatsOfMonoWav(String path){
		
	    File file = new File(path);
    	float[] soundFloat = null;
		
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);

				ledis.skip(34);
				short bps = ledis.readShort(); //bits per sample
				//bytes read = 36

				ledis.skip(4);
				//byteread = 40

				int bytesize = ledis.readInt();
				//byteread = 44

				switch(bps){
				case 8:
					soundFloat = new float[bytesize];
					for(int i = 0; i < bytesize; i++) {
						byte val = ledis.readByte();
						soundFloat[i] = (float)val;
					}
					break;
				case 16: 
					soundFloat = new float[bytesize/2];
					for(int i = 0; i < bytesize/2; i++){
						short val = ledis.readShort();
						soundFloat[i] = (float)val;
						}
					break;
				case 32:
					soundFloat = new float[bytesize/4];
					for(int i = 0; i < bytesize/4; i++){
						int val = ledis.readInt();
						soundFloat[i] = (float)val;
						}
					break;
				default: throw new Exception("WARNING: non valid bits per sample rate (must be 8, 16 or 32)");
				}
				

			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else Toast.makeText(MainActivity.this, "Warning: non valid path", Toast.LENGTH_SHORT).show();

    return soundFloat;
    }
    
    public short[] shortsOfMonoWav(String path){
		
	    File file = new File(path);
    	short[] soundShort = null;
		
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
				//byteread = 0

				for(int i=1;i<=22;i++) ledis.readByte(); //go through the header of the wav file
				//byteread = 22
				short nchan = ledis.readShort();//number of channels
				if(nchan!=1) Toast.makeText(MainActivity.this, "Warning: non mono .wav file!", Toast.LENGTH_SHORT).show();
				//byteread = 24

				for(int i=25;i<=34;i++) ledis.readByte(); //go through the header of the wav file
				//byteread = 34

				short bps = ledis.readShort(); //bits per sample
				if(bps!=16) Toast.makeText(MainActivity.this, "Warning: non valid bitrate (must be 16)!", Toast.LENGTH_SHORT).show();
				//byteread = 36

				for(int i=37;i<=40;i++) ledis.readByte();//go through the header of the wav file
				//byteread = 40

				int bytesize = ledis.readInt();
				
				
				switch(bps){
				case 8:
					soundShort = new short[bytesize];
					for(int i = 0; i < bytesize; i++) {
						byte val = ledis.readByte();
						soundShort[i] = (short)val;
					}
					break;
				case 16: 
					soundShort = new short[bytesize/2];
					for(int i = 0; i < bytesize/2; i++){
						short val = ledis.readShort();
						soundShort[i] = (short)val;
						}
					break;
				case 32:
					soundShort = new short[bytesize/4];
					for(int i = 0; i < bytesize/4; i++){
						int val = ledis.readInt();
						soundShort[i] = (short)val;
						}
					break;
				default: throw new Exception("WARNING: non valid bits per sample rate (must be 8, 16 or 32)");
				}
				

			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else Toast.makeText(MainActivity.this, "Warning: non valid path", Toast.LENGTH_SHORT).show();

    return soundShort;
    }
    
    
    
    public float[][] floatsOfStereoWav(String path){
		
	    File file = new File(path);
    	float[][] soundFloat = null;
		
		if(file.isFile()){
			try{
				FileInputStream fis = new FileInputStream(file);
				LittleEndianDataInputStream ledis = new LittleEndianDataInputStream(fis);
				//byteread = 0

				for(int i=1;i<=22;i++) ledis.readByte(); //go through the header of the wav file
				//byteread = 22
				short nchan = ledis.readShort();//number of channels
				if(nchan!=2) Toast.makeText(MainActivity.this, "Warning: non mono .wav file!", Toast.LENGTH_SHORT).show();
				//byteread = 24

				for(int i=25;i<=34;i++) ledis.readByte(); //go through the header of the wav file
				//byteread = 34

				short bps = ledis.readShort(); //bits per sample
				if(bps!=16) Toast.makeText(MainActivity.this, "Warning: non valid bitrate (must be 16)!", Toast.LENGTH_SHORT).show();
				//byteread = 36

				for(int i=37;i<=40;i++) ledis.readByte();//go through the header of the wav file
				//byteread = 40

				int bytesize = ledis.readInt();
				soundFloat = new float[2][bytesize/2]; // new float[bytesize/2];TODO: fix this
				
				
				switch(bps){
				case 8:
					for(int i = 0; i < bytesize; i++) {
						byte val0 = ledis.readByte();
						soundFloat[0][i] = (float)val0;
						byte val1 = ledis.readByte();
						soundFloat[1][i] = (float)val1;
					}
					break;
				case 16: 
					for(int i = 0; i < bytesize/2; i++){
						short val0 = ledis.readShort();
						soundFloat[0][i] = (float)val0;
						short val1 = ledis.readShort();
						soundFloat[1][i] = (float)val1;

						}
					break;
				case 32:
					for(int i = 0; i < bytesize/4; i++){
						int val0 = ledis.readInt();
						soundFloat[0][i] = (float)val0;
						int val1 = ledis.readInt();
						soundFloat[1][i] = (float)val1;
						}
					break;
				default: throw new Exception("WARNING: non valid bits per sample rate (must be 8, 16 or 32)");
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		else Toast.makeText(MainActivity.this, "Warning: non valid path", Toast.LENGTH_SHORT).show();

    return soundFloat;
    }

    public short[] shortArrayFromLittleEndianData(LittleEndianDataInputStream ledis,int size, boolean test) {
    	//endreachedshouldbefalse
    	short[] shortarray = new short[size];
    	for(int i = 0; i < size; i++){
    		if(test){
    			try {
    				shortarray[i] = ledis.readShort();
    			}
				catch (IOException e) {
				endreached = true;
				test = false;
				}
    		}
    		
    		else{
    			shortarray[i] = (short)0;
    		}
    	}
    	return shortarray;
    }
    
    public float[] floatArrayFromLittleEndianData(LittleEndianDataInputStream ledis,int size, boolean test) {
    	//endreachedshouldbefalse
    	float[] floatarray = new float[size];
    	for(int i = 0; i < size; i++){
    		if(test){
    			try {
    				floatarray[i] = (float)ledis.readShort();
    			}
				catch (IOException e) {
				test = false;
				endreached = true;
				}
    		}
    		else{
    			floatarray[i] = (float)0;
    		}
    	}
    	return floatarray;
    }
    
    public short[] floatArraytoShort(float[] farray){
    	
    	int len = farray.length;
    	short[] sarray = new short[len];
    	for(int i = 0; i < len; i++) sarray[i] = (short)farray[i];
    	return sarray;
    }
    
    
    
    public void writeMonoWav(float[] soundFloat, int sr, short bps, String path){
    	
    	int size = soundFloat.length;
    	try {
    		File file = new File(path);
			FileOutputStream fos = new FileOutputStream(file);
			LittleEndianDataOutputStream ledos = new LittleEndianDataOutputStream(fos);
			
			ledos.writeByte(82);//ChunkID ("RIFF") BIG ENDIAN
			ledos.writeByte(73);//ChunkID ("RIFF")
			ledos.writeByte(70);//ChunkID ("RIFF")
			ledos.writeByte(70);//ChunkID ("RIFF")

			ledos.writeInt(2*size+36);//ChunkSize
			ledos.writeByte(87);//Format ("WAVE") BIG ENDIAN
			ledos.writeByte(65);//Format ("WAVE")
			ledos.writeByte(86);//Format ("WAVE")
			ledos.writeByte(69);//Format ("WAVE")

			ledos.writeByte(102);//Subchunk1ID ("fmt ") BIG ENDIAN
			ledos.writeByte(109);//Subchunk1ID ("fmt ")
			ledos.writeByte(116);//Subchunk1ID ("fmt ")
			ledos.writeByte(32);//Subchunk1ID ("fmt ")
			
			ledos.writeInt(16);//Subchunk1Size
			ledos.writeShort(1);//AudioFormat (PCM)
			ledos.writeShort(1);
			//NB: NumChannels = 1
			ledos.writeInt(sr);//SampleRate
			ledos.writeInt(2*1*sr);//ByteRate (SampleRate * NumChannels * BitsPerSample/8)
			ledos.writeShort(1*bps/8);//BlockAlign (NumChannels * BitsPerSample/8)
			ledos.writeShort(bps);//BitsPerSample
			ledos.writeByte(100);//Subchunk2ID ("data") BIG ENDIAN
			ledos.writeByte(97);//Subchunk2ID ("data")
			ledos.writeByte(116);//Subchunk2ID ("data")
			ledos.writeByte(97);//Subchunk2ID ("data")
			
			ledos.writeInt(2*size);//Subchunk2Size
			
			switch(bps){
			case 8:
				for(int i = 0; i < size; i++){
					ledos.writeByte((byte)soundFloat[i]);
				}
				break;
			case 16: 
				for(int i = 0; i < size; i++){
					ledos.writeShort((short)soundFloat[i]);
				}
				break;
			case 32:
				for(int i = 0; i < size; i++){
					ledos.writeInt((int)soundFloat[i]);
				}
				break;
			default: throw new Exception("WARNING: non valid bits per sample rate (must be 8, 16 or 32)");
			}
			
			ledos.flush();
			ledos.close();	
		}
		
		catch (Exception e) {
	       	e.printStackTrace();
		}
    	
    }
    
    
    public void writeStereoWav(float[][] soundFloat, int sr, short bps, String path){
    	
    	int size = soundFloat.length;
    	try {
    		File file = new File(path);
			FileOutputStream fos = new FileOutputStream(file);
			LittleEndianDataOutputStream ledos = new LittleEndianDataOutputStream(fos);
			
			ledos.writeByte(82);//ChunkID ("RIFF") BIG ENDIAN
			ledos.writeByte(73);//ChunkID ("RIFF")
			ledos.writeByte(70);//ChunkID ("RIFF")
			ledos.writeByte(70);//ChunkID ("RIFF")

			ledos.writeInt(2*size+36);//ChunkSize
			ledos.writeByte(87);//Format ("WAVE") BIG ENDIAN
			ledos.writeByte(65);//Format ("WAVE")
			ledos.writeByte(86);//Format ("WAVE")
			ledos.writeByte(69);//Format ("WAVE")

			ledos.writeByte(102);//Subchunk1ID ("fmt ") BIG ENDIAN
			ledos.writeByte(109);//Subchunk1ID ("fmt ")
			ledos.writeByte(116);//Subchunk1ID ("fmt ")
			ledos.writeByte(32);//Subchunk1ID ("fmt ")
			
			ledos.writeInt(16);//Subchunk1Size
			ledos.writeShort(1);//AudioFormat (PCM)
			ledos.writeShort(2);
			//NB: NumChannels = 2
			ledos.writeInt(sr);//SampleRate
			ledos.writeInt(sr*2*bps/8);//ByteRate (SampleRate * NumChannels * BitsPerSample/8)
			ledos.writeShort(2*bps/8);//BlockAlign (NumChannels * BitsPerSample/8)
			ledos.writeShort(bps);//BitsPerSample
			ledos.writeByte(100);//Subchunk2ID ("data") BIG ENDIAN
			ledos.writeByte(97);//Subchunk2ID ("data")
			ledos.writeByte(116);//Subchunk2ID ("data")
			ledos.writeByte(97);//Subchunk2ID ("data")
			
			ledos.writeInt(2*size);//Subchunk2Size
			
			switch(bps){
			case 8:
				for(int i = 0; i < size; i++){
					ledos.writeByte((byte)soundFloat[0][i]);
					ledos.writeByte((byte)soundFloat[1][i]);

				}
				break;
			case 16: 
				for(int i = 0; i < size; i++){
					ledos.writeShort((short)soundFloat[0][i]);
					ledos.writeShort((short)soundFloat[1][i]);

				}
				break;
			case 32:
				for(int i = 0; i < size; i++){
					ledos.writeInt((int)soundFloat[0][i]);
					ledos.writeInt((int)soundFloat[1][i]);

				}
				break;
			default: throw new Exception("WARNING: non valid bits per sample rate (must be 8, 16 or 32)");
			}
			
			
			ledos.flush();
			ledos.close();
			
		}
		
		catch (Exception e) {
	       	e.printStackTrace();
		}
    }


    public float[] waveform(float[] soundfloat,int n, int m)
    {	// assumes m <= n << soundfloat.length
    	//TODO: take the file as an input
    	int size = soundfloat.length;
    	float[] waveform = new float[n+1];
    	float tmp;
    	float val;
    	float max = 0;
    		for(int i = 0; i < n; i++){
    			val = 0;
    			for(int j = 0; j < m; j++){
    				tmp = soundfloat[i*(size/n) + j];
    				val += tmp*tmp;
    			}
    			waveform[i] = val;
    			max = (val>max)?val:max;
    		}
    		waveform[n] = max;
    	return waveform;
    }
    
    public Bitmap plotwf(float[] waveform, int hnx){
    	//height of the bitmap output nx = 2*hnx +1
    	int nx = 2*hnx +1;
    	int ny = waveform.length - 1;//ny = width of the bitmap output
    	float max = waveform[ny];//maximal value of waveform is copied as extra value
        int[] pix = new int[nx*ny];
        int height;
        for (int y = 0; y < ny; y++){
        	height = (int) ((waveform[y]/max)*((float)(hnx-1)));
        	
        	for (int x = 0; x < hnx-height; x++){
        		
        		//plot white area
    			int index = x * ny + y;
    			pix[index] = 0xff000000 | (255 << 16) | (255 << 8) | 255;
        	}
        	
        	for (int x = hnx-height; x < hnx+height+1; x++){
        		
        		//plot colored area
    			int index = x * ny + y;
    			pix[index] = 0xff000000 | (0 << 16) | (0 << 8) | 255;
        	}
        	
        	for (int x = hnx+height+1; x < nx; x++){
        		
        		//plot white area
    			int index = x * ny + y;
    			pix[index] = 0xff000000 | (255 << 16) | (255 << 8) | 255;
        	}
        	
    			//int index = (hnx-1) * ny + y;
    			//pix[index] = 0xff000000 | (0 << 16) | (0 << 8) | 0;
        		
        }
        	
         Bitmap bitmap = Bitmap.createBitmap(ny, nx, Bitmap.Config.ARGB_8888);
         bitmap.setPixels(pix, 0, ny, 0, 0, ny, nx);
         return bitmap;
         
    }
    
    
    public Bitmap plotspec(float[][] S, int hnx){

    	int N = S.length - 1;//ny = width of the bitmap output
    	int F=S[0].length;
    	int ny=F/2;
    	float c1,c2,mod;
    	float max=0;
    	double l10=Math.log(10);
        int[] pix = new int[N*ny];
        int red,blue;
        for (int n = 0; n < N; n++){
        	for (int f = 0; f < F; f+=2){
    			c1=Math.abs(S[n][f]);
    			c2=Math.abs(S[n][f+1]);
    			mod= c1*c1+c2*c2;
    			if (n==0 && f==0){max=mod;}
        		max=Math.max(mod, max);
        	}
        }
        for (int n = 0; n < N; n++){
        	for (int f = 0; f < F; f+=2){
    			int index = n + (F-f-1)/2*N;
    			c1=Math.abs(S[n][f]);
    			c2=Math.abs(S[n][f+1]);
    			mod= c1*c1+c2*c2;
    			double lambda= Math.log((double) mod/max)/l10;
    			if (lambda>0){red=255;blue=0;}
    			else
    			{
    				if (lambda<-9){red=0;blue=255;}
    				else
    				{
    					red=(int) (255*(lambda+9)/9);
    					blue=255-red;
    				}
    			}
    			pix[index]=0xff000000 | ( red<< 16) | (0 << 8) | blue;
        	}
        	        		
        }
        	
         Bitmap bitmap = Bitmap.createBitmap(N,ny, Bitmap.Config.ARGB_8888);
         bitmap.setPixels(pix, 0, N, 0, 0, N,ny);

         return bitmap;
         
    }

    

}
