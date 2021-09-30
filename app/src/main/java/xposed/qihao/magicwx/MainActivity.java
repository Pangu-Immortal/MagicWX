package xposed.qihao.magicwx;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        button = (Button) findViewById(R.id.button_hook);
        button.setOnClickListener(v -> Toast.makeText(this, toastMessage(), Toast.LENGTH_SHORT).show());
    }

    public String toastMessage() {
        return "我未被劫持";
    }


}
