package xposed.qihao.magicwx;

import android.content.Context;

import java.util.Date;
import java.util.Random;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * (核心功能)：
 *
 * @author qihao
 * @date on 2020/3/3 17:21
 */
public class BiliHook {

    private static ClassLoader classLoader;
    private static Object ebx;
    private static int a;

    public static void init(Context application) {
        classLoader = application.getClassLoader();

        Class ChatMessage = XposedHelpers.findClass("com.bilibili.bplus.im.entity.ChatMessage", classLoader);
        Class BaseTypedMessage = XposedHelpers.findClass("com.bilibili.bplus.im.business.model.BaseTypedMessage", classLoader);


        XposedHelpers.findAndHookMethod("b.egx", classLoader, "a",
                ChatMessage, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        LogUtil.d(a + ",参数 = " + param.args[0]);
                        if (a <= 5) {
                            sendText(ChatMessage);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod("b.ebr", classLoader, "a",
                ChatMessage, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        LogUtil.d("参数 = " + param.args[0]);
                        Object o = param.args[0];
                        int a = (int) XposedHelpers.callMethod(o, "getType");
                        try {
                            switch (a) {
                                case -1004:
                                case -1002:
                                case -1001:
                                    break;
                                case 1:
                                    LogUtil.d("文本消息 ：" + o);
                                    if (a <= 5) {
                                        sendText(ChatMessage);
                                    }
                                    break;
                                case 2:
                                case 6:
                                    break;
                                case 4:
                                    break;
                                case 5:
                                    break;
                                case 7:
                                    break;
                                case 9:
                                    break;
                                case 10:
                                    break;
                                case 11:
                                    break;
                                case 12:
                                    break;
                                case 100:
                                    break;
                                case 301:
                                case 302:
                                case 305:
                                case 306:
                                    break;
                                case 303:
                                case 304:
                                    break;
                                default:
                                    break;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });


        XposedHelpers.findAndHookConstructor(BaseTypedMessage, ChatMessage, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                LogUtil.d("参数1 = " + param.args[0]);
            }
        });

        Class<?> Conversation = XposedHelpers.findClass("com.bilibili.bplus.im.entity.Conversation", classLoader);
        XposedHelpers.findAndHookMethod("b.ebx", classLoader, "a",
                int.class, String.class, String.class, Conversation, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        LogUtil.d("参数1 = " + param.args[0]);
                        LogUtil.d("参数2 = " + param.args[1]);
                        LogUtil.d("参数3 = " + param.args[2]);
                        LogUtil.d("参数4 = " + param.args[3]);
                    }
                });


//        Class efa = XposedHelpers.findClass("b.efa", classLoader);
    }


    private static void sendText(Class ChatMessage) {

        LogUtil.d("开始构造发送消息……");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Class<?> ebx = XposedHelpers.findClass("b.ebx", classLoader);
                Object ebxo = XposedHelpers.callStaticMethod(ebx, "c");
                Class<?> eco = XposedHelpers.findClass("b.eco", classLoader);
                Object ecoo = XposedHelpers.callStaticMethod(eco, "b", 1, 287664);
                Class<?> ehn = XposedHelpers.findClass("b.ehn", classLoader);
                int SeqId = (int) XposedHelpers.callStaticMethod(ehn, "g");
//                                XposedHelpers.callMethod(ebxo, "a", -1004, "ceshi", "demo", ecoo);

                Object ChatMessageo = XposedHelpers.newInstance(ChatMessage);
                XposedHelpers.callMethod(ChatMessageo, "setSenderUid", 324237833);
                XposedHelpers.callMethod(ChatMessageo, "setConversationType", 1);
                XposedHelpers.callMethod(ChatMessageo, "setReceiveId", 35792848);
                XposedHelpers.callMethod(ChatMessageo, "setStatus", 1);
                XposedHelpers.callMethod(ChatMessageo, "setTimestamp", new Date());
                XposedHelpers.callMethod(ChatMessageo, "setContent", "{\"content\":\"yyyyyyy\"}");
                XposedHelpers.callMethod(ChatMessageo, "setClientSeqId", new Random(System.nanoTime()).nextInt());
                XposedHelpers.callStaticMethod(ebx, "a", 0, ecoo, ChatMessageo);

                XposedHelpers.callMethod(ebxo, "c", ChatMessageo);
                LogUtil.d(ChatMessageo);
                a++;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).run();
    }
}
