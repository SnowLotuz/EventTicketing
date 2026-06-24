package com.capstone.eventticketing.ui.seat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.capstone.eventticketing.data.model.Seat;
import com.capstone.eventticketing.data.model.SeatStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom zoomable/pannable seat map. Seats are rendered directly to the Canvas
 * (not as child Views) for performance at scale; pan/zoom is applied via a
 * Matrix and taps are hit-tested by inverting that matrix.
 *
 * <p>Colors: grey = booked/unavailable, green = available, accent = selected by
 * this user. The view is purely presentational — it reports taps via
 * {@link OnSeatTapListener} and the host decides what to do (hold/release).
 */
public class SeatMapView extends View {

    public interface OnSeatTapListener {
        void onSeatTapped(@NonNull Seat seat);
    }

    // --- Layout constants (px set in init from dp) ---
    private float seatSize;
    private float seatGap;
    private float rowLabelWidth;
    /** Horizontal aisle width inserted after certain columns. */
    private float aisleWidth;

    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 3.0f;

    // --- Colors (resolved from the design palette) ---
    private int colorAvailable;
    private int colorBooked;
    private int colorSelected;
    private int colorHeldOther;
    private int colorLabel;

    private final Paint seatPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Matrix transformMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private float currentScale = 1.0f;

    @NonNull private List<Seat> seats = new ArrayList<>();
    /** seatId -> its drawing rectangle in content (pre-matrix) coordinates. */
    @NonNull private final Map<String, RectF> seatRects = new HashMap<>();
    /** seatIds currently selected (held) by THIS user, for accent rendering. */
    @NonNull private final List<String> selectedSeatIds = new ArrayList<>();

    @Nullable private String currentUserId;
    @Nullable private OnSeatTapListener tapListener;

    private int contentWidth;
    private int contentHeight;

    public SeatMapView(Context context) {
        super(context);
        init(context);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SeatMapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = getResources().getDisplayMetrics().density;
        seatSize = 32 * density;
        seatGap = 8 * density;
        rowLabelWidth = 24 * density;
        aisleWidth = 20 * density; // gap roughly two-thirds of a seat

        // Palette — kept literal here so the view is self-contained; these match colors.xml.
        colorAvailable = Color.parseColor("#16A34A"); // seat_available green
        colorBooked = Color.parseColor("#CBD5E1");    // seat_booked grey
        colorSelected = Color.parseColor("#1D4ED8");   // accent_blue
        colorHeldOther = Color.parseColor("#94A3B8");  // slate_400 (held by another)
        colorLabel = Color.parseColor("#475569");      // slate_600

        labelPaint.setColor(colorLabel);
        labelPaint.setTextSize(14 * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setCurrentUserId(@Nullable String userId) {
        this.currentUserId = userId;
    }

    public void setOnSeatTapListener(@Nullable OnSeatTapListener listener) {
        this.tapListener = listener;
    }

    /** Supplies fresh seat data (e.g. from a SnapshotListener) and redraws. */
    public void setSeats(@NonNull List<Seat> seats) {
        this.seats = seats;
        computeSeatRects();
        invalidate();
    }

    /** Updates which seats this user currently holds (accent color) and redraws. */
    public void setSelectedSeatIds(@NonNull List<String> ids) {
        selectedSeatIds.clear();
        selectedSeatIds.addAll(ids);
        invalidate();
    }

    /**
     * Number of aisles that fall to the LEFT of the given 0-based column.
     * Cinema layout splits 15 columns into 4 | 7 | 4 blocks: an aisle after
     * column 4 (0-based index 3) and after column 11 (0-based index 10).
     */
    private int aislesBefore(int zeroBasedColumn) {
        int aisles = 0;
        if (zeroBasedColumn >= 4) aisles++;   // past the first 4-seat block
        if (zeroBasedColumn >= 11) aisles++;  // past the middle 7-seat block
        return aisles;
    }

    /** Builds the content-coordinate rectangle for each seat based on row/column. */
    private void computeSeatRects() {
        seatRects.clear();
        int maxColumn = 0;
        // Map distinct rows to vertical indices in encounter order.
        Map<String, Integer> rowIndex = new HashMap<>();
        int nextRow = 0;
        for (Seat seat : seats) {
            if (!rowIndex.containsKey(seat.getRow())) {
                rowIndex.put(seat.getRow(), nextRow++);
            }
            if (seat.getColumn() > maxColumn) maxColumn = seat.getColumn();
        }

        for (Seat seat : seats) {
            int r = rowIndex.get(seat.getRow());
            int c = seat.getColumn() - 1; // columns are 1-based

            float left = rowLabelWidth + c * (seatSize + seatGap)
                    + aislesBefore(c) * aisleWidth;          // aisle offset

            float top = (r + 1) * (seatSize + seatGap); // +1 leaves room for the column header
            seatRects.put(seat.getSeatId(), new RectF(left, top, left + seatSize, top + seatSize));
        }

        contentWidth = (int) (rowLabelWidth + maxColumn * (seatSize + seatGap)
                + 2 * aisleWidth);
        contentHeight = (int) ((nextRow + 1) * (seatSize + seatGap)); // +1 for the header band
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.concat(transformMatrix);

        // Column number headers (1..maxColumn) across the top.
        drawColumnHeaders(canvas);

        float corner = 6 * getResources().getDisplayMetrics().density;
        Map<String, String> rowLabelDrawn = new HashMap<>();

        for (Seat seat : seats) {
            RectF rect = seatRects.get(seat.getSeatId());
            if (rect == null) continue;

            seatPaint.setColor(resolveColor(seat));
            canvas.drawRoundRect(rect, corner, corner, seatPaint);

            // Draw the row label once per row, to the left of the first seat.
            if (!rowLabelDrawn.containsKey(seat.getRow())) {
                rowLabelDrawn.put(seat.getRow(), seat.getRow());
                float ly = rect.centerY() - ((labelPaint.descent() + labelPaint.ascent()) / 2f);
                canvas.drawText(seat.getRow(), rowLabelWidth / 2f, ly, labelPaint);
            }
        }
        canvas.restore();
    }

    /** Draws column numbers (1, 2, … N) above the first row of seats. */
    private void drawColumnHeaders(@NonNull Canvas canvas) {
        if (seats.isEmpty()) return;
        // Find how many columns exist by scanning the computed rects' columns.
        int maxColumn = 0;
        for (Seat seat : seats) {
            if (seat.getColumn() > maxColumn) maxColumn = seat.getColumn();
        }

        float headerY = (seatSize / 2f) - ((labelPaint.descent() + labelPaint.ascent()) / 2f);

        for (int c = 1; c <= maxColumn; c++) {
            float centerX = rowLabelWidth + (c - 1) * (seatSize + seatGap)
                    + aislesBefore(c - 1) * aisleWidth        // same offset as seats
                    + seatSize / 2f;
            canvas.drawText(String.valueOf(c), centerX, headerY, labelPaint);
        }
    }

    /** Chooses a seat's fill color from its state relative to the current user. */
    private int resolveColor(@NonNull Seat seat) {
        if (selectedSeatIds.contains(seat.getSeatId())) {
            return colorSelected;
        }
        if (SeatStatus.BOOKED.equals(seat.getStatus())) {
            return colorBooked;
        }
        if (seat.isActivelyHeldByOther(currentUserId)) {
            return colorHeldOther;
        }
        // AVAILABLE, or a lapsed hold the user can still grab.
        return colorAvailable;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }

    /** Converts a screen tap to content coordinates and finds the hit seat. */
    private void handleTap(float screenX, float screenY) {
        transformMatrix.invert(inverseMatrix);
        float[] pts = {screenX, screenY};
        inverseMatrix.mapPoints(pts);

        for (Seat seat : seats) {
            RectF rect = seatRects.get(seat.getSeatId());
            if (rect != null && rect.contains(pts[0], pts[1])) {
                if (tapListener != null) tapListener.onSeatTapped(seat);
                return;
            }
        }
    }

    private void clampScale() {
        if (currentScale < MIN_SCALE) currentScale = MIN_SCALE;
        if (currentScale > MAX_SCALE) currentScale = MAX_SCALE;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float prevScale = currentScale;
            currentScale *= factor;
            clampScale();
            float applied = currentScale / prevScale;
            transformMatrix.postScale(applied, applied, detector.getFocusX(), detector.getFocusY());
            invalidate();
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
            transformMatrix.postTranslate(-distanceX, -distanceY);
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent e) {
            handleTap(e.getX(), e.getY());
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true; // required so scroll/tap are delivered
        }
    }
}