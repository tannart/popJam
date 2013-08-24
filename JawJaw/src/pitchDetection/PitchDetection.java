package pitchDetection;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import utilities.LowPassFilter;
import utilities.Mode;
import utilities.Note;
import utilities.Pitch;
import utilities.AudioData;
import edu.emory.mathcs.jtransforms.fft.*;

public class PitchDetection{
	
	private int sampleSize = AudioData.SAMPLE_SIZE;
	private int frameSize = AudioData.WINDOW_SIZE;
	private int sampleRate = AudioData.SAMPLE_RATE;
	private int oversamplingRate = AudioData.OVERSAMPLING_RATE;
	private int stepSize = AudioData.STEP_SIZE;
	
	private double[] inputData = new double[sampleSize];
	private float[] fourierTarget = new float[frameSize*2];

	double[] window;
	double[] frequencyArray;
	double[] magnitudeArray;
	
	double[] trueFreqArray = new double[sampleSize];	
	double[] maxFreq = new double[frameSize];
	double[] maxAmp = new double[frameSize];
	double[] prevPhase = new double[sampleSize];	
	
	private float binSize = (float) sampleRate / (float) frameSize;
	private	Pitch pitch = new Pitch();
	private int indexHolder; //holds the index value for the maximum amplitude, allowing me to find the corresponding frequency
	private int numberOfSamples;
	private int a = 0;
	private AudioData output;
	
	public AudioData detect(AudioData input){
		
		int counter = 0;
		int stepSize = frameSize/oversamplingRate;
		int latency = frameSize - stepSize;
		double expectedPhaseDifference = 2*Math.PI* (double) stepSize/ (float) frameSize;//The average difference between phase values
		this.output = input;
		
		Note[] pitchArray;

		numberOfSamples = input.getNumberOfSamples();//numSampsToProcess
				
		frequencyArray = new double[inputData.length];
		magnitudeArray = new double[inputData.length];
		
		if(counter == 0) counter = latency;
		System.out.println("Latency: " + latency);
		System.out.println("Frame Size: " + frameSize);
		System.out.println("Step Size: " + stepSize);
		int marker = 0;
		
		for(int k = 0; k < numberOfSamples; k++)//loop to cover all the samples
		{		
				LowPassFilter filter = new LowPassFilter();
				fourierTarget[marker] = (float) filter.twoPointMovingAverageFilter(inputData[counter]);
				fourierTarget[marker] = (float) inputData[counter];
				
				counter++;
				marker++;
										
						if(marker>=frameSize)//read enough data to fill the FFT
						{		
							System.out.println("post reaching frame size " + counter);
								marker = 0;
								counter -= latency;
								window = new double[frameSize];
								
								for(int i = 0; i < frameSize; i++)//loop builds Hann window
								{
										window[i] = 0.5 * (1- Math.cos((2 * Math.PI * i)/(frameSize-1)));
								}
								
								for(int i = 0; i < frameSize; i++)//this loop applies the Hann window function to the data
								{
										fourierTarget[i] *= window[i];
								}
								
								double[] complexTarget = new double[frameSize*2];

								DoubleFFT_1D transform = new DoubleFFT_1D(frameSize);
								
								for(int i = 0; i < frameSize; i++){
									complexTarget[2*i] = fourierTarget[i];
									complexTarget[2*i+1] = 0;
								}
								
								transform.complexForward(complexTarget);

								//Now that the transform has been performed on the array, the analysis can begin
								for(int i = 0; i < frameSize/2; i++)//Analysis
								{		
										//deinterlacing
										double real = complexTarget[2*i];//odd numbers are real
										double imag = complexTarget[2*i+1];// even numbers are imaginary
										
										double magnitude = 2*Math.sqrt(real*real + imag*imag);//calculate magnitude of sine
										double phase  = Math.atan2(imag, real);//calculate phase of sine

										//calculation of phase difference for real frequency
										double holder = phase - prevPhase[i];//variable holder used as storage for true frequency calculations
										prevPhase[i] = phase;
										
										//subtraction of expected phase difference
										holder -= (double) i*expectedPhaseDifference;
										
										int qpd = (int) (holder/Math.PI);
										if(qpd >= 0) qpd += qpd&1;//bitwise operator concatenates single bit
										else qpd -= qpd&1;
										holder -= Math.PI*(double) qpd;
										
										holder = oversamplingRate*holder/(2*Math.PI);
										
										//computer the true frequency of the current partial
										double trueFreq = (double) i*binSize + (holder * binSize);
										
										if(trueFreq!= 0) trueFreqArray[a] = trueFreq;
										
										//store the magnitude and frequency
										magnitudeArray[i] = (float) magnitude;
										frequencyArray[i] = (float) trueFreq;
								}
								
								maxAmp[a] = maxArray(magnitudeArray);//saving the maximum values from each FFT frame
								maxFreq[a] = frequencyArray[indexHolder];//saving the corresponding frequency
								a++;
						}
						
		}
		
		pitchArray = new Note[a];
		
		for(int i = 0; maxFreq[i] != 0; i++)
		{
			System.out.println(maxFreq[i]);
				pitchArray[i] = pitch.getNote(maxFreq[i]);
		}
		
		System.out.println("Pitches Present: ");
		System.out.println(pitchArray.length);
		int i = 0;
		
		while(i!= a)
		{	
				if(pitch.getNote(maxFreq[i])!= null)
				{
						System.out.print(pitchArray[i].getPitch() + ", ");
				}	
				i++;
		}

		output.setPitchArray(pitchArray);
				
		return null;
						
	}
	
	public double mode(double[] modePitches)
	{
			Mode mode = new Mode();
			return mode.getMode(modePitches);
	}
	
	public double maxArray(double[] input){
		double max = 0;
		for(int i = 0; i < input.length; i++)
		{
				if(input[i] > max)
				{
						max = input[i];
						indexHolder = i;
				}	
		}
		
		return max;	
	}	
}
