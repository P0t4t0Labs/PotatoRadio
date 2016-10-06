package us.potatosaur.p0t4t0labs.potatoradio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mantz_it.rfanalyzer.AudioSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import bg.cytec.android.fskmodem.FSKConfig;
import bg.cytec.android.fskmodem.FSKDecoder;
import bg.cytec.android.fskmodem.FSKEncoder;

/**
 * Created by kris on 10/5/16.
 */

public class Transceiver {

    private static String LOGTAG = "Transceiver";

    private static Transceiver instance = null;
    private static Transceiver getInstance() {
        if (instance == null) {
            try {
                instance = new Transceiver();
            } catch (Exception e) {
                Log.e(LOGTAG, e.getMessage());
            }
        }
        return instance;
    }

    private FSKConfig  config  = null;
    private FSKEncoder encoder = null;
    private FSKDecoder decoder = null;
    private AudioTrack audioTrack = null;
    private List<TransceiverDataReceiver> listeners = new ArrayList<TransceiverDataReceiver>();

    /**
     * Transceiver provides interfaces to the fskmodem
     * @throws Exception
     * @see FSKConfig
     * @see FSKEncoder
     */
    private Transceiver () throws Exception{
        try {
            // minimodem --rx -R 29400 -M 7350 -S 4900 1225
            config = new FSKConfig(FSKConfig.SAMPLE_RATE_29400, FSKConfig.PCM_16BIT,
                    FSKConfig.CHANNELS_MONO, FSKConfig.SOFT_MODEM_MODE_4, FSKConfig.THRESHOLD_20P);
        } catch (IOException e) {
            Log.e(LOGTAG, "FSK Config Failed");
            throw e;
        }

        // Initialize FSK Decoder
        decoder = new FSKDecoder(config, new FSKDecoder.FSKDecoderCallback() {
            @Override
            public void decoded(byte[] newData) {
                final String text = new String(newData);
                if (text == null)
                    return;

                Log.e(LOGTAG, text);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        for (TransceiverDataReceiver l : listeners) {
                            l.gotData(text);
                        }
                    }
                });
            }
        });

        // Initialize FSK Encoder
        encoder = new FSKEncoder(config, new FSKEncoder.FSKEncoderCallback() {
            @Override
            public void encoded(byte[] pcm8, short[] pcm16) {
                if (config.pcmFormat == config.PCM_16BIT) {
                    //16bit buffer is populated, 8bit buffer is null

                    audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                            config.sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, pcm16.length*2,
                            AudioTrack.MODE_STATIC);

                    audioTrack.write(pcm16, 0, pcm16.length);

                    audioTrack.play();
                }
            }
        });
    }

    public static void addListener(TransceiverDataReceiver toAdd) {
        getInstance().listeners.add(toAdd);
    }

    public static void stop() {
        getInstance().decoder.stop();

        /*getInstance().encoder.stop();
        if (getInstance().audioTrack != null && getInstance().audioTrack.getPlayState() == AudioTrack.STATE_INITIALIZED)
        {
            getInstance().audioTrack.stop();
            getInstance().audioTrack.release();
        }*/
    }

    /**
     * Submit a PotatoPacket for transmission
     * @see PotatoPacket
     * @param potatoPacket
     * @return
     */
    public static void transmit(PotatoPacket potatoPacket){
        getInstance().encoder.appendData(potatoPacket.getTxBytes());
    }

    /**
     * Appends short[] data to the decoder signal
     * @param data
     */
    public static void receive(short[] data){
        getInstance().decoder.appendSignal(data);
    }

    public interface TransceiverDataReceiver {
        void gotData(String data);
    }
}