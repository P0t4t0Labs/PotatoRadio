package us.potatosaur.p0t4t0labs.potatoradio;

import java.util.zip.CRC32;

/**
 * PotatoPacket is the object we use to pass messages around
 */
public class PotatoPacket {
    public enum DataType { GEO, MESSAGE}

    public String data;
    public DataType type;
    public String crc;

    /**
     * @param type DataType of message
     * @param data Data to be contained in message
     * @throws Exception
     */
    public PotatoPacket(DataType type, String data) throws Exception{
        if (type == null)
            throw new Exception("Type must not be null!");
        if (data.trim().isEmpty())
            throw new Exception("Data must not be null or whitespace!");

        this.type = type;
        this.data = data;
        calculateCRC();
    }

    /**
     * Generate a PotatoPacket given a String
     * @param data String that has been received and decoded
     * @return
     * @throws Exception
     */
    public static PotatoPacket fromRxString(String data) throws Exception{
        DataType incType = DataType.values()[Integer.parseInt(data.substring(0, 1))];
        String incCrc = data.substring(1, 9);
        String incData = data.substring(9);
        PotatoPacket packet = new PotatoPacket(incType, incData);

        if (packet.crc != incCrc)
            throw new Exception("CRC mismatch! Given: " + incCrc + " Calculated: " + packet.crc);
        return packet;
    }

    /**
     * CRC is calculated for each packet by prepending the data with the type ordinal then
     * generating the CRC32 which has 1/(2^32) chance of having a false positive.
     *
     * Also, CRC32 is built in to Java.
     */
    private void calculateCRC() {
        String toBeEncoded = type.ordinal() + data;
        CRC32 crc = new CRC32();
        crc.update(toBeEncoded.getBytes());
        // Using this instead of Long.toHexString() as that trims leading zeros
        this.crc = String.format("%08X", crc.getValue());
    }

    /**
     * Generate Data to be sent over the air.  Order was chosen due to fixed length of Type
     * ordinal and Crc.  First 8 byte will be the hex CRC, next byte is Type ordinal, rest of it
     * is variable sized data;
     * @return
     */
    public String getTxString(){
        return crc + type.ordinal() + data;
    }

    /**
     * Generates bytes of TxString to be used by FSKEncoder
     * @return
     */
    public byte[] getTxBytes(){
        return getTxString().getBytes();
    }




}
