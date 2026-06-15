package com.capstone.eventticketing.ui.tickets;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.capstone.eventticketing.R;
import com.capstone.eventticketing.data.model.Ticket;
import com.capstone.eventticketing.databinding.ItemTicketBinding;
import com.capstone.eventticketing.util.QrGenerator;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the user's tickets. Each row shows a small QR thumbnail; tapping a row
 * opens the full-screen scannable QR. Thumbnails are cached by payload to avoid
 * re-encoding on rebind.
 */
public class TicketAdapter extends ListAdapter<Ticket, TicketAdapter.TicketViewHolder> {

    public interface OnTicketClickListener {
        void onTicketClick(@NonNull Ticket ticket);
    }

    private static final int THUMB_SIZE_PX = 220;

    @NonNull private final OnTicketClickListener listener;
    @NonNull private final Map<String, Bitmap> qrCache = new HashMap<>();

    public TicketAdapter(@NonNull OnTicketClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    @NonNull
    @Override
    public TicketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTicketBinding binding = ItemTicketBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TicketViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull TicketViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class TicketViewHolder extends RecyclerView.ViewHolder {
        private final ItemTicketBinding binding;

        TicketViewHolder(@NonNull ItemTicketBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull Ticket ticket) {
            binding.tvSeat.setText(binding.getRoot().getContext()
                    .getString(R.string.tickets_seat_prefix, ticket.getSeatNumber()));

            if (ticket.isCheckedIn()) {
                binding.tvStatus.setText(R.string.tickets_status_used);
                binding.tvStatus.setTextColor(
                        binding.getRoot().getContext().getColor(R.color.slate_400));
            } else {
                binding.tvStatus.setText(R.string.tickets_status_valid);
                binding.tvStatus.setTextColor(
                        binding.getRoot().getContext().getColor(R.color.seat_available));
            }

            String payload = ticket.getQrCodeData();
            if (payload != null) {
                Bitmap qr = qrCache.get(payload);
                if (qr == null) {
                    qr = QrGenerator.generate(payload, THUMB_SIZE_PX);
                    if (qr != null) qrCache.put(payload, qr);
                }
                binding.ivQrThumb.setImageBitmap(qr);
            }

            binding.getRoot().setOnClickListener(v -> listener.onTicketClick(ticket));
        }
    }

    private static final DiffUtil.ItemCallback<Ticket> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Ticket>() {
                @Override
                public boolean areItemsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
                    return oldItem.getTicketId() != null
                            && oldItem.getTicketId().equals(newItem.getTicketId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Ticket oldItem, @NonNull Ticket newItem) {
                    return oldItem.isCheckedIn() == newItem.isCheckedIn();
                }
            };
}