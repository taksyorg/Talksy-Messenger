package com.talksy.messenger.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.talksy.messenger.R;
import com.talksy.messenger.data.model.DeviceSession;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.VH> {

    private final List<DeviceSession> items;
    private final SimpleDateFormat fmt = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());

    public interface OnLongClick { void onLongClick(DeviceSession s); }
    private OnLongClick longClick;
    public void setOnLongClick(OnLongClick l) { this.longClick = l; }

    public SessionAdapter(List<DeviceSession> items) { this.items = items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DeviceSession s = items.get(position);
        h.device.setText(s.device);
        h.where.setText(s.whereText().isEmpty() ? h.where.getContext().getString(R.string.location_unknown) : s.whereText());
        h.time.setText(s.createdAtMillis > 0 ? fmt.format(s.createdAtMillis) : "");
        h.current.setVisibility(s.isCurrent ? View.VISIBLE : View.GONE);
        h.itemView.setOnLongClickListener(v -> { if (longClick != null) longClick.onLongClick(s); return true; });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView device, where, time, current;
        VH(View v) {
            super(v);
            device = v.findViewById(R.id.txt_device);
            where = v.findViewById(R.id.txt_where);
            time = v.findViewById(R.id.txt_time);
            current = v.findViewById(R.id.txt_current);
        }
    }
}
