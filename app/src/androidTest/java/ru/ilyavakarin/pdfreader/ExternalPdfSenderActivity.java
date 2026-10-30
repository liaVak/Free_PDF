package ru.ilyavakarin.pdfreader;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Test-only external process; framework-only because target dependencies aren't in the test APK. */
public final class ExternalPdfSenderActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String name = getIntent().getStringExtra("fixture");
        if (!"embedded".equals(name) && !"mixed".equals(name) && !"missing".equals(name)) {
            throw new IllegalArgumentException("Unknown fixture");
        }
        File directory = new File(getCacheDir(), "external");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create fixtures");
        File file = new File(directory, name + ".pdf");
        if (!"missing".equals(name)) {
            try (InputStream input = getAssets().open("pdf/" + name + ".pdf");
                 FileOutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            } catch (IOException error) { throw new IllegalStateException(error); }
        } else file.delete();
        Uri uri = Uri.parse("content://" + getPackageName() + ".fixtures/fixtures/" + name + ".pdf");
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf");
        view.setPackage(getPackageName().substring(0, getPackageName().length() - ".test".length()));
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(view);
        finish();
    }
}
