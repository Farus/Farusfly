package cl.farus.farusfly;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER);
    layout.setPadding(32,32,32,32);
    layout.setBackgroundColor(Color.rgb(14,31,39));
    TextView title = new TextView(this);
    title.setText("FarusFly"); title.setTextSize(32); title.setTextColor(Color.WHITE);
    layout.addView(title);
    TextView status = new TextView(this);
    status.setText("Versión de prueba 0.7.1\n\nAplicación Android iniciada correctamente.\n\nConexión DJI y seguimiento autónomo: no habilitados.");
    status.setTextSize(16); status.setTextColor(Color.rgb(180,225,215));
    layout.addView(status);
    setContentView(layout);
  }
}
