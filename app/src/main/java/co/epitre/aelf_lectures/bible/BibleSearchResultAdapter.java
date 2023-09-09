package co.epitre.aelf_lectures.bible;

import android.content.Context;
import android.database.Cursor;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import co.epitre.aelf_lectures.R;

public class BibleSearchResultAdapter extends RecyclerView.Adapter<BibleSearchResultAdapter.ViewHolder> {

    private Cursor mCursor;
    private String mQuery;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    // Cache indexes
    int mColBookIndex;
    int mColTitleIndex;
    int mColChapterIndex;
    int mColSnippetIndex;
    int mColSkippedIndex;

    BibleSearchResultAdapter(Context context, @NonNull Cursor cursor, @NonNull String query) {
        this.mInflater = LayoutInflater.from(context);
        this.mCursor = cursor;
        this.mQuery = query;

        if (mCursor.getCount() > 0) {
            mCursor.moveToPosition(0);
            this.mColBookIndex = mCursor.getColumnIndex("book");
            this.mColTitleIndex = mCursor.getColumnIndex("title");
            this.mColChapterIndex = mCursor.getColumnIndex("chapter");
            this.mColSnippetIndex = mCursor.getColumnIndex("snippet");
            this.mColSkippedIndex = mCursor.getColumnIndex("skipped");
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_bible_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // FIXME: this is racy. On click listeners will move the cursor too
        mCursor.moveToPosition(position);

        String book = mCursor.getString(this.mColBookIndex);
        String title = mCursor.getString(this.mColTitleIndex);
        String chapter = mCursor.getString(this.mColChapterIndex);
        String preview = mCursor.getString(this.mColSnippetIndex);
        String skipped = mCursor.getString(this.mColSkippedIndex);

        String reference = ""+book+" "+chapter;
        String link = "https://www.aelf.org/bible/"+book+"/"+chapter+"?query="+this.mQuery;

        if (book.equals("Ps")) {
            reference = "Ps "+chapter;
        }

        if (!skipped.isEmpty()) {
            preview += "<br/><br/><i>Terme manquant: <s>"+skipped+"</s></i>";
        }

        holder.titleTextView.setText(title);
        holder.referenceTextView.setText(reference);
        holder.snippetTextView.setText(Html.fromHtml(preview));
        holder.link = link;
    }

    @Override
    public int getItemCount() {
        return mCursor.getCount();
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onItemClick(String link);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        TextView titleTextView;
        TextView referenceTextView;
        TextView snippetTextView;
        String link;

        ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.result_title);
            referenceTextView = itemView.findViewById(R.id.result_reference);
            snippetTextView = itemView.findViewById(R.id.result_snippet);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            // Forward event
            if (mClickListener != null) mClickListener.onItemClick(link);
        }
    }
}