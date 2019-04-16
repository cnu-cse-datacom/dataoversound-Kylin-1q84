package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;

import java.util.ArrayList;
import java.util.List;

import calsualcoding.reedsolomon.EncoderDecoder;
import google.zxing.common.reedsolomon.ReedSolomonException;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone(){
        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();
    }

    public void PreRequest() throws InterruptedException {
        //decode
        EncoderDecoder RS = new EncoderDecoder();
        int blocksize = findPowerSize((int) (long) Math.round(interval / 2 * mSampleRate));
        short[] buffer = new short[blocksize];
        boolean in_packet = false;
        List<Double> packet = new ArrayList<Double>();

        while(true) {
            Thread.sleep(44L);
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);
            double[] buf = new double[blocksize];
            for(int i = 0 ; i<blocksize;i++){
                buf[i] = (double) buffer[i];
            }
            double dom = findFrequency(buf);
            if(dom>800)
                Log.d("ListenDom : ", Double.toString(dom));

            if(match(dom, HANDSHAKE_END_HZ) && in_packet){
                List<Byte> byte_stream = extract_packet(packet);
                byte[] decode_stream = new byte[byte_stream.size()];
                String Decode_ErrorString = "";
                for(int i = 0 ; i < byte_stream.size() ; i ++){
                    decode_stream[i] = byte_stream.get(i).byteValue();
                }
                try {
                    byte[] decode = RS.decodeData(decode_stream,4);
                    String Decode_String = "";
                    for(int i = 0 ; i < byte_stream.size(); i ++){
                        Decode_String += Character.toString((char) decode[i]);
                    }
                    Log.d("Decode String : ", Decode_String);
                }
                catch (Exception e){
                    for(int i = 0 ; i < byte_stream.size() -4 ; i ++){
                        Decode_ErrorString += Character.toString((char) byte_stream.get(i).byteValue());
                    }
                }
                packet = new ArrayList<Double>();
                in_packet = false;
                Log.d("Decode Error String : ", Decode_ErrorString);
            }
            else if(in_packet){
                if(dom > 800){
                    packet.add(new Double(dom));
                }
            }
            else if(match(dom, HANDSHAKE_START_HZ)){
                in_packet = true;
                packet = new ArrayList<Double>();
            }
        }
    }
    private List<Byte> extract_packet(List<Double> freqs){
        List<Integer> bit_chunks = new ArrayList<Integer>();

        for(int i = 2; i< freqs.size();i+=2){
            double is_near = (freqs.get(i).doubleValue() -START_HZ) % STEP_HZ;
            int t = (int) ((freqs.get(i).doubleValue() - START_HZ) / STEP_HZ);
            int temp = Math.round(( is_near > (STEP_HZ/2)) ?  t+1:t);
            if( temp > 0 && temp < 16) bit_chunks.add(temp);
            Log.d("Packet : ", Integer.toString(temp));
        }
        return decode_bitchunks(bit_chunks);
    }
    private List<Byte> decode_bitchunks(List<Integer> bit){
        List<Byte> out_bytes = new ArrayList<Byte>();
        int temp = 0;
        for(int i = 0 ; i< bit.size();i++){
            if( (i%2) == 1) {
                temp += bit.get(i).intValue();
                Log.d("Packet temp : ", Integer.toString(temp));
                out_bytes.add((byte) temp);
                temp = 0;
                continue;
            }
            temp = (byte) (bit.get(i).byteValue() * 16);
        }
        return out_bytes;
    }
    private int findPowerSize(int size){
        int i = 0;
        double former = 0;
        while(true) {
            former =Math.pow(2,i);
            if(former > size)
                break;
            i++;
        }
        double latter = Math.pow(2,i-1);

        return (int) ((former - size) > (size - latter) ? latter: former);
    }

    private boolean match(double freq1, double freq2) {
        return Math.abs(freq1 - freq2) <= 20;
    }
    private double findFrequency(double[] toTransform){
        int len = toTransform.length;
        double realNum;
        double imgNum;
        double[] mag = new double[len];
        int peak_coeff = 0;

        //fourier transform을 이용해서 얻은 무게중심의 배열
        Complex[] complx = transform.transform(toTransform,TransformType.FORWARD);

        Double[] freq = fftfreq(complx.length,1);
        for(int i =0; i<complx.length;i++){
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum*realNum)+(imgNum*imgNum));
            peak_coeff = mag[i] > mag[peak_coeff] ? i : peak_coeff;
        }

        return Math.abs(freq[peak_coeff] * mSampleRate);
    }

    private Double[] fftfreq(int length, int d) {
        Double[] fftfreq = new Double[length];
        double temp = 0;
        double count = 1.0/length;
        for(int i = 0 ; i < length; i ++){
            if(temp == 0.5)
                temp = temp - 1;
            fftfreq[i] = new Double(temp);
            temp += count;

        }
        return fftfreq;
    }

}
