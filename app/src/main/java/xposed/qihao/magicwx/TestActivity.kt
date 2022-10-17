package xposed.qihao.magicwx

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xposed.qihao.magicwx.ui.theme.MagicWXTheme

/**
 * Doc说明 (此类核心功能):
 * @date on 2022/10/17 20:16
 * +--------------------------------------------+
 * | @author qihao                              |
 * | @GitHub https://github.com/Pangu-Immortal  |
 * +--------------------------------------------+
 */

class TestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MagicWXTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    Greeting(this,Message("Android", "Jetpack Compose"))
                }
            }
        }
    }
}

@Composable
fun Greeting(context: Context, msg: Message) {
    Column {
        Row(
            modifier = Modifier
                .size(500.dp, 200.dp)
                .background(color = Color.Green),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = "Contact profile picture",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, color = Color.Magenta, CircleShape)
                    .padding(5.dp)
            )
            Spacer(
                modifier = Modifier.width(5.dp)
            )
            Column(Modifier.padding(5.dp)) {
                Text(text = msg.author)
                Text(text = msg.body)
            }
        }
        Text(
            text = msg.author,
            fontSize = 32.sp, textAlign = TextAlign.Center,
            modifier = Modifier
                .align(alignment = Alignment.CenterHorizontally)
                .padding(start = 2.dp, end = 2.dp, top = 4.dp),
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                Toast.makeText(context, "点击了……", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(alignment = Alignment.CenterHorizontally),
            border = BorderStroke(
                5.dp,
                Brush.radialGradient(listOf(Color.Yellow, Color.Blue))
            ),
            content = {
                Text(
                    text = "广告测试",
                    color = Color.Green,
                    fontSize = 32.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp, top = 4.dp),
                )
            },//内容
            contentPadding = PaddingValues(10.dp),//当内容过长的时候才会有效，
            enabled = true,//设置按钮是否可用
            shape = CutCornerShape(10), //切角按钮
        )

        Spacer(Modifier.height(5.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MagicWXTheme {
        Greeting(LocalContext.current, Message("Android", "Jetpack Compose"))
    }
}

data class Message(val author: String, val body: String)