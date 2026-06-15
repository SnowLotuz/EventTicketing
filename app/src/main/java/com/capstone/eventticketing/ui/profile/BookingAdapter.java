package com.capstone.eventticketing.ui.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Booking;
import com.capstone.eventticketing.data.repository.BookingRepository.BookingWithEvent;
import com.capstone.eventticketing.databinding.ItemBookingBinding;

import java.text.SimpleDateFormat;
import java.util.Locale;

/** Renders the user's booking history with event title and price breakdown. */
public class BookingAdapter extends ListAdapter<BookingWithEvent, BookingAdapter.VH> {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public BookingAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBookingBinding b = ItemBookingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VH(b);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position));
    }

    static class VH extends RecyclerView.ViewHolder {
        private final ItemBookingBinding b;
        VH(@NonNull ItemBookingBinding b) { super(b.getRoot()); this.b = b; }

        void bind(@NonNull BookingWithEvent item) {
            Booking booking = item.booking;
            b.tvEventTitle.setText(item.eventTitle);

            if (booking.getBookingDate() != null) {
                b.tvDate.setText(b.getRoot().getContext().getString(
                        R.string.booking_booked_on,
                        DATE_FORMAT.format(booking.getBookingDate().toDate())));
            } else {
                b.tvDate.setText("");
            }

            // Status chip styling.
            boolean cancelled = Booking.STATUS_CANCELLED.equals(booking.getStatus());
            b.chipStatus.setText(cancelled
                    ? R.string.booking_status_cancelled : R.string.booking_status_confirmed);
            b.chipStatus.setChipBackgroundColorResource(
                    cancelled ? R.color.slate_200 : R.color.accent_blue_light);
            b.chipStatus.setTextColor(b.getRoot().getContext().getColor(
                    cancelled ? R.color.slate_600 : R.color.accent_blue));

            // Amount paid.
            b.tvAmount.setText(money(booking.getFinalAmount()));

            // Promo line, only when a discount was applied.
            if (booking.getPromoCode() != null && !booking.getPromoCode().isEmpty()
                    && booking.getDiscountAmount() > 0d) {
                b.tvPromo.setVisibility(View.VISIBLE);
                b.tvPromo.setText(b.getRoot().getContext().getString(
                        R.string.booking_promo_saved,
                        booking.getPromoCode(),
                        money(booking.getDiscountAmount())));
            } else {
                b.tvPromo.setVisibility(View.GONE);
            }
        }

        private String money(double amount) {
            return String.format(Locale.getDefault(), "$%.2f", amount);
        }
    }

    private static final DiffUtil.ItemCallback<BookingWithEvent> DIFF =
            new DiffUtil.ItemCallback<BookingWithEvent>() {
                @Override
                public boolean areItemsTheSame(@NonNull BookingWithEvent a, @NonNull BookingWithEvent b) {
                    return a.booking.getBookingId() != null
                            && a.booking.getBookingId().equals(b.booking.getBookingId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull BookingWithEvent a, @NonNull BookingWithEvent b) {
                    return a.booking.getStatus() != null
                            && a.booking.getStatus().equals(b.booking.getStatus())
                            && a.eventTitle.equals(b.eventTitle);
                }
            };
}