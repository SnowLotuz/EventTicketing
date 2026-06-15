package com.capstone.eventticketing.ui.tickets;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.util.QrGenerator;

/**
 * Full-screen QR display for gate scanning. Forces maximum screen brightness
 * while visible so the code scans reliably under any conditions, and restores
 * the prior brightness automatically when dismissed (the window attribute is
 * scoped to this dialog's own window).
 */
public class QrDialogFragment extends DialogFragment {

    private static final String ARG_PAYLOAD = "arg_payload";
    private static final String ARG_SEAT = "arg_seat";
    private static final int QR_SIZE_PX = 800;

    @NonNull
    public static QrDialogFragment newInstance(@NonNull String payload, @NonNull String seat) {
        QrDialogFragment f = new QrDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PAYLOAD, payload);
        args.putString(ARG_SEAT, seat);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            Window window = dialog.getWindow();
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            applyMaxBrightness(window);
        }
    }

    /** Pins this window's brightness to maximum. Restored when the dialog closes. */
    private void applyMaxBrightness(@NonNull Window window) {
        WindowManager.LayoutParams params = window.getAttributes();
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL; // 1.0f
        window.setAttributes(params);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String payload = getArguments() != null ? getArguments().getString(ARG_PAYLOAD) : null;
        String seat = getArguments() != null ? getArguments().getString(ARG_SEAT) : "";

        ImageView ivQr = view.findViewById(R.id.iv_qr_full);
        TextView tvSeat = view.findViewById(R.id.tv_qr_seat);
        View btnClose = view.findViewById(R.id.btn_close);

        tvSeat.setText(getString(R.string.tickets_seat_prefix, seat));

        if (payload != null) {
            Bitmap qr = QrGenerator.generate(payload, QR_SIZE_PX);
            if (qr != null) ivQr.setImageBitmap(qr);
        }

        btnClose.setOnClickListener(v -> dismiss());
    }
}