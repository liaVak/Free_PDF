package ru.ilyavakarin.pdfreader;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/** Read-only, narrow fixture provider in the test APK; never shipped with the reader. */
public final class ExternalPdfProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/pdf"; }
    private File fixture(Uri uri) {
        List<String> parts = uri.getPathSegments();
        if (parts.size() != 2 || !"fixtures".equals(parts.get(0))) throw new IllegalArgumentException("Invalid fixture URI");
        String name = parts.get(1);
        if (!"embedded.pdf".equals(name) && !"mixed.pdf".equals(name) && !"missing.pdf".equals(name)) {
            throw new IllegalArgumentException("Unknown fixture");
        }
        return new File(new File(getContext().getCacheDir(), "external"), name);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File file = fixture(uri);
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns);
        Object[] values = new Object[columns.length];
        for (int index = 0; index < columns.length; index++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[index])) values[index] = file.getName();
            if (OpenableColumns.SIZE.equals(columns[index])) values[index] = file.length();
        }
        cursor.addRow(values);
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read-only fixture provider");
        return ParcelFileDescriptor.open(fixture(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
