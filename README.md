# Video Oven

Video Oven 是一个命令行字幕翻译工具。它可以把英文字幕或英文音视频翻译成中文字幕，输出标准 `.srt` 文件。

支持三种输入：

- 本地 `.srt` / `.vtt` 字幕文件。
- 本地音频或视频文件，例如 `.mp3`、`.mp4`、`.mkv`。
- YouTube URL：优先下载已有字幕；没有字幕时自动下载音频并用 Whisper 识别。

## 安装依赖

推荐直接运行安装脚本：

```bash
scripts/install-deps.sh
```

它会安装：

- OpenJDK
- Maven
- `yt-dlp`
- `pipx`
- Whisper CLI

`ffmpeg` 不需要安装，项目会使用 `tool/ffmpeg`。

也可以手动安装基础依赖：

```bash
brew install openjdk maven yt-dlp
```

如果要处理没有字幕的音视频，还需要 Whisper CLI。推荐先安装 `pipx`：

```bash
brew install pipx
pipx ensurepath
```

执行 `pipx ensurepath` 后，重开一个终端，再安装 Whisper：

```bash
pipx install openai-whisper
```

如果不想使用 `pipx`，也可以用 `pip` 安装：

```bash
python3 -m pip install --user -U openai-whisper
```

确认命令可用：

```bash
java -version
mvn -version
yt-dlp --version
./tool/ffmpeg -version
whisper --help
```

## 构建

在项目根目录执行：

```bash
mvn clean package
```

生成的可执行 jar：

```text
target/video-oven-0.1.0.jar
```

## 一键生成硬字幕视频

脚本会自动完成三步：

1. 下载 YouTube 原视频。
2. 从本地视频识别并翻译字幕。
3. 用 ffmpeg 烧录硬字幕。

只传 DeepSeek API Key 和 YouTube 地址即可：

```bash
scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID"
```

默认输出：

- 字幕：`output.zh-en.srt`
- 原视频：`source.mp4`
- 硬字幕视频：`baked.with-hard-subtitles.mp4`

也可以指定输出文件名：

```bash
scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID" \
  output.zh-en.srt \
  source.mp4 \
  baked.mp4
```

脚本会先按直接下载视频的方式调用 `yt-dlp`，再对下载好的本地视频做字幕识别和翻译。

如果 YouTube 报 `Sign in to confirm you're not a bot`，需要让 `yt-dlp` 使用浏览器 cookies。先确认你已经在对应浏览器里登录 YouTube，然后这样运行：

```bash
YT_DLP_COOKIES_FROM_BROWSER=chrome scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID"
```

常见值可以用 `chrome`、`safari`、`firefox`、`edge`。如果你导出了 `cookies.txt`，也可以这样：

```bash
YT_DLP_COOKIES=/path/to/cookies.txt scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID"
```

## 手动三步生成硬字幕视频

如果不想用一键脚本，可以手动执行三步。每一步的输入和输出如下：

```text
YouTube 地址 -> source.mp4 -> output.zh-en.srt -> baked.mp4
```

### 第一步：下载原视频

这一步只负责下载 YouTube 原视频，输出 `source.mp4`：

```bash
yt-dlp -f "bv*+ba/b" \
  --merge-output-format mp4 \
  -o "source.mp4" \
  "https://www.youtube.com/watch?v=视频ID"
```

如果 YouTube 要求登录，可以加浏览器 cookies：

```bash
yt-dlp --cookies-from-browser chrome \
  -f "bv*+ba/b" \
  --merge-output-format mp4 \
  -o "source.mp4" \
  "https://www.youtube.com/watch?v=视频ID"
```

### 第二步：生成并翻译字幕

这一步读取本地视频 `source.mp4`，先用 Whisper 识别英文字幕，再用 DeepSeek 翻译，输出 `output.zh-en.srt`：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input source.mp4 \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode bilingual \
  --source en \
  --target zh-CN
```

如果你已经有字幕文件，可以把 `--input source.mp4` 换成字幕文件：

```bash
--input input.srt
```

### 第三步：烧录硬字幕

这一步读取原视频 `source.mp4` 和字幕 `output.zh-en.srt`，输出硬字幕视频 `baked.mp4`：

```bash
./tool/ffmpeg -y \
  -hide_banner \
  -i source.mp4 \
  -vf "subtitles='output.zh-en.srt'" \
  -c:v libx264 \
  -crf 18 \
  -preset medium \
  -c:a copy \
  baked.mp4
```

### 另一种第二步：直接使用 YouTube 已有字幕

如果视频本身有英文字幕，且 `yt-dlp` 下载字幕没有触发登录校验，可以不从本地视频识别，直接让工具读取 YouTube 字幕：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=视频ID" \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode bilingual \
  --source en \
  --target zh-CN
```

如果 YouTube 字幕下载也要求登录，可以加：

```bash
--yt-dlp-cookies-from-browser chrome
```

## 快速试跑

用 `fake` 翻译器验证流程，不会调用真实翻译接口：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=视频ID" \
  --output output.zh-en.srt \
  --translator fake \
  --mode bilingual
```

`fake` 会把原文前面加上 `[zh-CN]`，适合检查下载、识别、解析和输出流程是否正常。

## 翻译文章链接为 Markdown

文章模式和视频模式分开：文章使用 `--article-url`，视频继续使用 `--url`。程序会抓取网页 HTML，提取正文内容，转换为 Markdown，再用 DeepSeek 翻译成目标语言。

```bash
java -jar target/video-oven-0.1.0.jar \
  --article-url "https://example.com/technical-post" \
  --output article.zh-CN.md \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --source en \
  --target zh-CN
```

快速试跑可以使用 `fake` 翻译器，不会调用真实接口：

```bash
java -jar target/video-oven-0.1.0.jar \
  --article-url "https://example.com/technical-post" \
  --output article.zh-CN.md \
  --translator fake
```

文章模式会优先提取 `<article>`、`<main>` 或 `role=main` 内容，并尽量保留标题、段落、列表、链接和代码块。代码块不会进入翻译批次，适合技术文章搬运。

## 翻译 YouTube 视频

默认会先下载英文字幕。如果视频没有英文字幕，会自动回退到 Whisper 语音识别：

```bash
java -jar target/video-oven-0.1.0.jar \
  --url "https://www.youtube.com/watch?v=视频ID" \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode bilingual \
  --source en \
  --target zh-CN
```

只使用已有字幕，不做语音识别：

```bash
--asr never
```

强制忽略平台字幕，重新语音识别：

```bash
--asr force
```

## 翻译本地字幕

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

`.srt` 输入也支持。

## 翻译本地音视频

没有字幕时，直接传音频或视频文件。程序会先调用 Whisper 生成临时英文字幕，再翻译：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input input.mp4 \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --mode bilingual \
  --source en \
  --target zh-CN
```

调整 Whisper 命令、模型和超时时间：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input input.mp3 \
  --output output.zh-en.srt \
  --translator deepseek \
  --deepseek-api-key "你的 DeepSeek API Key" \
  --whisper-command whisper \
  --whisper-model medium \
  --asr-timeout-minutes 180
```

## 使用 OpenAI 翻译

先设置环境变量：

```bash
export OPENAI_API_KEY="你的 OpenAI API Key"
```

运行：

```bash
java -jar target/video-oven-0.1.0.jar \
  --input input.srt \
  --output output.zh-en.srt \
  --translator openai \
  --openai-model gpt-4.1-mini \
  --mode bilingual
```

## 输出模式

双语字幕：

```bash
--mode bilingual
```

输出格式：

```text
中文翻译
English original
```

只输出中文字幕：

```bash
--mode chinese
```

## 常用参数

- `--input`：本地字幕、音频或视频文件。
- `--url`：在线视频 URL，目前主要用于 YouTube。
- `--article-url`：技术文章 URL，输出 Markdown 文档。
- `--output`：输出 `.srt` 字幕或 `.md` 文章文件路径。
- `--translator`：翻译器，支持 `fake`、`deepseek`、`openai`。
- `--mode`：输出模式，支持 `bilingual`、`chinese`。
- `--source`：源语言，默认 `en`。
- `--target`：目标语言，默认 `zh-CN`。
- `--translation-batch-size`：每次翻译的字幕条数，默认 `50`。
- `--asr`：语音识别模式，支持 `auto`、`never`、`force`，默认 `auto`。
- `--whisper-command`：Whisper CLI 命令，默认 `whisper`。
- `--whisper-model`：Whisper 模型，默认 `small`。
- `--asr-timeout-minutes`：语音识别超时时间，默认 `120`。
- `--yt-dlp-cookies-from-browser`：让 `yt-dlp` 读取浏览器 cookies，例如 `chrome` 或 `safari`。
- `--yt-dlp-cookies`：让 `yt-dlp` 使用 `cookies.txt` 文件。

## 常见问题

### 找不到 yt-dlp、ffmpeg 或 whisper

确认命令能在当前 shell 里直接执行：

```bash
yt-dlp --version
./tool/ffmpeg -version
whisper --help
```

### YouTube 要求登录或提示不是机器人

如果看到类似错误：

```text
Sign in to confirm you're not a bot
```

说明 YouTube 拦截了 `yt-dlp` 的匿名下载请求。先用浏览器登录 YouTube，再给脚本加浏览器 cookies：

```bash
YT_DLP_COOKIES_FROM_BROWSER=chrome scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID"
```

这里的 `chrome` 要换成你实际登录 YouTube 的浏览器。如果你是在 Safari 登录，就用：

```bash
YT_DLP_COOKIES_FROM_BROWSER=safari scripts/bake-hard-subtitles.sh \
  "你的 DeepSeek API Key" \
  "https://www.youtube.com/watch?v=视频ID"
```

不要用 `sh scripts/bake-hard-subtitles.sh` 运行脚本。这个脚本使用 Bash，请直接运行：

```bash
scripts/bake-hard-subtitles.sh ...
```

或者：

```bash
bash scripts/bake-hard-subtitles.sh ...
```

### YouTube 提示 Requested format is not available

如果看到类似错误：

```text
Requested format is not available. Use --list-formats for a list of available formats
```

说明 `yt-dlp` 当前没有找到匹配的视频格式。可能原因包括：

- 这个视频当前地区或账号下可用格式不同。
- YouTube 拦截后，`yt-dlp` 拿到的格式列表不完整。
- `yt-dlp` 版本太旧，解析 YouTube 格式失败。

一键脚本已经内置三层下载兜底：

1. 先尝试高清音视频合并格式。
2. 失败后尝试 `best[ext=mp4]/best`。
3. 再失败后，不指定 `-f`，让 `yt-dlp` 使用默认格式。

如果三层兜底仍然失败，先更新 `yt-dlp`：

```bash
brew upgrade yt-dlp
```

然后查看当前视频到底有哪些格式：

```bash
yt-dlp --list-formats "https://www.youtube.com/watch?v=视频ID"
```

如果需要带登录态查看格式：

```bash
yt-dlp --cookies-from-browser chrome \
  --list-formats \
  "https://www.youtube.com/watch?v=视频ID"
```

### DeepSeek 翻译条数不一致

可以调小批量：

```bash
--translation-batch-size 25
```

如果还不稳定，继续调到 `10`。批量越小越稳，但请求次数会变多。

### 字幕时间轴不准

已有字幕会保留原时间轴。音视频 ASR 的时间轴由 Whisper 生成，准确度取决于音频质量和模型大小。
