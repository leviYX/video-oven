# Video Oven 烤肉机

Video Oven 是一个 Java 25 + Maven 构建的字幕处理工具。它可以从本地字幕文件或 YouTube 视频链接读取英文字幕，调用翻译服务生成中文字幕或中英双语字幕，最终输出 `.srt` 文件。

当前版本重点解决“把英文字幕翻译成中文/双语字幕”的核心流程。视频下载、字幕上传、字幕压制可以配合 `yt-dlp` 和 `ffmpeg` 完成。

## 功能

- 支持输入 `.srt` / `.vtt` 字幕文件。
- 支持直接传 YouTube URL，自动调用 `yt-dlp` 下载字幕。
- 支持输出中文字幕或中英双语字幕。
- 支持 DeepSeek 翻译。
- 支持 OpenAI 翻译。
- 提供 `fake` 翻译器，方便本地测试命令是否跑通。

## 环境要求

必须安装：

- JDK 25 或更高版本
- Maven

处理 YouTube 链接时需要：

- `yt-dlp`

下载视频、合并字幕轨或压制字幕时需要：

- `ffmpeg`

macOS 可以用 Homebrew 安装：

```bash
brew install openjdk maven yt-dlp ffmpeg
```

确认命令可用：

```bash
java -version
mvn -version
yt-dlp --version
ffmpeg -version
```

如果 `java -version` 不是 JDK 25，请先调整本机 `JAVA_HOME`。

## 构建项目

下载源码后，在项目根目录执行：

```bash
mvn clean package
```

构建成功后会生成：

```text
target/video-oven-0.1.0.jar
```

也可以先跑测试：

```bash
mvn test
```

## 快速试跑

不调用真实翻译接口，只验证流程：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=jKi2SvWOCXc" \
  --output output.zh-en.srt \
  --translator fake \
  --mode bilingual \
  --source en \
  --target zh-CN
```

这会生成一个双语字幕文件：

```text
output.zh-en.srt
```

`fake` 翻译器不会真的翻译，只会给英文前面加上 `[zh-CN]`，用于检查 `yt-dlp`、字幕解析、输出流程是否正常。

## 使用 DeepSeek 翻译

推荐直接用参数传入 DeepSeek API Key：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=jKi2SvWOCXc" \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --deepseek-model deepseek-v4-flash \
  --mode bilingual \
  --source en \
  --target zh-CN
```

也可以用环境变量：

```bash
export DEEPSEEK_API_KEY="你的 DeepSeek API Key"

java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=jKi2SvWOCXc" \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-model deepseek-v4-flash \
  --mode bilingual \
  --source en \
  --target zh-CN
```

如果你使用 DeepSeek 兼容网关，可以指定接口地址：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=jKi2SvWOCXc" \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --deepseek-base-url "https://api.deepseek.com" \
  --deepseek-model deepseek-v4-flash \
  --mode bilingual \
  --source en \
  --target zh-CN
```

## 使用本地字幕文件

如果你已经有字幕文件，例如：

```text
input.vtt
```

可以这样翻译：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input input.vtt \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode bilingual \
  --source en \
  --target zh-CN
```

`.srt` 输入也支持：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input input.srt \
  --output output.zh.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode chinese \
  --source en \
  --target zh-CN
```

## 输出模式

`--mode bilingual` 输出双语字幕：

```text
中文翻译
English original
```

`--mode chinese` 只输出中文字幕：

```text
中文翻译
```

## 下载原视频

本工具的 `--url` 只下载字幕，不下载视频。如果你还需要原视频，可以用 `yt-dlp`：

```bash
yt-dlp -f "bv*+ba/b" \
  --merge-output-format mp4 \
  -o "input.%(ext)s" \
  "https://www.youtube.com/watch?v=jKi2SvWOCXc"
```

下载完成后通常会得到：

```text
input.mp4
```

## 上传到 B 站

推荐方式是上传原视频，再在 B 站投稿页面单独上传 `.srt` 字幕：

1. 上传 `input.mp4`。
2. 在投稿编辑页找到字幕入口。
3. 上传 `output.zh-en.srt`。
4. 选择中文或中英双语字幕类型。
5. 预览时间轴和字幕内容。
6. 确认无误后发布。

这种方式不会重新压缩视频画面，字幕也方便后续修改。

## 合并软字幕轨

如果你想把字幕作为一个可开关字幕轨写进 mp4，可以用：

```bash
ffmpeg -y \
  -i input.mp4 \
  -i output.zh-en.srt \
  -c copy \
  -c:s mov_text \
  output.with-subtitle-track.mp4
```

生成：

```text
output.with-subtitle-track.mp4
```

注意：软字幕轨不是硬字幕，播放器或平台可能不显示。B 站更推荐单独上传 `.srt`。

## 压制硬字幕

硬字幕会把字幕永久渲染进视频画面。这个能力依赖 ffmpeg 的 `subtitles/libass` 滤镜。

先检查你当前的 ffmpeg 是否支持：

```bash
ffmpeg -hide_banner -filters | grep -E "subtitles| ass "
```

如果能看到类似输出，说明可以压制硬字幕：

```text
ass               V->V       Render ASS subtitles onto input video using the libass library.
subtitles         V->V       Render text subtitles onto input video using the libass library.
```

然后执行：

```bash
ffmpeg -y \
  -i input.mp4 \
  -vf "subtitles=filename='output.zh-en.srt'" \
  -c:v libx264 \
  -c:a copy \
  output.with-hard-subtitles.mp4
```

如果你的 ffmpeg 报错：

```text
No such filter: 'subtitles'
```

说明当前 ffmpeg 没有编入 `subtitles/libass` 滤镜，不能直接压制硬字幕。此时可以：

- 改用 B 站单独上传 `.srt`。
- 使用支持 `subtitles` 滤镜的 ffmpeg 版本。
- 先用软字幕轨方案临时处理。

### 使用独立 ffmpeg 压制硬字幕

如果 Homebrew 的 ffmpeg 没有 `subtitles` 滤镜，可以下载一个独立的 macOS ffmpeg 放到项目里。这样不影响系统 ffmpeg，也不需要 conda。

在项目根目录执行：

```bash
mkdir -p tools/ffmpeg
cd tools/ffmpeg

curl -L -o ffmpeg.zip \
  "https://ffmpeg.martin-riedl.de/redirect/latest/macos/arm64/release/ffmpeg.zip"

unzip -o ffmpeg.zip
chmod +x ffmpeg

# macOS 如果拦截执行，去掉隔离属性
xattr -dr com.apple.quarantine ffmpeg

cd ../..
```

检查独立 ffmpeg 是否支持硬字幕：

```bash
./tools/ffmpeg/ffmpeg -hide_banner -filters | grep -E "subtitles| ass "
```

看到下面两行之一就可以：

```text
ass               V->V       Render ASS subtitles onto input video using the libass library.
subtitles         V->V       Render text subtitles onto input video using the libass library.
```

用独立 ffmpeg 压制硬字幕：

```bash
./tools/ffmpeg/ffmpeg -y \
  -i input.mp4 \
  -vf "subtitles=filename='output.zh-en.srt'" \
  -c:v libx264 \
  -c:a copy \
  output.with-hard-subtitles.mp4
```

如果你当前目录已经在 `tools/ffmpeg` 里面，可以这样执行：

```bash
./ffmpeg -y \
  -i ../../input.mp4 \
  -vf "subtitles=filename='../../output.zh-en.srt'" \
  -c:v libx264 \
  -c:a copy \
  ../../output.with-hard-subtitles.mp4
```

生成的文件：

```text
output.with-hard-subtitles.mp4
```

这个文件就是字幕已经烧进画面里的视频。

## 常见问题

### Missing required option: '--input=<input>'

你使用的是旧版本 jar。重新构建：

```bash
mvn clean package
```

新版本支持 `--input` 或 `--url` 二选一。

### 找不到 yt-dlp

安装：

```bash
brew install yt-dlp
```

确认：

```bash
yt-dlp --version
```

### YouTube 视频没有英文字幕

先检查字幕：

```bash
yt-dlp --list-subs "https://www.youtube.com/watch?v=视频ID"
```

如果没有 `en` 字幕，当前版本不能自动语音识别。后续可以扩展 Whisper 或其他 ASR。

### DeepSeek key 不想放环境变量

直接用参数：

```bash
--deepseek-api-key "你的 DeepSeek API Key"
```

### 字幕时间轴不准

本工具保留原字幕时间轴。如果源字幕本身不准，需要先修正原始 `.srt` / `.vtt`。

### 中文乱码

确保字幕文件是 UTF-8。当前工具写出的 `.srt` 使用 Java 默认 UTF-8 写入。

## 命令参数

核心参数：

- `--input`：本地 `.srt` 或 `.vtt` 文件。
- `--url`：在线视频 URL，目前主要用于 YouTube 字幕下载。
- `--output`：输出 `.srt` 文件路径。
- `--translator`：翻译器，支持 `fake`、`deepseek`、`openai`。
- `--mode`：输出模式，支持 `bilingual`、`chinese`。
- `--source`：源语言，默认 `en`。
- `--target`：目标语言，默认 `zh-CN`。

DeepSeek 参数：

- `--deepseek-api-key`：DeepSeek API Key。
- `--deepseek-model`：DeepSeek 模型，默认 `deepseek-v4-flash`。
- `--deepseek-base-url`：DeepSeek 兼容接口地址，默认 `https://api.deepseek.com`。

OpenAI 参数：

- `--openai-model`：OpenAI 模型，默认 `gpt-4.1-mini`。

## 后续可扩展方向

- 自动下载视频并生成完整烤肉视频。
- 集成支持硬字幕的 ffmpeg 检测和压制流程。
- 集成 Whisper / faster-whisper，在没有字幕时从音频识别英文。
- 增加批量处理。
- 增加 Web 页面或桌面 UI。

## 版权提醒

从 YouTube 下载、翻译、搬运视频到其他平台前，请确认你有相应授权，并遵守 YouTube、B 站以及原作者的版权要求。
