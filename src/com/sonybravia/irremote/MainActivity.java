package com.sonybravia.irremote;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Sony Bravia IR Remote - modern infrared remote for Sony Bravia TVs.
 *
 * Encodes commands using the Sony SIRC protocol (15-bit):
 *   [1 start bit][7-bit device address][8-bit command]
 * Carrier: 40 kHz. Header: 2400us mark / 600us space.
 *   bit1 = 1200us mark / 600us space, bit0 = 600us mark / 600us space.
 *
 * TV device address = 1. Command table below lists common Sony Bravia codes.
 * IR codes can vary by region / panel; edit CODES if a button does not work.
 *
 * ConsumerIrManager is accessed via reflection so the app does not depend on
 * the exact stub package layout of the build-time android.jar.
 */
public class MainActivity extends Activity {

    private Object mIrService;
    private Method mHasIrEmitter;
    private Method mTransmit;
    private Vibrator mVib;
    private TextView mStatus;

    /** Sony Bravia command table: label -> {deviceAddress, command}. */
    private static final int[][] CODES = {
        { 1, 132 }, // 0 Power      (0x84) - most common Sony TV power
        { 1,  73 }, // 1 Volume Up
        { 1,  74 }, // 2 Volume Down
        { 1,  32 }, // 3 Channel Up
        { 1,  33 }, // 4 Channel Down
        { 1,  69 }, // 5 Mute
        { 1,   9 }, // 6 Input
        { 1,  23 }, // 7 Display
        { 1,  70 }, // 8 Sound
        { 1, 139 }, // 9 Home
        { 1, 140 }, // 10 Back
        { 1, 141 }, // 11 Menu
        { 1, 142 }, // 12 Netflix
        { 1, 143 }, // 13 YouTube
    };

    private static final int IDX_POWER   = 0;
    private static final int IDX_VOL_UP  = 1;
    private static final int IDX_VOL_DN  = 2;
    private static final int IDX_CH_UP   = 3;
    private static final int IDX_CH_DN   = 4;
    private static final int IDX_MUTE    = 5;
    private static final int IDX_INPUT   = 6;
    private static final int IDX_DISPLAY = 7;
    private static final int IDX_SOUND   = 8;
    private static final int IDX_HOME    = 9;
    private static final int IDX_BACK    = 10;
    private static final int IDX_MENU    = 11;
    private static final int IDX_NETFLIX = 12;
    private static final int IDX_YOUTUBE = 13;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mStatus = findViewById(R.id.status);

        initIrService();

        boolean hasIr = irHasEmitter();
        if (!hasIr) {
            mStatus.setText(R.string.status_no_ir);
            mStatus.setTextColor(getColor(R.color.power_on));
        }

        bind(R.id.btn_power,   IDX_POWER, hasIr);
        bind(R.id.btn_vol_up,  IDX_VOL_UP, hasIr);
        bind(R.id.btn_vol_down,IDX_VOL_DN, hasIr);
        bind(R.id.btn_ch_up,   IDX_CH_UP, hasIr);
        bind(R.id.btn_ch_down, IDX_CH_DN, hasIr);
        bind(R.id.btn_mute,    IDX_MUTE, hasIr);
        bind(R.id.btn_input,   IDX_INPUT, hasIr);
        bind(R.id.btn_display, IDX_DISPLAY, hasIr);
        bind(R.id.btn_sound,   IDX_SOUND, hasIr);
        bind(R.id.btn_home,    IDX_HOME, hasIr);
        bind(R.id.btn_back,    IDX_BACK, hasIr);
        bind(R.id.btn_menu,    IDX_MENU, hasIr);
        bind(R.id.btn_netflix, IDX_NETFLIX, hasIr);
        bind(R.id.btn_youtube, IDX_YOUTUBE, hasIr);

        // Number pad 0-9
        for (int d = 0; d <= 9; d++) {
            int res = getResources().getIdentifier("btn_numpad_" + d, "id", getPackageName());
            final int digit = d;
            if (res != 0) {
                findViewById(res).setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) {
                        tap();
                        status("Digit " + digit);
                        if (irHasEmitter()) irSend(IDX_VOL_UP);
                    }
                });
            }
        }

        status(hasIr ? R.string.status_ready : R.string.status_no_ir);
    }

    private void initIrService() {
        try {
            // Look up ConsumerIrManager class by name (package varies by build)
            Class<?> irCls = Class.forName("android.hardware.consumerir.ConsumerIrManager");
            mHasIrEmitter = irCls.getMethod("hasIrEmitter");
            mTransmit = irCls.getMethod("transmit", int.class, int[].class);
            // getSystemService returns Object; no cast needed
            mIrService = getSystemService(Context.CONSUMER_IR_SERVICE);
        } catch (Exception e) {
            mIrService = null;
            mHasIrEmitter = null;
            mTransmit = null;
        }
    }

    private boolean irHasEmitter() {
        if (mIrService == null || mHasIrEmitter == null) return false;
        try {
            return (Boolean) mHasIrEmitter.invoke(mIrService);
        } catch (Exception e) {
            return false;
        }
    }

    private void irSend(int codeIdx) {
        if (mIrService == null || mTransmit == null) return;
        int[] code = CODES[codeIdx];
        int[] pattern = buildSirc15(code[0], code[1]);
        try {
            mTransmit.invoke(mIrService, 40000, pattern);
        } catch (Exception e) {
            // ignore
        }
    }

    private void bind(int resId, final int codeIdx, final boolean hasIr) {
        Button b = findViewById(resId);
        if (b == null) return;
        final String label = (String) b.getText();
        b.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                tap();
                status("Sending " + label);
                if (hasIr) irSend(codeIdx);
            }
        });
    }

    private void tap() {
        if (mVib != null) mVib.vibrate(20);
    }

    private void status(int resId) {
        mStatus.setText(resId);
    }

    private void status(String text) {
        mStatus.setText(text);
    }

    /**
     * Build a ConsumerIrManager pattern (alternating mark/space microseconds)
     * for a 15-bit SIRC frame: start + 7-bit address + 8-bit command.
     */
    private static int[] buildSirc15(int address, int command) {
        List<Integer> p = new ArrayList<>();
        p.add(2400); p.add(600);        // header
        p.add(1200); p.add(600);        // start bit = 1
        for (int i = 6; i >= 0; i--) {  // 7-bit address, MSB first
            boolean bit = (address & (1 << i)) != 0;
            p.add(bit ? 1200 : 600);
            p.add(600);
        }
        for (int i = 7; i >= 0; i--) {  // 8-bit command, MSB first
            boolean bit = (command & (1 << i)) != 0;
            p.add(bit ? 1200 : 600);
            p.add(600);
        }
        int[] out = new int[p.size()];
        for (int i = 0; i < p.size(); i++) out[i] = p.get(i);
        return out;
    }
}
