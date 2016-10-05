package us.potatosaur.p0t4t0labs.potatoradio;

import bg.cytec.android.fskmodem.FSKConfig;
import bg.cytec.android.fskmodem.FSKDecoder;
import bg.cytec.android.fskmodem.FSKEncoder;

/**
 * Created by kris on 10/5/16.
 */

public class Transceiver {
    private static FSKEncoder encoder = null;
    private static FSKDecoder decoder = null;

    /**
     * Transceiver provides interfaces to the fskmodem
     * @throws Exception
     * @see FSKConfig
     * @see FSKEncoder
     */
    public Transceiver (FSKEncoder encoder, FSKDecoder decoder) throws Exception{
        if (encoder == null)
            throw new Exception("The encoder parameter must not be null!");
        if (decoder == null)
            throw new Exception("The decoder parameter must not be null!");
        this.encoder = encoder;
        this.decoder = decoder;
    }


    /**
     * Submit a PotatoPacket for transmission
     * @see PotatoPacket
     * @param potatoPacket
     * @return
     */
    public static void transmit(PotatoPacket potatoPacket){
        encoder.appendData(potatoPacket.getTxBytes());
    }

    /**
     * Appends short[] data to the decoder signal
     * @param data
     */
    public static void receive(short[] data){
        decoder.appendSignal(data);
    }
}
