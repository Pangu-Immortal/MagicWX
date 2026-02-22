#!/bin/bash
##############################################################################
# MagicWX 批量自动化测试脚本 v2
#
# 核心改进：
# - 使用 grep -F 固定字符串匹配，避免特殊字符问题
# - 查找可点击父节点的 bounds（而非文本节点本身）
# - 增加操作间延时，适配 Compose 渲染
# - 支持按文本点击 + 按坐标范围点击两种模式
##############################################################################

ADB=~/Library/Android/sdk/platform-tools/adb
PKG="com.qihao.open.rwkv"
ACTIVITY="${PKG}/.MainActivity"
UI_XML="/sdcard/ui_test.xml"
LOCAL_XML="/tmp/ui_test.xml"
PASS=0; FAIL=0; TOTAL=0

G='\033[0;32m'; R='\033[0;31m'; Y='\033[1;33m'; N='\033[0m'

# ===== 工具函数 =====

dump_ui() {
    $ADB shell uiautomator dump "$UI_XML" >/dev/null 2>&1
    $ADB pull "$UI_XML" "$LOCAL_XML" >/dev/null 2>&1
}

# 检查文本是否存在
has_text() {
    grep -qF "text=\"$1\"" "$LOCAL_XML" 2>/dev/null
}

# 点击文本元素（通过文本匹配 bounds 并计算中心）
tap_text() {
    local text="$1"
    dump_ui
    sleep 0.3

    # 从本地 XML 中提取该文本所在节点的 bounds
    local line=$(grep -oF "text=\"${text}\"" "$LOCAL_XML" 2>/dev/null | head -1)
    if [ -z "$line" ]; then
        return 1
    fi

    # 用 python 精确解析 XML 获取坐标
    local coords=$(python3 -c "
import xml.etree.ElementTree as ET
import sys
tree = ET.parse('$LOCAL_XML')
target = '$text'
# 先找可点击的包含该文本子节点的元素
def find_clickable_parent(root, text):
    for node in root.iter('node'):
        if node.get('clickable') == 'true':
            for child in node.iter('node'):
                if child.get('text') == text:
                    return node.get('bounds')
    return None

# 回退：直接找文本节点
def find_text_node(root, text):
    for node in root.iter('node'):
        if node.get('text') == text:
            return node.get('bounds')
    return None

bounds = find_clickable_parent(tree.getroot(), target)
if not bounds:
    bounds = find_text_node(tree.getroot(), target)
if bounds:
    import re
    m = re.findall(r'\d+', bounds)
    cx = (int(m[0]) + int(m[2])) // 2
    cy = (int(m[1]) + int(m[3])) // 2
    print(f'{cx} {cy}')
" 2>/dev/null)

    if [ -z "$coords" ]; then
        return 1
    fi

    local x=$(echo "$coords" | awk '{print $1}')
    local y=$(echo "$coords" | awk '{print $2}')
    $ADB shell input tap "$x" "$y"
    sleep 1.5
    return 0
}

# 等待文本出现
wait_for_text() {
    local text="$1"; local timeout="${2:-15}"
    for i in $(seq 1 "$timeout"); do
        dump_ui
        if has_text "$text"; then return 0; fi
        sleep 1
    done
    return 1
}

# 在输入框输入文本
input_text() {
    # Compose 输入框需要先点击聚焦，然后用 adb 输入
    $ADB shell input text "$1"
    sleep 0.5
}

# 报告
report() {
    TOTAL=$((TOTAL+1))
    if [ "$2" = "PASS" ]; then
        PASS=$((PASS+1)); echo -e "  ${G}[PASS]${N} $1: $3"
    else
        FAIL=$((FAIL+1)); echo -e "  ${R}[FAIL]${N} $1: $3"
    fi
}

launch() {
    $ADB shell am force-stop "$PKG"
    sleep 0.5
    $ADB shell am start -n "$ACTIVITY" >/dev/null 2>&1
    sleep 3
}

# 获取界面上所有文本（调试用）
debug_texts() {
    dump_ui
    python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$LOCAL_XML')
for n in tree.getroot().iter('node'):
    t = n.get('text','')
    if t: print(f'  [{n.get(\"clickable\",\"\")}] {t}  {n.get(\"bounds\",\"\")}')
" 2>/dev/null
}

# ===== TC1: 模型选择界面 =====
tc1() {
    echo -e "\n${Y}=== TC1: 模型选择界面验证 ===${N}"
    launch
    wait_for_text "选择模型" 10
    dump_ui

    has_text "选择模型" && report "TC1.1" "PASS" "标题显示正确" || report "TC1.1" "FAIL" "标题缺失"
    has_text "选择一个模型开始对话" && report "TC1.2" "PASS" "提示文字显示正确" || report "TC1.2" "FAIL" "提示文字缺失"

    # 检查可见的模型卡片数量
    local count=$(python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$LOCAL_XML')
models = ['RWKV-7 World 0.4B','DeepSeek-R1 1.5B','Qwen3 0.6B','Gemma 3 1B','Phi-3 Mini 4K','Llama 3.2 1B','SmolLM2 360M','TinyLlama 1.1B','StableLM 2 1.6B','MiniCPM 2B']
found = 0
for n in tree.getroot().iter('node'):
    if n.get('text','') in models: found += 1
print(found)
" 2>/dev/null)

    # 滚动后再检查
    if [ "${count:-0}" -lt 10 ]; then
        $ADB shell input swipe 540 2000 540 800 500; sleep 1
        dump_ui
        count=$(python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$LOCAL_XML')
models = ['RWKV-7 World 0.4B','DeepSeek-R1 1.5B','Qwen3 0.6B','Gemma 3 1B','Phi-3 Mini 4K','Llama 3.2 1B','SmolLM2 360M','TinyLlama 1.1B','StableLM 2 1.6B','MiniCPM 2B']
found = sum(1 for n in tree.getroot().iter('node') if n.get('text','') in models)
print(found)
" 2>/dev/null)
        $ADB shell input swipe 540 800 540 2000 500; sleep 0.5
    fi

    [ "${count:-0}" -ge 8 ] && report "TC1.3" "PASS" "找到 ${count}/10 个模型卡片" || report "TC1.3" "FAIL" "仅找到 ${count}/10 个模型"
}

# ===== TC2: 逐个模型下载确认界面 =====
tc2() {
    echo -e "\n${Y}=== TC2: 10个模型下载确认界面 ===${N}"

    local -a names=("RWKV-7 World 0.4B" "DeepSeek-R1 1.5B" "Qwen3 0.6B" "Gemma 3 1B" "Phi-3 Mini 4K" "Llama 3.2 1B" "SmolLM2 360M" "TinyLlama 1.1B" "StableLM 2 1.6B" "MiniCPM 2B")
    local idx=0

    # 先滚回顶部
    $ADB shell input swipe 540 400 540 2200 500; sleep 0.5
    $ADB shell input swipe 540 400 540 2200 500; sleep 0.5

    for model in "${names[@]}"; do
        idx=$((idx+1))

        # 确保在选择界面
        dump_ui
        if ! has_text "选择模型"; then
            # 尝试各种方式返回
            tap_text "返回选择" 2>/dev/null || tap_text "返回" 2>/dev/null || {
                launch; wait_for_text "选择模型" 10
            }
        fi

        # 滚动到可见
        dump_ui
        if ! has_text "$model"; then
            $ADB shell input swipe 540 2000 540 800 500; sleep 1
            dump_ui
            if ! has_text "$model"; then
                $ADB shell input swipe 540 2000 540 600 500; sleep 1
            fi
        fi

        # 点击模型
        if tap_text "$model"; then
            sleep 1
            dump_ui
            if has_text "下载模型" && has_text "开始下载"; then
                report "TC2.${idx}" "PASS" "${model} → 下载确认界面正确"
            elif has_text "正在加载" || has_text "加载模型" || has_text "发送"; then
                report "TC2.${idx}" "PASS" "${model} → 已下载，直接加载/聊天"
                # 返回选择
                wait_for_text "切换" 60
                tap_text "切换" 2>/dev/null
                sleep 1
            else
                report "TC2.${idx}" "FAIL" "${model} → 界面异常"
            fi

            # 返回选择界面
            dump_ui
            if has_text "返回选择"; then
                tap_text "返回选择"
            elif has_text "返回"; then
                tap_text "返回"
            fi
            sleep 1
        else
            report "TC2.${idx}" "FAIL" "${model} → 卡片未找到"
        fi

        # 滚回顶部（每4个一次）
        if [ $((idx % 4)) -eq 0 ]; then
            $ADB shell input swipe 540 400 540 2200 500; sleep 0.5
        fi
    done
}

# ===== TC3: 返回导航（使用未下载模型测试） =====
tc3() {
    echo -e "\n${Y}=== TC3: 导航返回功能 ===${N}"
    dump_ui
    if ! has_text "选择模型"; then launch; wait_for_text "选择模型" 10; fi

    # 使用未下载的模型来测试导航（DeepSeek-R1 肯定未下载）
    local nav_model="DeepSeek-R1 1.5B"

    # 测试顶部返回按钮
    dump_ui
    if ! has_text "$nav_model"; then
        $ADB shell input swipe 540 800 540 2000 500; sleep 0.5  # 先滚回顶部
    fi
    tap_text "$nav_model"
    sleep 1
    dump_ui
    if has_text "下载模型"; then
        tap_text "返回"
        sleep 1; dump_ui
        has_text "选择模型" && report "TC3.1" "PASS" "顶部'返回'按钮正常" || report "TC3.1" "FAIL" "'返回'未生效"
    else
        report "TC3.1" "FAIL" "未进入下载确认（可能已下载）"
    fi

    # 测试底部返回选择按钮
    dump_ui
    if ! has_text "选择模型"; then launch; wait_for_text "选择模型" 10; fi
    dump_ui
    if ! has_text "$nav_model"; then
        $ADB shell input swipe 540 800 540 2000 500; sleep 0.5
    fi
    tap_text "$nav_model"
    sleep 1; dump_ui
    if has_text "返回选择"; then
        tap_text "返回选择"
        sleep 1; dump_ui
        has_text "选择模型" && report "TC3.2" "PASS" "'返回选择'按钮正常" || report "TC3.2" "FAIL" "'返回选择'未生效"
    else
        report "TC3.2" "FAIL" "下载确认界面无'返回选择'"
    fi
}

# ===== TC4: 下载流程（使用最小模型 SmolLM2 360M 测试，260MB） =====
tc4() {
    echo -e "\n${Y}=== TC4: 下载功能验证（SmolLM2 360M, 260MB） ===${N}"
    dump_ui
    if ! has_text "选择模型"; then launch; wait_for_text "选择模型" 10; fi

    # 滚动到 SmolLM2 360M（可能在下方）
    dump_ui
    if ! has_text "SmolLM2 360M"; then
        $ADB shell input swipe 540 2000 540 800 500; sleep 1
        dump_ui
    fi

    # 检查是否已下载
    dump_ui
    # 点击 SmolLM2 模型
    if tap_text "SmolLM2 360M"; then
        sleep 1; dump_ui

        if has_text "加载模型" || has_text "正在加载" || has_text "发送"; then
            report "TC4.1" "PASS" "SmolLM2 已下载，直接进入加载"
            report "TC4.2" "PASS" "已下载模型直接加载"
            return
        fi

        if has_text "开始下载"; then
            tap_text "开始下载"
            sleep 3; dump_ui
            if has_text "正在下载"; then
                report "TC4.1" "PASS" "进入下载进度界面"
            else
                report "TC4.1" "FAIL" "未进入下载界面"
                return
            fi

            # 等待下载完成（最多 10 分钟，260MB 足够）
            echo -e "  ${Y}等待下载完成（260MB）...${N}"
            for sec in $(seq 1 300); do
                dump_ui
                if has_text "正在加载" || has_text "加载模型" || has_text "发送" || has_text "重置"; then
                    report "TC4.2" "PASS" "下载完成"
                    return
                fi
                if has_text "出错" || has_text "失败" || has_text "下载失败"; then
                    report "TC4.2" "FAIL" "下载出错"
                    return
                fi
                [ $((sec % 30)) -eq 0 ] && echo -e "  ${Y}  已等 $((sec*2))s ...${N}"
                sleep 2
            done
            report "TC4.2" "FAIL" "下载超时（>10分钟）"
        else
            report "TC4.1" "FAIL" "下载确认界面异常"
        fi
    else
        report "TC4.1" "FAIL" "SmolLM2 卡片未找到"
    fi
}

# ===== TC5: 模型加载 + 聊天就绪 =====
tc5() {
    echo -e "\n${Y}=== TC5: 聊天界面验证 ===${N}"

    # 等待聊天界面
    if ! wait_for_text "发送" 120; then
        if ! wait_for_text "重置" 5; then
            dump_ui
            report "TC5.1" "FAIL" "模型加载超时，未进入聊天"
            return
        fi
    fi
    report "TC5.1" "PASS" "聊天界面就绪"

    dump_ui
    has_text "重置" && has_text "切换" && report "TC5.2" "PASS" "顶栏按钮（重置/切换）存在" || report "TC5.2" "FAIL" "顶栏按钮缺失"
    has_text "发送消息开始对话" && report "TC5.3" "PASS" "空消息提示存在" || report "TC5.3" "FAIL" "空消息提示缺失"
}

# ===== TC6: 发送消息 =====
tc6() {
    echo -e "\n${Y}=== TC6: 聊天发送功能 ===${N}"
    dump_ui
    if ! has_text "发送"; then report "TC6.1" "FAIL" "不在聊天界面"; return; fi

    # 找到输入框位置并点击
    local input_pos=$(python3 -c "
import xml.etree.ElementTree as ET, re
tree = ET.parse('$LOCAL_XML')
for n in tree.getroot().iter('node'):
    cls = n.get('class','')
    if 'EditText' in cls or 'TextField' in cls:
        b = n.get('bounds','')
        m = re.findall(r'\d+', b)
        if m: print(f'{(int(m[0])+int(m[2]))//2} {(int(m[1])+int(m[3]))//2}')
        break
# 回退：找 placeholder 文本
for n in tree.getroot().iter('node'):
    if n.get('text','') == '输入消息...' or n.get('hint','') != '':
        b = n.get('bounds','')
        m = re.findall(r'\d+', b)
        if m: print(f'{(int(m[0])+int(m[2]))//2} {(int(m[1])+int(m[3]))//2}')
        break
" 2>/dev/null | head -1)

    if [ -n "$input_pos" ]; then
        local ix=$(echo "$input_pos" | awk '{print $1}')
        local iy=$(echo "$input_pos" | awk '{print $2}')
        $ADB shell input tap "$ix" "$iy"
        sleep 0.5
    else
        # 回退：点击屏幕下方输入区域
        $ADB shell input tap 400 2250
        sleep 0.5
    fi

    # Compose TextField 需要先聚焦再输入
    sleep 0.5
    $ADB shell input text "hello"
    sleep 1
    dump_ui
    # 检查输入框是否有内容
    local has_input=$(python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$LOCAL_XML')
for n in tree.getroot().iter('node'):
    t = n.get('text','')
    if 'hello' in t.lower(): print('yes'); break
" 2>/dev/null)

    tap_text "发送"
    sleep 2

    dump_ui
    # 检查聊天消息中是否包含 hello
    local found=$(python3 -c "
import xml.etree.ElementTree as ET
tree = ET.parse('$LOCAL_XML')
for n in tree.getroot().iter('node'):
    t = n.get('text','')
    if 'hello' in t.lower(): print('yes'); break
" 2>/dev/null)

    [ "$found" = "yes" ] && report "TC6.1" "PASS" "用户消息显示" || report "TC6.1" "FAIL" "用户消息未显示"

    # 等一下看 AI 回复
    sleep 5
    dump_ui
    if has_text "停止"; then
        report "TC6.2" "PASS" "AI 正在生成（停止按钮出现）"
        tap_text "停止"; sleep 1
        dump_ui
        has_text "发送" && report "TC6.3" "PASS" "停止功能正常" || report "TC6.3" "FAIL" "停止后状态异常"
    elif has_text "发送"; then
        report "TC6.2" "PASS" "AI 回复已完成"
        report "TC6.3" "PASS" "发送按钮恢复"
    else
        report "TC6.2" "FAIL" "AI 回复异常"
        report "TC6.3" "FAIL" "按钮状态异常"
    fi
}

# ===== TC7: 重置 + 切换 =====
tc7() {
    echo -e "\n${Y}=== TC7: 重置与切换 ===${N}"
    dump_ui
    if ! has_text "重置"; then report "TC7.1" "FAIL" "不在聊天界面"; return; fi

    tap_text "重置"; sleep 1; dump_ui
    has_text "发送消息开始对话" && report "TC7.1" "PASS" "重置成功" || report "TC7.1" "FAIL" "重置未清空"

    tap_text "切换"; sleep 1; dump_ui
    has_text "选择模型" && report "TC7.2" "PASS" "切换回选择界面" || report "TC7.2" "FAIL" "切换失败"
}

# ===== TC8: 已下载标记 =====
tc8() {
    echo -e "\n${Y}=== TC8: 已下载标记验证 ===${N}"
    dump_ui
    if ! has_text "选择模型"; then launch; wait_for_text "选择模型" 10; fi

    dump_ui
    has_text "已下载" && report "TC8.1" "PASS" "已下载标记显示" || report "TC8.1" "FAIL" "无已下载标记"
}

# ===== TC9: 删除模型 =====
tc9() {
    echo -e "\n${Y}=== TC9: 删除功能 ===${N}"
    dump_ui
    if ! has_text "选择模型"; then launch; wait_for_text "选择模型" 10; fi

    dump_ui
    if has_text "删除"; then
        tap_text "删除"; sleep 1; dump_ui
        if ! has_text "已下载"; then
            report "TC9.1" "PASS" "删除成功，标记消失"
        else
            report "TC9.1" "FAIL" "删除后标记仍在"
        fi
    else
        report "TC9.1" "FAIL" "无删除按钮"
    fi
}

# ===== TC10: 日志检查 =====
tc10() {
    echo -e "\n${Y}=== TC10: 日志验证 ===${N}"
    local logs=$($ADB logcat -d -s MainViewModel:D ModelDownloader:D RWKVModel:D RWKVTokenizer:D RWKVApp:D 2>/dev/null | tail -80)

    echo "$logs" | grep -qF "RWKV Android 应用启动" && report "TC10.1" "PASS" "App启动日志" || report "TC10.1" "FAIL" "App启动日志缺失"
    echo "$logs" | grep -qE "已下载模型|选中模型|模型加载完成" && report "TC10.2" "PASS" "ViewModel日志" || report "TC10.2" "FAIL" "ViewModel日志缺失"
    echo "$logs" | grep -qE "模型资源已释放|对话已重置|切换模型" && report "TC10.3" "PASS" "生命周期日志" || report "TC10.3" "FAIL" "生命周期日志缺失"
}

# ===== 主流程 =====
echo -e "${Y}========================================${N}"
echo -e "${Y}  MagicWX 自动化测试 v2${N}"
echo -e "${Y}========================================${N}"

tc1; tc2; tc3; tc4; tc5; tc6; tc7; tc8; tc9; tc10

echo -e "\n${Y}========================================${N}"
echo -e "${Y}  测试报告${N}"
echo -e "${Y}========================================${N}"
echo -e "  总计: ${TOTAL}"
echo -e "  ${G}通过: ${PASS}${N}"
echo -e "  ${R}失败: ${FAIL}${N}"
if [ $TOTAL -gt 0 ]; then
    echo -e "  通过率: $(( PASS * 100 / TOTAL ))%"
fi
echo -e "${Y}========================================${N}"
