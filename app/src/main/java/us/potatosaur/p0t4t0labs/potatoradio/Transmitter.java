package us.potatosaur.p0t4t0labs.potatoradio;

import bg.cytec.android.fskmodem.FSKConfig;
import bg.cytec.android.fskmodem.FSKDecoder;
import bg.cytec.android.fskmodem.FSKEncoder;

/**
 * Created by kris on 10/5/16.
 */

public class Transmitter {
    private static FSKEncoder encoder = null;

    /**
     * Transmitter provides interfaces to the fskmodem
     * @throws Exception
     * @see FSKConfig
     * @see FSKEncoder
     */
    public Transmitter (FSKEncoder encoder) throws Exception{
        if (encoder == null)
            throw new Exception("The encoder parameter must not be null!");
        this.encoder = encoder;
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
}
