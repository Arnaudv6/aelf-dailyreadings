package co.epitre.aelf_lectures.lectures.data.cache;

import android.util.Pair;

import co.epitre.aelf_lectures.lectures.data.AelfDate;
import co.epitre.aelf_lectures.lectures.data.OfficeTypes;

public final class CacheEntryIndex extends Pair<String, String> {
    public CacheEntryIndex(String what_str, String when_str) {
        super(what_str, when_str);
    }

    public CacheEntryIndex(OfficeTypes what, AelfDate when) {
        this(what.toString(), when.toIsoString());
    }
}
