#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/bake-hard-subtitles-i18n.sh <deepseek-api-key> <youtube-url> <source-language> <target-language> [subtitle.srt] [video.mp4] [baked-video.mp4]

Examples:
  scripts/bake-hard-subtitles-i18n.sh "$DEEPSEEK_API_KEY" "https://www.youtube.com/watch?v=VIDEO_ID" ko zh-CN
  scripts/bake-hard-subtitles-i18n.sh "$DEEPSEEK_API_KEY" "https://www.youtube.com/watch?v=VIDEO_ID" en zh-CN output.zh-en.srt source.mp4 baked.mp4

Language values are passed directly to Video Oven --source and --target, for example: ko, en, ja, zh-CN.
Requires a prebuilt jar at target/video-oven-0.1.0.jar.

If YouTube asks you to sign in, pass browser cookies:
  YT_DLP_COOKIES_FROM_BROWSER=chrome scripts/bake-hard-subtitles-i18n.sh "$DEEPSEEK_API_KEY" "https://www.youtube.com/watch?v=VIDEO_ID" ko zh-CN
EOF
}

die() {
  echo "Error: $*" >&2
  echo >&2
  usage >&2
  exit 1
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    die "missing required command: $1"
  fi
}

ensure_parent_dir() {
  local path=$1
  local parent
  parent=$(dirname "$path")
  if [[ "$parent" != "." ]]; then
    mkdir -p "$parent"
  fi
}

filter_escape_path() {
  local value=$1
  value=${value//\\/\\\\}
  value=${value//\'/\\\'}
  value=${value//:/\\:}
  value=${value//,/\\,}
  value=${value//[/\\[}
  value=${value//]/\\]}
  printf "%s" "$value"
}

download_video() {
  local format=${1:-}

  if [[ -n "${YT_DLP_COOKIES_FROM_BROWSER:-}" ]]; then
    set -- --cookies-from-browser "$YT_DLP_COOKIES_FROM_BROWSER"
  elif [[ -n "${YT_DLP_COOKIES:-}" ]]; then
    set -- --cookies "$YT_DLP_COOKIES"
  else
    set --
  fi

  if [[ -n "$format" ]]; then
    yt-dlp \
      "$@" \
      --ffmpeg-location "$ffmpeg_cmd" \
      -f "$format" \
      --merge-output-format mp4 \
      -o "$video_output" \
      "$youtube_url"
  else
    yt-dlp \
      "$@" \
      --ffmpeg-location "$ffmpeg_cmd" \
      --merge-output-format mp4 \
      -o "$video_output" \
      "$youtube_url"
  fi
}

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$script_dir/.." && pwd)

if [[ ${1:-} == "-h" || ${1:-} == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -gt 7 ]]; then
  die "too many arguments"
fi

deepseek_api_key=${1:-}
youtube_url=${2:-}
source_language=${3:-}
target_language=${4:-}

if [[ -z "$deepseek_api_key" ]]; then
  die "DeepSeek API key is required"
fi

if [[ -z "$youtube_url" ]]; then
  die "YouTube URL is required"
fi

if [[ -z "$source_language" ]]; then
  die "Source language is required"
fi

if [[ -z "$target_language" ]]; then
  die "Target language is required"
fi

subtitle_output=${5:-output.${target_language}-${source_language}.srt}
video_output=${6:-source.mp4}
baked_output=${7:-baked.with-hard-subtitles.mp4}

if [[ -n "${YT_DLP_COOKIES_FROM_BROWSER:-}" && -n "${YT_DLP_COOKIES:-}" ]]; then
  die "use only one of YT_DLP_COOKIES_FROM_BROWSER or YT_DLP_COOKIES"
fi

jar="$project_dir/target/video-oven-0.1.0.jar"
if [[ ! -f "$jar" ]]; then
  die "Prebuilt jar is required: $jar. Build it first with: mvn clean package"
fi

if [[ -x "$project_dir/tool/ffmpeg" ]]; then
  ffmpeg_cmd="$project_dir/tool/ffmpeg"
else
  require_command ffmpeg
  ffmpeg_cmd=ffmpeg
fi

require_command java
require_command yt-dlp

ensure_parent_dir "$subtitle_output"
ensure_parent_dir "$video_output"
ensure_parent_dir "$baked_output"

echo "1/3 Downloading video -> $video_output"
if ! download_video "bv*+ba/bestvideo*+bestaudio/best"; then
  echo "Primary format was unavailable, retrying with best[ext=mp4]/best..." >&2
  if ! download_video "best[ext=mp4]/best"; then
    echo "Fallback format was unavailable, retrying with yt-dlp default format..." >&2
    if download_video; then
      echo "Downloaded with yt-dlp default format." >&2
    else
      if [[ -z "${YT_DLP_COOKIES_FROM_BROWSER:-}" && -z "${YT_DLP_COOKIES:-}" ]]; then
        echo >&2
        echo "YouTube blocked direct download. Log in to YouTube in your browser, then rerun with cookies:" >&2
        echo "  YT_DLP_COOKIES_FROM_BROWSER=chrome $0 \"<deepseek-api-key>\" \"$youtube_url\" \"$source_language\" \"$target_language\" \"$subtitle_output\" \"$video_output\" \"$baked_output\"" >&2
        echo >&2
        echo "Use safari/firefox/edge instead of chrome if that is where you are logged in." >&2
      fi
      exit 1
    fi
  fi
fi

echo "2/3 Translating subtitles ($source_language -> $target_language) from local video -> $subtitle_output"
java -jar "$jar" \
  --input "$video_output" \
  --output "$subtitle_output" \
  --translator deepseek \
  --deepseek-api-key "$deepseek_api_key" \
  --mode bilingual \
  --source "$source_language" \
  --target "$target_language"

echo "3/3 Burning hard subtitles -> $baked_output"
subtitle_filter_path=$(filter_escape_path "$subtitle_output")
"$ffmpeg_cmd" \
  -y \
  -hide_banner \
  -i "$video_output" \
  -vf "subtitles='$subtitle_filter_path'" \
  -c:v libx264 \
  -crf 18 \
  -preset medium \
  -c:a copy \
  "$baked_output"

echo "Done: $baked_output"
