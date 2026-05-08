package com.calldesk.audio;

public final class MuLaw {
    private static final int BIAS = 0x84;
    private static final int CLIP = 32635;
    private static final short[] DECODE_TABLE = createDecodeTable();

    private MuLaw() { }

    public static short decode(byte value) {
        return DECODE_TABLE[value & 0xff];
    }

    public static short[] decode(byte[] values) {
        short[] samples = new short[values.length];
        for (int i = 0; i < values.length; i++) samples[i] = decode(values[i]);
        return samples;
    }

    public static byte encode(short sample) {
        int pcm = sample;
        int sign = (pcm >> 8) & 0x80;
        if (sign != 0) pcm = -pcm;
        if (pcm > CLIP) pcm = CLIP;
        pcm += BIAS;
        int exponent = 7;
        for (int mask = 0x4000; exponent > 0 && (pcm & mask) == 0; exponent--, mask >>= 1) { }
        int mantissa = (pcm >> (exponent + 3)) & 0x0f;
        return (byte) ~(sign | (exponent << 4) | mantissa);
    }

    public static byte[] encode(short[] samples) {
        byte[] encoded = new byte[samples.length];
        for (int i = 0; i < samples.length; i++) encoded[i] = encode(samples[i]);
        return encoded;
    }

    public static byte[] toPcm16LittleEndian(short[] samples) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            pcm[i * 2] = (byte) samples[i];
            pcm[i * 2 + 1] = (byte) (samples[i] >>> 8);
        }
        return pcm;
    }

    private static short[] createDecodeTable() {
        short[] table = new short[256];
        for (int i = 0; i < table.length; i++) {
            int value = (~i) & 0xff;
            int sample = ((value & 0x0f) << 3) + BIAS;
            sample <<= (value & 0x70) >> 4;
            sample -= BIAS;
            table[i] = (short) ((value & 0x80) == 0 ? sample : -sample);
        }
        return table;
    }
}
