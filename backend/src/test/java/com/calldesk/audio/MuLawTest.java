package com.calldesk.audio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MuLawTest {
    @Test void decodesKnownG711Values() {
        assertThat(MuLaw.decode((byte) 0xff)).isZero();
        assertThat(MuLaw.decode((byte) 0x7f)).isZero();
        assertThat(MuLaw.decode((byte) 0x00)).isEqualTo((short) -32124);
        assertThat(MuLaw.decode((byte) 0x80)).isEqualTo((short) 32124);
    }

    @Test void roundTripsPcmWithinCompandingTolerance() {
        for (short value : new short[]{-30000, -10000, -1000, 0, 1000, 10000, 30000}) {
            assertThat((int) MuLaw.decode(MuLaw.encode(value))).isCloseTo((int) value, within(1800));
        }
    }
}
